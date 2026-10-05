# Architecture

Why the system looks the way it does, including the parts that were built, measured and
then taken out again. Written for someone deciding whether the code is worth reading.

---

## Service boundaries

**The split is heavier than the traffic warrants.** notification-service scales with the
number of open WebSocket connections — memory and file descriptors — while chat-service
scales with database load. Those are genuinely different profiles, which is the honest
case for separating them. At the volume this project will ever see, a modular monolith
would be the better engineering call, and nothing here pretends otherwise.

**Transport is split by what the data is.** Commands (send a message, mark as read) go
over REST into chat-service. Delivery and notifications travel chat → outbox → Kafka →
notification → WebSocket. Anything ephemeral that nobody needs to recover, such as typing
indicators, would go straight into notification over the socket. Only
notification-service speaks WebSocket; chat-service has no socket at all.

**User data is replicated, not queried.** auth-service publishes `UserRegistered`;
chat-service keeps its own copy. User search lives in chat-service and runs against that
replica, which removes a synchronous dependency on auth from the hot path. The event
contract has exactly one event, so the consumer is an `INSERT ... ON CONFLICT DO NOTHING`
— there is no deactivation to handle because there is no deactivation event.

**The gateway is deliberately dumb.** It routes by path prefix, proxies WebSocket upgrades
and handles CORS. It does not validate JWTs: every service is its own resource server and
checks the token itself. That keeps the gateway stateless, and more importantly it means a
service reached directly — bypassing the gateway entirely — is still protected.

**It is reactive for one reason.** Spring Cloud Gateway's servlet variant still cannot
proxy WebSocket connections
([spring-cloud-gateway#3442](https://github.com/spring-cloud/spring-cloud-gateway/issues/3442)),
and `/ws/**` has to pass through. Everything else runs on MVC with virtual threads.

**No service discovery.** Docker Compose already resolves service names, and round-robin
DNS is enough because of a property the design already has: every notification instance
consumes every event and can serve any user, so routing does not need to be smart. Had
sessions been tracked in a shared registry with targeted delivery, the gateway would need
to know which instance holds which socket, and discovery with metadata would start earning
its keep.

---

## Delivery and consistency

**Transactional outbox in both writing services.** The domain row and the event row are
written in one transaction; a poller publishes to Kafka afterwards. This is the standard
answer to "the database committed but the broker call failed", and it is why delivery is
at-least-once rather than maybe-once.

**The poller is woken after commit, not only on a schedule.** With a five-second interval,
a message reached its recipient seconds after it was sent, which is a broken experience
for a chat. `OutboxRecorder` registers a `TransactionSynchronization`, and publishing
starts immediately after the commit: measured 68–156 ms end to end, against up to five
seconds before. The scheduled pass stays, but its role changes from primary mechanism to
safety net for anything the wake-up missed, so its interval can be raised rather than
lowered.

The executor behind it is one thread with a queue of one and `DiscardPolicy`. A burst of
events inside a single transaction therefore collapses into one publish without any
bookkeeping: a run already queued will pick up whatever committed since.

**What the wake-up costs.** Two concurrent passes can reorder adjacent events of the same
conversation. The window is tiny, `FOR UPDATE SKIP LOCKED` separates the rows, and
different conversations are independent anyway. Removing it entirely would need one poller
per conversation, which is not proportionate here.

The same wake-up was carried back into auth-service, where the race is concrete rather
than theoretical: a freshly registered user whose row has not yet reached chat's replica
gets a 404 saying their own id was not found when they create their first conversation.

**Ordering is per conversation.** Events are keyed by `conversationId`, the poller reads
`ORDER BY id`, and after a failed send the remaining events of that same conversation are
skipped for the rest of the pass — otherwise the retry would deliver them out of order.
Other conversations keep flowing.

**WebSocket delivery is best-effort by design.** The database is the source of truth. A
client that missed frames catches up with `GET /api/conversations/{id}/messages`, the same
endpoint that serves history. This is also what makes closing sockets on token expiry
harmless: the reconnect path already exists.

**Deduplication is the client's job, and it is unavoidable.** Every event goes to every
participant including whoever caused it, because a person may have two tabs open and only
one of them knows about the action. The client therefore applies events as idempotent
state assignments keyed by domain identity, not as log appends. Six of the seven event
types are naturally idempotent that way — assigning a title twice changes nothing,
removing an absent participant changes nothing, a high-water mark only moves forward.
`MessageCreated` is the only type that genuinely appends, and it is the one that carries a
`clientMessageId`.

**Idempotent sends use the unique index, not a cache and not an exception.**
`ON CONFLICT (conversation_id, client_message_id) DO NOTHING ... RETURNING id` returns
empty on a retry, which is an ordinary branch rather than an error. Catching
`DataIntegrityViolationException` instead would not work: the transaction is already
marked rollback-only by then, and the commit throws `UnexpectedRollbackException`.

---

## Event contracts

**One envelope, seven payloads.** Confluent's default subject strategy allows one schema
per topic. The alternative — `TopicRecordNameStrategy` — would mean duplicating the shared
fields across seven schemas and checking compatibility separately for each. `ChatEvent`
carries `eventId`, `conversationId`, `occurredAt`, `recipients` and a union payload.

**Events carry their recipients**, captured in the same transaction as the write. This is
the single decision that keeps notification-service stateless: it holds no participant
replica, no table and no consumer of its own for membership changes. It removes a layer of
state and a class of races where membership is updated but the replica has not caught up.

One race remains and is not solvable this way: a participant who leaves while another is
mid-send will still receive that message, because the recipient list was captured before
the leave committed. Delivery is best-effort, the database is authoritative, and
`GET /messages` already returns 404 for them.

**Compatibility is enforced at `BACKWARD`** on the registry. For a union specifically, a
new branch appended at the end is compatible; one inserted in the middle is not, because
the branch index is part of the binary encoding. That is exactly the mistake the rule
catches, and it is why `ConversationCreated` was added last rather than next to its
relatives.

**Reader schemas are deliberately narrower than writer schemas.** chat-service reads
`UserRegistered` without `occurredAt`, which exercises real Avro schema resolution rather
than a class serialising itself. Each service owns its own copy of the schema it reads —
that is what allows them to diverge at all.

**The payload is mapped explicitly on the way out to clients.** `SpecificRecordBase`
exposes `getSchema()`, so serialising an Avro object straight to JSON drags the schema
into the frame. The mapping is a pattern-matching switch over the generated classes, so
the compiler checks the dispatch and no string keys exist to drift; the wire `type` comes
from `getClass().getSimpleName()`. The frame is serialised once per event rather than once
per session — a group of a hundred would otherwise pay a hundred identical serialisations.

---

## Consumer resilience

Three separate bugs here produced the same symptom: a Kafka partition blocked forever.
Each was found by a test rather than by reading the code, and two of them were introduced
by the mechanism meant to prevent the first.

**A malformed record used to block the partition permanently.** The error handler was
configured with `addNotRetryableExceptions(DeserializationException.class)`, which looks
like protection and was dead code. Without `ErrorHandlingDeserializer`, deserialization
fails inside `poll()` before Spring ever sees a record, so that exception is never raised:
the consumer re-reads the same offset forever. In production this meant one bad record in
`user-events` would stop user replication for good, with a green health check and
gigabytes of stack traces. Found by an integration test that writes raw bytes to the
topic; a mocked consumer cannot see this at all.

**Then the dead-letter producer pointed at the wrong broker.** `@ServiceConnection` and
other connection-details sources override the factories Spring Boot builds — they do not
change `spring.kafka.bootstrap-servers`. A hand-built producer reading that property goes
to the address in the YAML. Publication to the DLT failed, the recoverer could not recover
the record, and the partition blocked again — the fix reintroducing the bug it fixed.

**Then one serializer could not handle the dead letters.** A dead letter is not uniformly
typed: a key that deserialized fine arrives as `String`, a payload that failed arrives as
raw bytes, and a record that failed in the listener after its retries arrives as a built
Avro object. One serializer for all three throws `ClassCastException` inside the recoverer
and blocks the partition a third time. `DelegatingByTypeSerializer` handles all three; the
Avro case had not occurred yet and was closed pre-emptively, because it would only appear
during a database outage — the worst possible moment to discover it.

**chat-service parks bad records in a DLT; notification-service does not.** The difference
is what could be done with the record afterwards. A malformed `UserRegistered` means a
user permanently missing from chat's replica — a state divergence only a replay can fix,
so the record must be kept. A malformed `ChatEvent` in notification means one undelivered
frame, and the client recovers it over REST within seconds; by the time a human looks at
the dead-letter topic the record is historical litter. A log line and an alertable counter
carry the whole signal with no extra topic and no extra producer. `ErrorHandlingDeserializer`
is mandatory in both regardless — it is what unblocks the partition, independent of where
the record ends up.

**Retries differ for the same reason.** chat retries; notification uses
`FixedBackOff(0, 0)` and skips immediately. Retrying there would delay every later event
on the partition for the sake of a frame that is not authoritative anyway.

---

## State and scaling

**notification-service holds no state at all.** Every candidate for storage was checked
and none survived. Deduplication is not needed because the client must deduplicate for
other reasons already. A user replica is not needed because `ParticipantAdded` and
`ConversationCreated` carry usernames. Queued notifications for offline users are not
needed because delivery is best-effort and clients catch up over REST. The session
registry is ephemeral by nature. JWT validation goes through JWKS. The service can be
killed and restarted with no consequences, and scaled by adding instances with no
coordination.

**Each instance gets its own consumer group** (`notification-${random.uuid}`), so every
instance receives every event and delivers to whichever sessions it holds locally. The
obvious alternative — a session registry in Redis — only solves half the problem: knowing
that a user's socket is on instance B does not let instance A write to it, so Redis Pub/Sub
would be needed as a transport as well. The cost of the chosen design is that every
instance deserializes every event, which is cheap, and that abandoned consumer groups
accumulate in the broker until `offsets.retention.minutes` expires them.

`auto-offset-reset` is `latest`, not `earliest`: a restarted instance must not replay
history into live sockets. Clients disconnected during the restart recover over REST.

**Virtual threads are enabled where there is something to unblock** — auth and chat, whose
REST endpoints block on JDBC. They are deliberately not enabled in notification, which has
no HTTP traffic beyond actuator; WebSocket connections are served by Tomcat's NIO pollers,
and the Kafka listener, the outbox wake-up and the scheduler all run on their own bounded
pools. Turning it on there for symmetry would present copying as a decision.

The honest limit: virtual threads remove the cap on concurrent requests but not on
database connections, and every REST request goes to PostgreSQL. The queue moves from
"waiting for a thread" to "waiting for a connection"; throughput is unchanged. The one
real behavioural difference is that pool exhaustion now surfaces as
`SQLTransientConnectionException` after `connectionTimeout` instead of silently queueing
in the acceptor — a failure closer to its cause. Carrier pinning on `synchronized`, which
made virtual threads risky on Java 21, was fixed in JDK 24 (JEP 491); this project
compiles for 21 and runs on 25.

---

## Data model

**Foreign keys are values, not associations.** There is no `@ManyToOne` or `@OneToMany`
anywhere: `conversationId` and `senderId` are `Long`. This removes N+1, cascades and
`LazyInitializationException` by removing their cause. The price is that every join is
written out by hand with no compiler support if one is forgotten. A side effect worth
naming: `JOIN FETCH` does not appear in this codebase at all, because there is nothing to
fetch.

**All conversations are groups.** A two-person chat is a special case with no special
logic, which also means two people can have several conversations with different purposes.
This removed an entire branch of complexity: find-or-create with an advisory lock keyed on
the ordered pair of user ids, which the earlier one-to-one model needed and which is now
only a note in the rejected section.

**One role: `admin_id`.** Neutral actions — invite, rename — are open to any participant;
destructive ones — remove a participant, transfer ownership, delete the conversation — are
the admin's. The admin cannot leave without transferring first, which holds the invariant
that a conversation is never empty and `admin_id` always points at an existing
participant. A composite foreign key `(id, admin_id) → participants` would express it in
the schema, but it creates a cycle between the two tables, requires `DEFERRABLE`, and
breaks Hibernate's insert ordering; the invariant is held in application code instead.

**A new participant sees the whole history.** The alternative needs a
`joined_from_message_id` recomputed on every re-join, and it leaks into both keyset
pagination and the unread counter. For a real product — work chats especially — the
decision goes the other way.

**Leaving is a hard delete of the participant row.** A soft delete with `left_at` would
drag `AND left_at IS NULL` into every membership query. Historical membership is not
needed: `messages.sender_id` references `users`, so messages from someone who left stay
put. The cost is that re-joining resets their read marker.

**Read state is a high-water mark** — one id per participant instead of a flag per
message. It moves forward only, enforced in the `UPDATE` predicate, and zero affected rows
is the signal not to publish an event. Sending a message implicitly advances the sender's
own marker, because otherwise "1 unread" would sit in your own chat right after you
replied. `last_delivered_message_id` was removed: there are no double ticks.

**The marker is moved by an explicit `POST /read`, never by a `GET`.** A client fetches
messages while catching up in a background tab, while scrolling history, and when
reloading a collapsed page — none of which is reading. And `GET` has to stay safe, because
browsers and proxies prefetch and repeat it. The one exception is `POST /messages`, where
the server knows rather than guesses.

**`messages.id` is an identity column; `outbox_events.id` comes from a sequence with
allocation size 50.** For messages the id order must match time order — keyset pagination
and reconnect catch-up both rely on it, and a cached sequence across several instances
would break that. The outbox needs no ordering guarantee of that kind, so it gets the
batching instead.

**Search escapes `LIKE` wildcards.** Without it, a query of `%` enumerates every user in
the system, page by page. Not a critical vulnerability — the parameter is bound, there is
no injection — but account enumeration all the same. The escape character is doubled first,
otherwise it escapes the backslash added in the next step; a backslash is used rather than
a letter because `lower()` is applied to the whole pattern and would corrupt a letter.

---

## API surface

All paths go through the gateway on port 8000. Everything except registration, login,
refresh and the key set requires `Authorization: Bearer <access token>`.

### auth-service

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/api/auth/register` | username, password | `201`, empty |
| `POST` | `/api/auth/login` | username, password | access token, refresh token |
| `POST` | `/api/auth/refresh` | refresh token | a new pair; the old refresh token is burned |
| `POST` | `/api/auth/logout` | refresh token | `204` |
| `GET` | `/api/auth/me` | — | the caller's id and username |
| `GET` | `/.well-known/jwks.json` | — | the public key, for the other services |

The access token is a 15-minute RS256 JWT with `sub` set to the user id — not the
username — and an audience listing all three services. The refresh token is opaque, 32
bytes of `SecureRandom`, stored only as a SHA-256 hash, valid for 30 days, and rotated on
every use. One session per user: a new login revokes the previous one.

`/api/auth/me` lives under the auth prefix rather than `/api/users/me` because
`/api/users/**` is routed to chat-service at the gateway.

### chat-service

| Method | Path | What it does | Returns |
|---|---|---|---|
| `POST` | `/api/conversations` | create with a title and participant ids | `201` + `Location`, the roster |
| `GET` | `/api/conversations?page=` | unread counts and a participant preview | summaries, newest activity first |
| `GET` | `/api/conversations/{id}` | full roster with everyone's read marker | details |
| `PATCH` | `/api/conversations/{id}` | rename — any participant | `204` |
| `DELETE` | `/api/conversations/{id}` | admin only, cascades | `204` |
| `POST` | `/api/conversations/{id}/participants` | invite — any participant | `204`, silently idempotent |
| `DELETE` | `/api/conversations/{id}/participants/me` | leave; the admin cannot | `204` |
| `DELETE` | `/api/conversations/{id}/participants/{userId}` | remove — admin only | `204` |
| `PUT` | `/api/conversations/{id}/admin` | transfer ownership to a participant | `204` |
| `POST` | `/api/conversations/{id}/messages` | send | `201`, or `200` on a retry with the same `clientMessageId` |
| `GET` | `/api/conversations/{id}/messages?messageId=&isBefore=&limit=` | history and catch-up | a page plus `hasMore` |
| `POST` | `/api/conversations/{id}/read` | move the read marker forward | `204` |
| `GET` | `/api/users/search?query=&page=` | search the replicated user list | users, excluding the caller |

**A non-participant gets 404, not 403**, because a 403 would confirm the conversation
exists to a stranger. A participant without the right gets 403 — there is nothing left to
hide from them.

**`201` versus `200` on send is the idempotency signal.** A first send creates the
message; a retry carrying the same `clientMessageId` returns the stored one with `200`,
because a retry is not a new resource. Clients can ignore the distinction entirely and
nothing breaks.

**Several mutations answer `204` whether or not they changed anything.** Inviting someone
already in the conversation, removing someone already gone, marking as read up to a
message already behind the marker — all of them are no-ops that publish no event. The
status says the request was honoured, not that state moved.

**History pagination takes one cursor and a direction**, `messageId` + `isBefore`, rather
than separate `before` and `after` parameters. With two cursors the invalid state — both
set — is expressible, and it then has to be either rejected or silently resolved in favour
of one, which is behaviour you can only learn from the source. With one cursor it cannot
be sent at all, so there is nothing to handle. `hasMore` is derived from fetching one row
beyond the limit; an exact remaining count would mean a `COUNT(*)` per page for a number
no interface shows.

---

## WebSocket protocol

`ws://localhost:8000/ws`, JSON frames, raw WebSocket rather than STOMP.

STOMP's main contribution here would be `convertAndSendToUser`, which maintains a session
registry — and the registry is written by hand anyway. What settled it was testability: a
raw socket can be driven with `websocat` or a line in the browser console, while STOMP
needs a page with stomp.js or a purpose-built Java client, and this project has no
frontend.

**The client authenticates with its first frame.** A browser cannot set headers on a
WebSocket upgrade, which rules out `Authorization`, and a token in a query parameter ends
up in access logs. The connection opens unauthenticated, the server sends nothing until
`AUTH` arrives, and a connection that has not authenticated within `app.ws.auth-timeout` is
closed. This is what STOMP's `CONNECT` does, written out explicitly. It also means the
WebSocket path must be `permitAll` in Spring Security — requiring authentication there
would reject the handshake with 401 before the client could send anything.

```
→ {"type":"AUTH","token":"<access token>"}
← {"type":"READY","userId":1}

→ {"type":"PING"}
← {"type":"PONG"}

← {"type":"EVENT","event":{"eventId":203,"conversationId":4,
     "occurredAt":"...","type":"MessageCreated","payload":{...}}}
```

Close codes: **4401** the token was never valid — send the user to login; **4402** the
token expired — refresh and reconnect; **1001 and anything else** — just reconnect.

**Sessions are closed when the token expires**, at the moment recorded in the token rather
than a fixed time after connecting. The gap is seconds and nothing is lost, because the
reconnecting client catches up over REST. The benefit is that revocation takes effect
within one access-token lifetime instead of lasting as long as the tab stays open.

**Sessions are also closed explicitly on shutdown**, with 1001. Graceful shutdown treats a
WebSocket as a request in flight and would otherwise wait out
`timeout-per-shutdown-phase` for something that never ends on its own.

**A WebSocket through the gateway is two TCP connections**, not a tunnel: the gateway
upgrades on the client side, opens its own connection downstream, and bridges frames. Close
codes do propagate from the service through the bridge. The gateway's own shutdown does not
send a close frame, so clients see 1005 — which is indistinguishable from a network drop,
and handled by the same reconnect path.

---

## Testing

**Integration tests run against real infrastructure** — PostgreSQL, Kafka and Apicurio
through Testcontainers, in one shared context. A separate context for the consumer was
planned and turned out to be unnecessary: the consumer test publishes with its own
`KafkaProducer` while the application consumer uses the `ConsumerFactory`, and the mocked
`KafkaTemplate` that outbox tests need is in neither path.

**No `@Transactional` on the base class.** A wrapping transaction would hide flush-ordering
bugs and break the concurrency tests, which need real commits. An explicit `cleanState()`
runs before each test instead, deleting from dependent tables first.

**Query counts are asserted through Hibernate Statistics.** "Ten conversations, exactly two
queries" catches an N+1 regression more reliably than review does: without associations the
compiler says nothing, and an extra query inside a loop looks innocent.

**Negative cases are a valid baseline plus exactly one break.** A body broken in two places
returns 400 for any reason and proves nothing. There is a test showing the baseline body is
accepted, every negative case differs from it by one field, and boundary values are checked
separately so the limit is where it is claimed to be. Bodies are built from the record
through Jackson rather than written as strings, so field names cannot drift from the DTO.

**Three Kafka-specific traps**, each of which cost real time:

- Anything built by hand — a producer factory, a test producer — must take the broker
  address from the container or from `KafkaConnectionDetails`, never from
  `spring.kafka.bootstrap-servers`. Connection details override the factories Spring Boot
  builds, not the property.
- With `auto-offset-reset: latest`, an event published before the consumer owns its
  partitions is never seen. `ContainerTestUtils.waitForAssignment` before each test removes
  the randomness.
- Topics are not cleaned between tests. Deleting a topic mid-run fights the live listener,
  triggers rebalances and metadata errors, and leaves `__consumer_offsets` referring to a
  topic that no longer exists. Each test writes a uniquely identifiable payload and filters
  on it instead of counting everything in the topic.

**JWTs in tests are signed with a key pair generated in the test**, not mocked. The decoder
is replaced by one built from that pair with the same validator chain as production, so the
tests also cover issuer and audience checks. Being able to issue a token with an arbitrary
`exp` makes the expiry-close test trivial — and `JwtTimestampValidator` allows 60 seconds of
clock skew by default, so a token expiring in two seconds still decodes, which means what
closes the socket is the scheduled task rather than the validator. That is what the test is
supposed to prove.

---

## Considered and rejected

**Advisory lock for find-or-create of a direct conversation.** Disappeared with the
one-to-one model. The key had to be built from the *ordered* pair: `(a,b)` and `(b,a)`
would otherwise take different locks and both requests would create a conversation.

**Redis for send idempotency.** The unique index on
`(conversation_id, client_message_id)` gives the same guarantee atomically, in one
round-trip, inside the same transaction. Redis in front of it is an extra hop on the normal
path, does not help with the concurrent case, and introduces a new bug: key written,
transaction rolled back. Unlike auth, where the grace cache holds information the database
does not have — when a refresh token was used.

**Redis in notification-service.** Planned for the session registry, made unnecessary by
per-instance consumer groups. It would come back with exactly one feature — presence — and
that feature does not exist.

**Presence.** It belongs to notification by definition; nothing else knows the state of the
connections. But it should be answered over the client's existing socket, not by chat
calling notification, which would reintroduce the synchronous dependency removed when user
search moved out of auth. Left out partly because it is deceptively hard: half-open TCP
connections, multiple tabs in different states, "away" versus "closed" — a naive
implementation simply lies.

**Eureka and a config server.** Compose already resolves service names, and the stateless
design means routing does not need to be smart. In any real deployment today the platform's
own discovery would be used instead. This is named as a trade-off rather than dressed up as
a technical need: a reviewer looking specifically for the canonical Spring Cloud set will
not find it here.

**A shared module for the outbox.** The entity, repository, properties, scheduler and
wake-up are duplicated between auth and chat. The publishers genuinely differ, though — a
registry of factories, an envelope and ordering in chat against a direct builder for a
single event in auth — so a shared module would need an abstraction over event construction
for two implementations. A shared library also couples releases: a change for chat would
force a rebuild of auth, which is the coupling the service split was supposed to avoid.

**`invited_by` on participants.** Never read, no moderation feature to justify it, and
`ON CONFLICT DO NOTHING` would keep the original value on re-join — a field that quietly
lies is worse than no field.

**A windowed `before` + `after` page.** A meaningful operation — closing a known gap in a
client cache — but it needs a third cursor semantics: the limit has to apply from one edge,
and `hasMore` starts meaning something else. The variant without a limit was rejected
separately, since the limit is not a convenience but the guarantee that no single request
can pull an unbounded number of rows.

**Heartbeat.** Phantom sessions are already bounded by the token lifetime, so the registry
does not grow without limit. A server-side ping becomes necessary once a gateway sits in
front — proxies cut idle connections after 30–60 seconds — and the interval should then
come from that proxy's real timeout rather than being guessed in advance. Client-side
keep-alive is already available through the `PING` frame.

**Separate databases per service.** Both schemas live in one PostgreSQL instance with
separate users and no cross-grants. The isolation that matters comes from privileges, not
from the database boundary, and in a real deployment "database per service" means a
separate instance anyway — two `CREATE DATABASE` statements in one container gesture at the
guarantee without providing it. The database is named `chat_db` for historical reasons and
holds both schemas.

---

## Known limits

- The unread counter is a correlated subquery per row of the conversation list. The index
  covers it, but a conversation with a hundred thousand messages that nobody has opened in
  a year is counted honestly, every time. The fixes are a denormalised counter, which has
  to be kept consistent, or a "99+" ceiling via `LIMIT`.
- User search uses `LIKE '%...%'`. `pg_trgm` is the right answer and would also remove the
  hand-written wildcard escaping.
- Nothing watches the dead-letter topic. At minimum it needs a metric and an alert on a
  non-zero size.
- Topics are auto-created. In production that is usually off, and publication to
  `<topic>.DLT` would fail on a missing topic.
- `auto.register.schemas` is on. It should be off outside development, with schemas
  registered by the build.
- No rate limiting on sends. A counter with a TTL has no equivalent in the database, which
  makes it the natural second use for Redis.
- Conversation list pagination uses offset. Fine for tens of rows; message history requires
  keyset and uses it.
- The gateway holds buffers for both sides of every proxied socket, so per-connection
  memory roughly doubles there while it does nothing but move bytes. At any real socket
  count the gateway becomes the bottleneck — which is the argument for letting clients
  connect to notification directly and leaving the gateway on REST.
- Abandoned consumer groups accumulate: one per notification restart, until the broker
  expires them.
- The gateway actuator endpoint exposes the routing table, including internal service
  addresses. It should be closed before anything public, alongside tightening CORS.

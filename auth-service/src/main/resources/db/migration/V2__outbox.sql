CREATE SEQUENCE auth_schema.outbox_events_seq
    START WITH 1 INCREMENT BY 50;

CREATE TABLE auth_schema.outbox_events (
                                           id             BIGINT       PRIMARY KEY,
                                           aggregate_type VARCHAR(64)  NOT NULL,
                                           aggregate_id   VARCHAR(64)  NOT NULL,
                                           event_type     VARCHAR(64)  NOT NULL,
                                           payload        JSONB        NOT NULL,
                                           created_at     TIMESTAMPTZ  NOT NULL,
                                           published_at   TIMESTAMPTZ,
                                           attempts       INT          NOT NULL DEFAULT 0,
                                           last_error     TEXT
);

CREATE INDEX outbox_events_unpublished_idx
    ON auth_schema.outbox_events (created_at)
    WHERE published_at IS NULL;
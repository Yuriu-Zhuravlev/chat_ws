CREATE SCHEMA IF NOT EXISTS auth_schema;

CREATE SEQUENCE auth_schema.users_seq
    START WITH 1 INCREMENT BY 50;

CREATE TABLE auth_schema.users (
                                   id            BIGINT       PRIMARY KEY,
                                   username      VARCHAR(32)  NOT NULL,
                                   password_hash VARCHAR(72)  NOT NULL,
                                   created_at    TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX users_username_lower_uq
    ON auth_schema.users (lower(username));

CREATE SEQUENCE auth_schema.refresh_tokens_seq
    START WITH 1 INCREMENT BY 50;

CREATE TABLE auth_schema.refresh_tokens (
                                            id         BIGINT      PRIMARY KEY,
                                            user_id    BIGINT      NOT NULL
                                                REFERENCES auth_schema.users(id) ON DELETE CASCADE,
                                            token_hash VARCHAR(64) NOT NULL UNIQUE,
                                            created_at TIMESTAMPTZ NOT NULL,
                                            expires_at TIMESTAMPTZ NOT NULL,
                                            used_at    TIMESTAMPTZ,
                                            revoked_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX refresh_tokens_active_uq
    ON auth_schema.refresh_tokens (user_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;
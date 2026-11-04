CREATE TABLE revoked_tokens (
    jti        text        PRIMARY KEY,
    expires_at timestamptz NOT NULL
);
CREATE INDEX revoked_tokens_expires_idx ON revoked_tokens (expires_at);
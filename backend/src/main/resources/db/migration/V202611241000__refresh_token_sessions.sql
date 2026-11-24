-- One session = the chain of refresh tokens produced by rotation, so a user can list and revoke devices.
-- Existing rows each become their own session through the default.
ALTER TABLE refresh_tokens
    ADD COLUMN session_id   UUID        NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN user_agent   VARCHAR(255),
    ADD COLUMN ip           VARCHAR(45),
    ADD COLUMN last_used_at TIMESTAMPTZ NOT NULL DEFAULT now();

UPDATE refresh_tokens SET last_used_at = created_at;

CREATE INDEX refresh_tokens_user_session_idx ON refresh_tokens (user_id, session_id);

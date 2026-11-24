-- Where a conversation stands for the team: still being handled, or done. Existing rows start OPEN.
ALTER TABLE conversations
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    ADD CONSTRAINT conversations_status_known CHECK (status IN ('OPEN', 'CLOSED'));

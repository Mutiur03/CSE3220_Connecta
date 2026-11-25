-- A conversation is unread from the moment a customer writes until a member reads it. Existing rows start read.
ALTER TABLE conversations
    ADD COLUMN unread BOOLEAN NOT NULL DEFAULT FALSE;

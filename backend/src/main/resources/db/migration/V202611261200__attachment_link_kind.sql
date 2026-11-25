-- A LINK attachment is a web page the customer shared (an Instagram post, reel or story): url is the
-- instagram.com link, never downloaded. No data change.
ALTER TABLE message_attachments
    DROP CONSTRAINT message_attachments_kind_known,
    ADD CONSTRAINT message_attachments_kind_known CHECK (kind IN ('IMAGE', 'VIDEO', 'AUDIO', 'FILE', 'LINK'));

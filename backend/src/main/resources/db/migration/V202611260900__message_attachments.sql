-- A file, image, video or voice note sent with a message (1:M from messages). url is where Meta serves the
-- media. (id, organization_id) on messages lets the attachment prove it belongs to the message's organization.
ALTER TABLE messages
    ADD CONSTRAINT messages_organization_id_key UNIQUE (id, organization_id);

CREATE TABLE message_attachments (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    message_id      UUID         NOT NULL,
    kind            VARCHAR(16)  NOT NULL,
    url             TEXT         NOT NULL,
    file_name       VARCHAR(255),
    content_type    VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT message_attachments_kind_known CHECK (kind IN ('IMAGE', 'VIDEO', 'AUDIO', 'FILE')),
    CONSTRAINT message_attachments_url_not_blank CHECK (btrim(url) <> ''),
    CONSTRAINT message_attachments_message_same_organization_fkey FOREIGN KEY (message_id, organization_id)
        REFERENCES messages (id, organization_id) ON DELETE CASCADE
);

-- Loads the attachments of a page of messages. Also covers the foreign key to messages.
CREATE INDEX message_attachments_message_idx ON message_attachments (message_id);
-- Covers the foreign key to organizations.
CREATE INDEX message_attachments_organization_idx ON message_attachments (organization_id);

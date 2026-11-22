-- A conversation is one chat thread with a customer on one channel (Instagram or Messenger).
-- external_thread_id is the thread id Meta gives us; it is unique per organization and channel, so a
-- repeated webhook for the same thread finds the same row. (id, organization_id) lets child rows prove
-- they belong to the same organization.
CREATE TABLE conversations (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    channel           VARCHAR(16)  NOT NULL,
    external_thread_id VARCHAR(255) NOT NULL,
    last_message_at   TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT conversations_channel_known CHECK (channel IN ('INSTAGRAM', 'MESSENGER')),
    CONSTRAINT conversations_external_thread_not_blank CHECK (btrim(external_thread_id) <> ''),
    CONSTRAINT conversations_external_thread_key UNIQUE (organization_id, channel, external_thread_id),
    CONSTRAINT conversations_organization_id_key UNIQUE (id, organization_id)
);

-- The inbox list: one organization's conversations, newest activity first. Also covers the foreign key
-- to organizations.
CREATE INDEX conversations_organization_last_message_idx ON conversations (organization_id, last_message_at);

-- A customer account on one channel. The same customer may appear in several organizations, so each
-- organization keeps its own participant row.
CREATE TABLE participants (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    channel           VARCHAR(16)  NOT NULL,
    external_id       VARCHAR(255) NOT NULL,
    display_name      VARCHAR(150),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT participants_channel_known CHECK (channel IN ('INSTAGRAM', 'MESSENGER')),
    CONSTRAINT participants_external_id_not_blank CHECK (btrim(external_id) <> ''),
    CONSTRAINT participants_external_id_key UNIQUE (organization_id, channel, external_id),
    CONSTRAINT participants_organization_id_key UNIQUE (id, organization_id)
);

-- Many-to-many link between conversations and participants. One conversation can have several
-- participants (group threads) and one participant can join many conversations.
CREATE TABLE conversation_participants (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    conversation_id UUID         NOT NULL,
    participant_id  UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT conversation_participants_pair_key UNIQUE (conversation_id, participant_id),
    CONSTRAINT conversation_participants_conversation_same_organization_fkey
        FOREIGN KEY (conversation_id, organization_id)
        REFERENCES conversations (id, organization_id) ON DELETE CASCADE,
    CONSTRAINT conversation_participants_participant_same_organization_fkey
        FOREIGN KEY (participant_id, organization_id)
        REFERENCES participants (id, organization_id) ON DELETE CASCADE
);

-- Covers the foreign key to participants (the unique pair covers the conversation side).
CREATE INDEX conversation_participants_participant_idx ON conversation_participants (participant_id);

-- One message in a conversation. An inbound message names the customer who sent it; an outbound one
-- names the agent (user) who sent it. external_message_id is the Meta message id: unique per
-- organization, so a duplicate webhook event cannot create a second message.
CREATE TABLE messages (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    conversation_id       UUID         NOT NULL,
    direction             VARCHAR(16)  NOT NULL,
    sender_participant_id UUID,
    sent_by_user_id       UUID REFERENCES users (id) ON DELETE SET NULL,
    external_message_id   VARCHAR(255),
    body                  TEXT         NOT NULL,
    sent_at               TIMESTAMPTZ  NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT messages_direction_known CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT messages_inbound_has_sender CHECK (direction <> 'INBOUND' OR sender_participant_id IS NOT NULL),
    CONSTRAINT messages_body_not_blank CHECK (btrim(body) <> ''),
    CONSTRAINT messages_external_message_id_key UNIQUE (organization_id, external_message_id),
    CONSTRAINT messages_conversation_same_organization_fkey FOREIGN KEY (conversation_id, organization_id)
        REFERENCES conversations (id, organization_id) ON DELETE CASCADE,
    CONSTRAINT messages_sender_same_organization_fkey FOREIGN KEY (sender_participant_id, organization_id)
        REFERENCES participants (id, organization_id) ON DELETE SET NULL (sender_participant_id)
);

-- A conversation's timeline, oldest first. Also covers the foreign key to conversations.
CREATE INDEX messages_conversation_sent_idx ON messages (conversation_id, sent_at);
-- Covers the foreign key to participants (sender).
CREATE INDEX messages_sender_participant_idx ON messages (sender_participant_id);
-- Covers the foreign key to users (agent who sent the message).
CREATE INDEX messages_sent_by_user_idx ON messages (sent_by_user_id);

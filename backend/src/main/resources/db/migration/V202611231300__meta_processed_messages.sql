-- One row per message an organization has already received from Meta. The webhook can deliver the same message
-- more than once (Meta retries an event that got no 200), so the unique key makes the second delivery a no-op.
-- The key is per organization: two organizations may both be connected to the same Page or account.
CREATE TABLE meta_processed_messages (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    object              VARCHAR(16)  NOT NULL,
    external_message_id VARCHAR(255) NOT NULL,
    processed_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT meta_processed_messages_object_known CHECK (object IN ('PAGE', 'INSTAGRAM')),
    CONSTRAINT meta_processed_messages_mid_not_blank CHECK (btrim(external_message_id) <> ''),
    CONSTRAINT meta_processed_messages_once_key UNIQUE (organization_id, object, external_message_id)
);

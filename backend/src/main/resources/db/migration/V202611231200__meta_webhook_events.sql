-- Verified Meta webhook deliveries, kept as received until processed (META-06). The raw JSON is stored exactly
-- as Meta sent it, because the signature covers those bytes. processed_at stays null until the event is handled.
CREATE TABLE meta_webhook_events (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    object       VARCHAR(16)  NOT NULL,
    payload      TEXT         NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL,
    processed_at TIMESTAMPTZ,
    CONSTRAINT meta_webhook_events_object_known CHECK (object IN ('PAGE', 'INSTAGRAM')),
    CONSTRAINT meta_webhook_events_payload_not_blank CHECK (btrim(payload) <> '')
);

-- Events still waiting to be processed, oldest first.
CREATE INDEX meta_webhook_events_pending_idx ON meta_webhook_events (received_at) WHERE processed_at IS NULL;

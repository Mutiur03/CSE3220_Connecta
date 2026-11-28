-- Analytics count an organization's messages by sent_at over a date range. The only sent_at index is per
-- conversation, so without this every report would scan the whole messages table.
CREATE INDEX messages_organization_sent_idx ON messages (organization_id, sent_at);

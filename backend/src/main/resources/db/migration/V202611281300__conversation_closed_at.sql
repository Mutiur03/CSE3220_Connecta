-- When a conversation was last closed and by whom, for the resolved count in agent reports. Both are set
-- when it closes and cleared when it reopens, so only the latest close counts. Rows closed before this
-- migration stay null and are not counted. The closing user may leave; the close time is kept.
ALTER TABLE conversations
    ADD COLUMN closed_at         TIMESTAMPTZ,
    ADD COLUMN closed_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL;

-- Resolved conversations of one organization in a report range.
CREATE INDEX conversations_organization_closed_idx ON conversations (organization_id, closed_at);
-- Covers the foreign key to users (who closed it).
CREATE INDEX conversations_closed_by_user_idx ON conversations (closed_by_user_id);

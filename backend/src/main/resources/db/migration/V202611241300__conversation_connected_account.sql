-- Records which connected account a conversation belongs to, so a reply leaves from the account the customer
-- wrote to. Two real foreign keys instead of one polymorphic id: a Messenger conversation points at a
-- Facebook Page, an Instagram one at an Instagram account. ON DELETE SET NULL keeps the history when an
-- account is disconnected; the send path then treats the conversation as unlinked.
ALTER TABLE conversations
    ADD COLUMN meta_page_id UUID REFERENCES meta_connected_pages (id) ON DELETE SET NULL,
    ADD COLUMN instagram_account_id UUID REFERENCES meta_connected_accounts (id) ON DELETE SET NULL,
    ADD CONSTRAINT conversations_account_matches_channel CHECK (
        (meta_page_id IS NULL OR channel = 'MESSENGER')
        AND (instagram_account_id IS NULL OR channel = 'INSTAGRAM'));

-- Cover both foreign keys (looked up when an account is deleted).
CREATE INDEX conversations_meta_page_idx ON conversations (meta_page_id);
CREATE INDEX conversations_instagram_account_idx ON conversations (instagram_account_id);

-- Backfill only the unambiguous rows: an organization with exactly one account of the channel. Conversations
-- of organizations with several accounts stay NULL.
UPDATE conversations c
SET meta_page_id = p.id
FROM meta_connected_pages p
WHERE c.channel = 'MESSENGER'
  AND p.organization_id = c.organization_id
  AND (SELECT count(*) FROM meta_connected_pages x WHERE x.organization_id = c.organization_id) = 1;

UPDATE conversations c
SET instagram_account_id = a.id
FROM meta_connected_accounts a
WHERE c.channel = 'INSTAGRAM'
  AND a.platform = 'INSTAGRAM'
  AND a.organization_id = c.organization_id
  AND (SELECT count(*) FROM meta_connected_accounts x
       WHERE x.organization_id = c.organization_id AND x.platform = 'INSTAGRAM') = 1;

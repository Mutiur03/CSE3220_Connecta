-- Incoming media is copied once into stored_files, because Meta's media URLs expire (M:1 to stored_files).
-- source_url: the Meta link the attachment came from; set only for media that arrived from Meta, so it is what
--   the mirror looks for. NULL for files our agents sent (their url already points at /api/files/{id}).
-- stored_file_id: our copy; url then holds its public URL. Deleting the file sets it back to NULL.
-- mirror_attempted_at: last copy attempt. A failed copy is retried later, least recently tried first.
ALTER TABLE message_attachments
    ADD COLUMN source_url          TEXT,
    ADD COLUMN stored_file_id      UUID REFERENCES stored_files (id) ON DELETE SET NULL,
    ADD COLUMN mirror_attempted_at TIMESTAMPTZ,
    ADD CONSTRAINT message_attachments_source_url_not_blank CHECK (source_url IS NULL OR btrim(source_url) <> '');

-- Existing rows from Meta's CDN keep working as copy sources. Outgoing rows (our /api/files/ URL) do not match.
UPDATE message_attachments
SET source_url = url
WHERE url ~* '^https://([a-z0-9-]+\.)*(fbcdn\.net|cdninstagram\.com|fbsbx\.com|facebook\.com)(:443)?/';

-- Covers the foreign key to stored_files.
CREATE INDEX message_attachments_stored_file_idx ON message_attachments (stored_file_id);
-- The retry sweep: attachments from Meta that have no copy yet.
CREATE INDEX message_attachments_mirror_pending_idx ON message_attachments (created_at)
    WHERE source_url IS NOT NULL AND stored_file_id IS NULL;

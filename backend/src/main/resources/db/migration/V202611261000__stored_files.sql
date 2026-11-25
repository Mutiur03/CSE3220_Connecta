-- Files the app serves at a public, unguessable URL (GET /api/files/{id}), for example an agent's attachment that
-- Meta fetches before delivering it. Render has no persistent disk, so the bytes live here; the upload size limit
-- (app.attachments.max-bytes) keeps each row small. Deleting an organization deletes its files.
CREATE TABLE stored_files (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID          NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    content_type    VARCHAR(100)  NOT NULL,
    file_name       VARCHAR(255),
    size_bytes      BIGINT        NOT NULL,
    data            BYTEA         NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT stored_files_content_type_not_blank CHECK (btrim(content_type) <> ''),
    CONSTRAINT stored_files_size_positive CHECK (size_bytes > 0),
    CONSTRAINT stored_files_size_matches_data CHECK (size_bytes = octet_length(data))
);

-- Covers the foreign key to organizations.
CREATE INDEX stored_files_organization_idx ON stored_files (organization_id);

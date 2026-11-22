-- A Facebook Page the organization connected for Messenger. page_access_token_encrypted is the Page token,
-- encrypted with TokenCipher, and never leaves the server. One row per organization and Page: reconnecting
-- the same Page replaces its token. granted_by_user_id is nulled if that admin is deleted; the row stays.
CREATE TABLE meta_connected_pages (
    id                          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id             UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    page_id                     VARCHAR(64)  NOT NULL,
    page_name                   VARCHAR(255) NOT NULL,
    page_access_token_encrypted TEXT         NOT NULL,
    granted_by_user_id          UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT meta_connected_pages_organization_page_key UNIQUE (organization_id, page_id),
    CONSTRAINT meta_connected_pages_page_id_not_blank CHECK (btrim(page_id) <> ''),
    CONSTRAINT meta_connected_pages_token_not_blank CHECK (btrim(page_access_token_encrypted) <> '')
);

-- Covers the foreign key to users. The unique (organization_id, page_id) covers the foreign key to organizations.
CREATE INDEX meta_connected_pages_granted_by_idx ON meta_connected_pages (granted_by_user_id);

-- The Meta (Facebook Login for Business) grant of one organization: the user token an admin authorized, kept only
-- encrypted (AES-GCM, see meta.service.TokenCipher). One grant per organization; reconnecting replaces it.
-- granted_by_user_id names the admin who connected; it is nulled if that user is deleted, the grant stays.
CREATE TABLE meta_authorizations (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id        UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    granted_by_user_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    access_token_encrypted TEXT         NOT NULL,
    expires_at             TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT meta_authorizations_organization_key UNIQUE (organization_id),
    CONSTRAINT meta_authorizations_token_not_blank CHECK (btrim(access_token_encrypted) <> '')
);

-- Covers the foreign key to users. The unique organization_id covers the foreign key to organizations.
CREATE INDEX meta_authorizations_granted_by_idx ON meta_authorizations (granted_by_user_id);

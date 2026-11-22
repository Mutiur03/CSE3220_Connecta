-- Meta accounts an organization connected through a Meta login: the Instagram professional account (Instagram
-- Business Login). The access token is stored only encrypted (see meta.service.TokenCipher). One row per organization,
-- platform and Meta account id, so reconnecting refreshes the same row.
CREATE TABLE meta_connected_accounts (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id        UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    platform               VARCHAR(16)  NOT NULL,
    external_account_id    VARCHAR(64)  NOT NULL,
    username               VARCHAR(150),
    access_token_encrypted TEXT         NOT NULL,
    granted_by_user_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT meta_connected_accounts_platform_known CHECK (platform IN ('INSTAGRAM')),
    CONSTRAINT meta_connected_accounts_external_id_not_blank CHECK (btrim(external_account_id) <> ''),
    CONSTRAINT meta_connected_accounts_token_not_blank CHECK (btrim(access_token_encrypted) <> ''),
    CONSTRAINT meta_connected_accounts_account_key UNIQUE (organization_id, platform, external_account_id)
);

-- The unique key above (organization first) covers the foreign key to organizations.
-- Covers the foreign key to users.
CREATE INDEX meta_connected_accounts_granted_by_idx ON meta_connected_accounts (granted_by_user_id);

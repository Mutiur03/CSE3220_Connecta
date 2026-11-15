-- A tenant. Slugs are lower-case kebab-case and unique across the whole system.
CREATE TABLE organizations (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(60)  NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT organizations_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT organizations_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

-- Joins users to organizations (many-to-many) and carries the user's role inside one organization.
-- organization_id is covered by the leading column of the unique key, so it has no separate index.
CREATE TABLE memberships (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id         UUID        NOT NULL REFERENCES roles (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT memberships_organization_user_key UNIQUE (organization_id, user_id)
);

CREATE INDEX memberships_user_id_idx ON memberships (user_id);
CREATE INDEX memberships_role_id_idx ON memberships (role_id);

-- An offer to join an organization, addressed to an email. Only the SHA-256 hash of the token is stored;
-- the raw token is returned once, when the invitation is created.
CREATE TABLE invitations (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    email           VARCHAR(254) NOT NULL,
    role_id         UUID         NOT NULL REFERENCES roles (id),
    invited_by      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash      CHAR(64)     NOT NULL UNIQUE,
    status          VARCHAR(20)  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    accepted_at     TIMESTAMPTZ,
    CONSTRAINT invitations_email_format CHECK (email = lower(email) AND btrim(email) <> ''),
    CONSTRAINT invitations_status_check CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED')),
    CONSTRAINT invitations_accepted_at_matches_status CHECK ((status = 'ACCEPTED') = (accepted_at IS NOT NULL))
);

CREATE INDEX invitations_organization_id_idx ON invitations (organization_id);
CREATE INDEX invitations_role_id_idx ON invitations (role_id);
CREATE INDEX invitations_invited_by_idx ON invitations (invited_by);

-- At most one open invitation per organization and email.
CREATE UNIQUE INDEX invitations_one_pending_per_email ON invitations (organization_id, email)
    WHERE status = 'PENDING';

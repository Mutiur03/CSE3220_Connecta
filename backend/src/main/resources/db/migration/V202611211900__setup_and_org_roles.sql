-- One row, written once, when the first-run setup creates the system OWNER. The primary key and the
-- check make a second insert fail, so two concurrent setups cannot both succeed.
CREATE TABLE system_setup (
    id           SMALLINT    PRIMARY KEY CHECK (id = 1),
    owner_id     UUID        NOT NULL REFERENCES users (id),
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX system_setup_owner_id_idx ON system_setup (owner_id);

-- OWNER is now the system owner (a global role). Inside an organization only ADMIN and AGENT exist.
UPDATE memberships
SET role_id = (SELECT id FROM roles WHERE name = 'ADMIN')
WHERE role_id = (SELECT id FROM roles WHERE name = 'OWNER');

UPDATE invitations
SET role_id = (SELECT id FROM roles WHERE name = 'ADMIN')
WHERE role_id = (SELECT id FROM roles WHERE name = 'OWNER');

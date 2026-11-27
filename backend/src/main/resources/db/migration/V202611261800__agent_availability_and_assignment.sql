-- (id, organization_id) lets assignment rows prove the member belongs to the same organization.
ALTER TABLE memberships
    ADD CONSTRAINT memberships_organization_id_key UNIQUE (id, organization_id);

-- Whether a member takes new chats. One row per membership; a member without a row is not available,
-- so automatic assignment only picks members who opted in. last_assigned_at is the round-robin cursor:
-- the next automatic assignment goes to the available member who waited longest (never assigned first).
CREATE TABLE agent_availability (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    membership_id    UUID        NOT NULL,
    status           VARCHAR(16) NOT NULL,
    last_assigned_at TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT agent_availability_status_known CHECK (status IN ('AVAILABLE', 'AWAY')),
    CONSTRAINT agent_availability_membership_key UNIQUE (membership_id),
    CONSTRAINT agent_availability_membership_same_organization_fkey
        FOREIGN KEY (membership_id, organization_id)
        REFERENCES memberships (id, organization_id) ON DELETE CASCADE
);

-- Round-robin pick: an organization's available members, least recently assigned first. Also covers
-- the foreign key to organizations.
CREATE INDEX agent_availability_round_robin_idx
    ON agent_availability (organization_id, status, last_assigned_at);

-- The member currently handling a conversation. Null means unassigned; it becomes null again when the
-- member leaves the organization.
ALTER TABLE conversations
    ADD COLUMN assignee_membership_id UUID,
    ADD CONSTRAINT conversations_assignee_same_organization_fkey
        FOREIGN KEY (assignee_membership_id, organization_id)
        REFERENCES memberships (id, organization_id) ON DELETE SET NULL (assignee_membership_id);

-- Workload per member (open conversations assigned to them). Also covers the foreign key.
CREATE INDEX conversations_assignee_status_idx ON conversations (assignee_membership_id, status);

-- Append-only history of who a conversation was handed to. A null from_membership_id is the first
-- assignment, a null to_membership_id an unassignment. Rows stay when a member or the acting user is
-- removed (their column becomes null), so the history is kept.
CREATE TABLE conversation_assignments (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    conversation_id     UUID        NOT NULL,
    from_membership_id  UUID,
    to_membership_id    UUID,
    assigned_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    source              VARCHAR(32) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT conversation_assignments_source_known CHECK (source IN ('AUTO_ROUND_ROBIN', 'MANUAL')),
    CONSTRAINT conversation_assignments_conversation_same_organization_fkey
        FOREIGN KEY (conversation_id, organization_id)
        REFERENCES conversations (id, organization_id) ON DELETE CASCADE,
    CONSTRAINT conversation_assignments_from_same_organization_fkey
        FOREIGN KEY (from_membership_id, organization_id)
        REFERENCES memberships (id, organization_id) ON DELETE SET NULL (from_membership_id),
    CONSTRAINT conversation_assignments_to_same_organization_fkey
        FOREIGN KEY (to_membership_id, organization_id)
        REFERENCES memberships (id, organization_id) ON DELETE SET NULL (to_membership_id)
);

-- A conversation's history, newest first. Also covers the foreign key to conversations.
CREATE INDEX conversation_assignments_conversation_created_idx
    ON conversation_assignments (conversation_id, created_at);
CREATE INDEX conversation_assignments_from_membership_idx ON conversation_assignments (from_membership_id);
CREATE INDEX conversation_assignments_to_membership_idx ON conversation_assignments (to_membership_id);
CREATE INDEX conversation_assignments_assigned_by_user_idx ON conversation_assignments (assigned_by_user_id);
CREATE INDEX conversation_assignments_organization_idx ON conversation_assignments (organization_id);

-- Time a member spent AVAILABLE: one row per stretch, open while ended_at is null. A member has at
-- most one open stretch. Rows go when the member or the organization is deleted.
CREATE TABLE availability_intervals (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    membership_id   UUID        NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL,
    ended_at        TIMESTAMPTZ,
    CONSTRAINT availability_intervals_end_after_start CHECK (ended_at IS NULL OR ended_at >= started_at),
    CONSTRAINT availability_intervals_membership_same_organization_fkey
        FOREIGN KEY (membership_id, organization_id)
        REFERENCES memberships (id, organization_id) ON DELETE CASCADE
);

-- At most one open interval per member. Also covers the foreign key to memberships for open rows.
CREATE UNIQUE INDEX availability_intervals_one_open_idx
    ON availability_intervals (membership_id) WHERE ended_at IS NULL;

-- Member history by time, and the report range scan. The leading column covers organization_id.
CREATE INDEX availability_intervals_organization_started_idx
    ON availability_intervals (organization_id, started_at);
CREATE INDEX availability_intervals_membership_started_idx
    ON availability_intervals (membership_id, started_at);

-- Members already AVAILABLE when this ships get an open interval from their last status change.
INSERT INTO availability_intervals (organization_id, membership_id, started_at)
SELECT organization_id, membership_id, updated_at
FROM agent_availability
WHERE status = 'AVAILABLE';

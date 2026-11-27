-- Admin-controlled switch: only assignable members get automatic assignments, online or not.
-- Backfilled from today's AVAILABLE rows so behavior does not change on deploy.
ALTER TABLE agent_availability ADD COLUMN assignable BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE agent_availability SET assignable = TRUE WHERE status = 'AVAILABLE';

-- Round-robin pick now filters on assignable instead of status.
DROP INDEX agent_availability_round_robin_idx;
CREATE INDEX agent_availability_assignable_idx
    ON agent_availability (organization_id, assignable, last_assigned_at);

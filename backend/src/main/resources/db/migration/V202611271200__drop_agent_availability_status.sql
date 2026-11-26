-- The manual Available/Away status is gone: eligibility is the admin assignable flag and presence is
-- automatic. Dropping the column also drops its CHECK constraint (agent_availability_status_known).
ALTER TABLE agent_availability DROP COLUMN status;

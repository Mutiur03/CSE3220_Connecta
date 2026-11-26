-- When a member was last known to be connected. A heartbeat refreshes it for open intervals, and a
-- backend restart closes a still-open interval there instead of at its start. Nullable so rows written
-- by older code stay valid; every existing row is backfilled below.
ALTER TABLE availability_intervals ADD COLUMN last_seen_at TIMESTAMPTZ;

UPDATE availability_intervals SET last_seen_at = COALESCE(ended_at, started_at);

-- Safe after the backfill: ended_at is already checked to be at or after started_at.
ALTER TABLE availability_intervals
    ADD CONSTRAINT availability_intervals_last_seen_after_start
    CHECK (last_seen_at IS NULL OR last_seen_at >= started_at);

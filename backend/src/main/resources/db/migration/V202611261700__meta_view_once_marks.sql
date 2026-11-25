-- Timestamps (ms) of view-once photo echoes; the receiver's event carries the same timestamp.
-- No tenant data and no message content; rows are deleted by a scheduled cleanup after about an hour.
CREATE TABLE meta_view_once_marks (
    sent_at_ms BIGINT PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX meta_view_once_marks_created_idx ON meta_view_once_marks (created_at);

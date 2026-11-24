-- Why the last history import of a connection failed (PERMISSION or FETCH, never Meta's text) and when.
-- Both are cleared by the next successful import, so NULL means no known failure.
ALTER TABLE meta_connected_pages
    ADD COLUMN history_import_error VARCHAR(16),
    ADD COLUMN history_import_failed_at TIMESTAMPTZ,
    ADD CONSTRAINT meta_connected_pages_history_import_error_check
        CHECK (history_import_error IN ('PERMISSION', 'FETCH'));
ALTER TABLE meta_connected_accounts
    ADD COLUMN history_import_error VARCHAR(16),
    ADD COLUMN history_import_failed_at TIMESTAMPTZ,
    ADD CONSTRAINT meta_connected_accounts_history_import_error_check
        CHECK (history_import_error IN ('PERMISSION', 'FETCH'));

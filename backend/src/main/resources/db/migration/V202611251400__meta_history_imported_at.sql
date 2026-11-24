-- When a connection's past conversations were imported to the end of a run. NULL means not yet (or failed),
-- so the startup catch-up and a reconnect retry it.
ALTER TABLE meta_connected_pages ADD COLUMN history_imported_at TIMESTAMPTZ;
ALTER TABLE meta_connected_accounts ADD COLUMN history_imported_at TIMESTAMPTZ;

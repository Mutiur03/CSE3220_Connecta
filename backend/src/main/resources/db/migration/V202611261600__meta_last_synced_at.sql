-- When the scheduled recent-message sync last finished without error for this connection (NULL until one did).
ALTER TABLE meta_connected_pages    ADD COLUMN last_synced_at TIMESTAMPTZ;
ALTER TABLE meta_connected_accounts ADD COLUMN last_synced_at TIMESTAMPTZ;

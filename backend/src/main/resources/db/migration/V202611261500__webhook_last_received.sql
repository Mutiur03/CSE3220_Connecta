-- When a webhook for the connection last arrived and was routed to it; null until one does.
ALTER TABLE meta_connected_pages ADD COLUMN last_webhook_at TIMESTAMPTZ;
ALTER TABLE meta_connected_accounts ADD COLUMN last_webhook_at TIMESTAMPTZ;

-- Every change to a product's stock is one row here. RECEIVE adds, ISSUE removes, ADJUST sets the level;
-- quantity_after is the stock level once the movement is applied and can never be negative.
CREATE TABLE stock_movements (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    product_id      UUID         NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    type            VARCHAR(16)  NOT NULL,
    quantity_delta  INTEGER      NOT NULL,
    quantity_after  INTEGER      NOT NULL,
    reason          VARCHAR(255),
    created_by      UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT stock_movements_type_known CHECK (type IN ('RECEIVE', 'ISSUE', 'ADJUST')),
    CONSTRAINT stock_movements_quantity_after_not_negative CHECK (quantity_after >= 0),
    CONSTRAINT stock_movements_delta_matches_type CHECK (
        (type = 'RECEIVE' AND quantity_delta > 0)
        OR (type = 'ISSUE' AND quantity_delta < 0)
        OR type = 'ADJUST'),
    CONSTRAINT stock_movements_reason_not_blank CHECK (reason IS NULL OR btrim(reason) <> '')
);

-- Product history, newest first; also covers the foreign key to products.
CREATE INDEX stock_movements_product_created_idx ON stock_movements (product_id, created_at);
-- Organization-wide history, newest first; also covers the foreign key to organizations.
CREATE INDEX stock_movements_organization_created_idx ON stock_movements (organization_id, created_at);
-- Covers the foreign key to users.
CREATE INDEX stock_movements_created_by_idx ON stock_movements (created_by);

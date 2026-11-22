-- Inventory fields on products. stock_quantity is only changed by stock movements (a later migration).
ALTER TABLE products
    ADD COLUMN sku            VARCHAR(64),
    ADD COLUMN unit_price     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN stock_quantity INTEGER        NOT NULL DEFAULT 0,
    ADD COLUMN reorder_level  INTEGER        NOT NULL DEFAULT 0,
    ADD CONSTRAINT products_unit_price_not_negative CHECK (unit_price >= 0),
    ADD CONSTRAINT products_stock_quantity_not_negative CHECK (stock_quantity >= 0),
    ADD CONSTRAINT products_reorder_level_not_negative CHECK (reorder_level >= 0),
    ADD CONSTRAINT products_sku_not_blank CHECK (sku IS NULL OR btrim(sku) <> '');

-- A SKU is unique inside an organization, ignoring case. Products without a SKU never clash.
CREATE UNIQUE INDEX products_organization_sku_key ON products (organization_id, lower(sku)) WHERE sku IS NOT NULL;

-- Serves the stock status filters (out of stock, low stock) within one organization.
CREATE INDEX products_organization_stock_idx ON products (organization_id, stock_quantity, reorder_level);

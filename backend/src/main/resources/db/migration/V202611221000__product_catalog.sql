-- A product category belongs to one organization. Names are unique inside an organization, so two
-- tenants can both have a "Shoes" category. The (id, organization_id) key lets products reference a
-- category only when both belong to the same organization.
CREATE TABLE product_categories (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT product_categories_organization_name_key UNIQUE (organization_id, name),
    CONSTRAINT product_categories_organization_id_key UNIQUE (id, organization_id),
    CONSTRAINT product_categories_name_not_blank CHECK (btrim(name) <> '')
);

-- A product belongs to one organization and optionally to one category (many products to one category).
-- Deleting a category leaves its products uncategorized; only category_id is nulled, never organization_id.
CREATE TABLE products (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    category_id     UUID,
    name            VARCHAR(150) NOT NULL,
    description     VARCHAR(1000),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT products_organization_name_key UNIQUE (organization_id, name),
    CONSTRAINT products_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT products_category_same_organization_fkey FOREIGN KEY (category_id, organization_id)
        REFERENCES product_categories (id, organization_id) ON DELETE SET NULL (category_id)
);

-- Covers the foreign key to a category and the "products in this category" filter.
CREATE INDEX products_category_id_idx ON products (category_id);

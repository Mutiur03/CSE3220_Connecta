-- Image metadata only; the bytes live in the object store under object_key. Rows go with their product
-- (ON DELETE CASCADE); the stored objects are removed by the application after the product delete commits.
CREATE TABLE product_images (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    product_id      UUID         NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    object_key      VARCHAR(300) NOT NULL,
    content_type    VARCHAR(50)  NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    position        INTEGER      NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT product_images_object_key_key UNIQUE (object_key),
    CONSTRAINT product_images_size_positive CHECK (size_bytes > 0),
    CONSTRAINT product_images_position_not_negative CHECK (position >= 0)
);

CREATE INDEX product_images_organization_id_idx ON product_images (organization_id);
CREATE INDEX product_images_product_id_idx ON product_images (product_id, position);

-- V1__product_schema.sql
-- Product module initial schema
-- Designed for PostgreSQL 15+

-- ─────────────────────────────────────────────────────────────────
--  Products table
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS products (
    id           VARCHAR(36)  NOT NULL,
    tenant_id    VARCHAR(64)  NOT NULL,

    -- Core identity
    sku          VARCHAR(128) NOT NULL,
    name         VARCHAR(512) NOT NULL,
    description  TEXT,
    type         VARCHAR(32)  NOT NULL,  -- ProductType enum
    category_id  VARCHAR(64),
    brand_id     VARCHAR(64),
    external_ref VARCHAR(128),

    -- Lifecycle
    status       VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',

    -- Schema-free extension data (one JSONB column per extension context)
    extensions   JSONB        NOT NULL DEFAULT '{}',

    -- Arbitrary metadata
    attributes   JSONB        NOT NULL DEFAULT '{}',
    labels       JSONB        NOT NULL DEFAULT '{}',

    -- Optimistic locking
    version      BIGINT       NOT NULL DEFAULT 1,

    -- Audit
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   VARCHAR(128),
    updated_by   VARCHAR(128),

    CONSTRAINT pk_products PRIMARY KEY (id),

    -- Unique SKU per tenant
    CONSTRAINT uq_products_tenant_sku UNIQUE (tenant_id, sku),

    -- Status constraint
    CONSTRAINT ck_products_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'ARCHIVED')
    ),

    -- Type constraint
    CONSTRAINT ck_products_type CHECK (
        type IN ('PHYSICAL', 'DIGITAL', 'SUBSCRIPTION', 'SERVICE',
                 'VOUCHER', 'BUNDLE', 'VARIANT_PARENT', 'VARIANT')
    )
);

-- ─────────────────────────────────────────────────────────────────
--  Indexes
-- ─────────────────────────────────────────────────────────────────

-- Primary lookup patterns
CREATE INDEX idx_products_tenant_status  ON products (tenant_id, status);
CREATE INDEX idx_products_tenant_cat     ON products (tenant_id, category_id);
CREATE INDEX idx_products_tenant_type    ON products (tenant_id, type);
CREATE INDEX idx_products_tenant_brand   ON products (tenant_id, brand_id);
CREATE INDEX idx_products_updated_at     ON products (updated_at DESC);

-- Extension context existence lookup (jsonb ? 'key' operator)
-- e.g.: SELECT * FROM products WHERE extensions ? 'ecommerce'
CREATE INDEX idx_products_extensions_gin ON products USING GIN (extensions);

-- Attribute/label filter
CREATE INDEX idx_products_attributes_gin ON products USING GIN (attributes);
CREATE INDEX idx_products_labels_gin     ON products USING GIN (labels);

-- ─────────────────────────────────────────────────────────────────
--  Product outbox table (transactional event publishing)
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS product_outbox (
    id           BIGSERIAL    NOT NULL,
    event_id     VARCHAR(36)  NOT NULL UNIQUE,
    tenant_id    VARCHAR(64)  NOT NULL,
    product_id   VARCHAR(36)  NOT NULL,
    event_type   VARCHAR(128) NOT NULL,
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    retry_count  INT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_product_outbox PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_unpublished ON product_outbox (created_at)
    WHERE published_at IS NULL;

-- ─────────────────────────────────────────────────────────────────
--  Updated_at auto-maintenance trigger
-- ─────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_products_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

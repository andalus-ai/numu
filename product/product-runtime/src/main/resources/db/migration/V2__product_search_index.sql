-- V2__product_search_index.sql
-- Full-text search support using PostgreSQL tsvector + GIN index

-- Generated tsvector column (auto-maintained by PostgreSQL)
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS search_vector TSVECTOR
        GENERATED ALWAYS AS (
            setweight(to_tsvector('english', coalesce(name, '')),       'A') ||
            setweight(to_tsvector('english', coalesce(sku, '')),        'A') ||
            setweight(to_tsvector('english', coalesce(description, '')), 'B')
        ) STORED;

-- GIN index on the generated tsvector column
CREATE INDEX idx_products_search_gin
    ON products USING GIN (search_vector);

-- Convenience: also support Indonesian language tokenization
-- (Install pg_trgm for trigram similarity search as well)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_products_name_trgm  ON products USING GIN (name  gin_trgm_ops);
CREATE INDEX idx_products_sku_trgm   ON products USING GIN (sku   gin_trgm_ops);

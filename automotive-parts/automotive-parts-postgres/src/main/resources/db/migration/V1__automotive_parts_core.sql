CREATE TABLE automotive_part_profile (
    tenant_id TEXT NOT NULL,
    product_id UUID NOT NULL,
    part_kind TEXT NOT NULL CHECK (part_kind IN (
        'REPLACEMENT_PART', 'CONSUMABLE', 'FLUID', 'ACCESSORY', 'ASSEMBLY', 'TOOL'
    )),
    description TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, product_id)
);

CREATE TABLE automotive_part_reference (
    tenant_id TEXT NOT NULL,
    product_id UUID NOT NULL,
    reference_type TEXT NOT NULL CHECK (reference_type IN (
        'OEM', 'AFTERMARKET', 'SUPPLIER_SKU', 'BARCODE', 'LEGACY'
    )),
    reference_value TEXT NOT NULL,
    issuer TEXT,
    market TEXT,
    normalized_value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, product_id, reference_type, normalized_value, COALESCE(issuer, ''), COALESCE(market, '')),
    CONSTRAINT automotive_part_reference_profile_fk
        FOREIGN KEY (tenant_id, product_id)
        REFERENCES automotive_part_profile (tenant_id, product_id)
        ON DELETE CASCADE
);

-- A reference namespace is unique per tenant; the same raw code may be reused by
-- a different issuer or market. COALESCE prevents NULL namespaces bypassing uniqueness.
CREATE UNIQUE INDEX automotive_part_reference_namespace_uq
    ON automotive_part_reference (
        tenant_id, reference_type, COALESCE(issuer, ''), COALESCE(market, ''), normalized_value
    );

CREATE INDEX automotive_part_reference_product_idx
    ON automotive_part_reference (tenant_id, product_id);

CREATE TABLE automotive_vehicle_fitment (
    fitment_id UUID PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    product_id UUID NOT NULL,
    market TEXT,
    make TEXT NOT NULL,
    model TEXT NOT NULL,
    generation TEXT,
    from_model_year INTEGER NOT NULL CHECK (from_model_year BETWEEN 1886 AND 2200),
    through_model_year INTEGER NOT NULL CHECK (through_model_year BETWEEN from_model_year AND 2200),
    engine_code TEXT,
    trim_name TEXT,
    evidence_source TEXT NOT NULL,
    evidence_reference TEXT,
    effective_from DATE NOT NULL,
    effective_through DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (effective_through IS NULL OR effective_through >= effective_from),
    FOREIGN KEY (tenant_id, product_id)
        REFERENCES automotive_part_profile (tenant_id, product_id)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX automotive_vehicle_fitment_claim_uq
    ON automotive_vehicle_fitment (
        tenant_id, product_id, COALESCE(market, ''), make, model,
        COALESCE(generation, ''), from_model_year, through_model_year,
        COALESCE(engine_code, ''), COALESCE(trim_name, ''), evidence_source,
        COALESCE(evidence_reference, ''), effective_from,
        COALESCE(effective_through, DATE '9999-12-31')
    );

CREATE INDEX automotive_vehicle_fitment_search_idx
    ON automotive_vehicle_fitment (tenant_id, make, model, from_model_year, through_model_year, effective_from, effective_through);

CREATE TABLE automotive_part_substitution (
    substitution_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id TEXT NOT NULL,
    source_product_id UUID NOT NULL,
    substitute_product_id UUID NOT NULL,
    substitution_kind TEXT NOT NULL CHECK (substitution_kind IN (
        'APPROVED_EQUIVALENT', 'OEM_SUPERSESSION', 'CONDITIONAL', 'EMERGENCY_ONLY'
    )),
    requires_technician_approval BOOLEAN NOT NULL,
    rationale TEXT NOT NULL DEFAULT '',
    effective_from DATE NOT NULL,
    effective_through DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (source_product_id <> substitute_product_id),
    CHECK (effective_through IS NULL OR effective_through >= effective_from),
    FOREIGN KEY (tenant_id, source_product_id)
        REFERENCES automotive_part_profile (tenant_id, product_id)
        ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, substitute_product_id)
        REFERENCES automotive_part_profile (tenant_id, product_id)
        ON DELETE CASCADE
);

-- Idempotent retries of the same directed policy must not create duplicate rows.
CREATE UNIQUE INDEX automotive_part_substitution_claim_uq
    ON automotive_part_substitution (
        tenant_id, source_product_id, substitute_product_id, substitution_kind,
        effective_from, COALESCE(effective_through, DATE '9999-12-31')
    );

CREATE INDEX automotive_part_substitution_lookup_idx
    ON automotive_part_substitution (tenant_id, source_product_id, effective_from, effective_through);

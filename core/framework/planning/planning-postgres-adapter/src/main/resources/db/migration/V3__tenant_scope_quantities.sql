-- Neutral quantity schedules are tenant-owned and versioned independently of domain adapters.
-- Pricing, currency, tax, and construction-specific BoQ policies belong in extensions.
CREATE TABLE IF NOT EXISTS planning_scope_quantity_definition (
    tenant_id UUID NOT NULL,
    definition_id UUID NOT NULL,
    scope_type VARCHAR(100) NOT NULL,
    scope_id VARCHAR(240) NOT NULL,
    quantity_code VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    unit_code VARCHAR(32) NOT NULL,
    planned_quantity NUMERIC(20,6) NOT NULL CHECK (planned_quantity >= 0),
    revision INTEGER NOT NULL CHECK (revision >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, definition_id),
    CONSTRAINT uq_scope_quantity_revision UNIQUE (tenant_id, scope_type, scope_id, quantity_code, revision)
);

CREATE INDEX IF NOT EXISTS ix_scope_quantity_lookup
    ON planning_scope_quantity_definition(tenant_id, scope_type, scope_id, quantity_code, revision DESC);

CREATE TABLE IF NOT EXISTS planning_quantity_measurement (
    tenant_id UUID NOT NULL,
    measurement_id UUID NOT NULL,
    definition_id UUID NOT NULL,
    as_of_date DATE NOT NULL,
    actual_quantity NUMERIC(20,6) NOT NULL CHECK (actual_quantity >= 0),
    recorded_at TIMESTAMPTZ NOT NULL,
    recorded_by VARCHAR(240) NOT NULL,
    idempotency_key VARCHAR(240) NOT NULL,
    source_reference VARCHAR(500),
    PRIMARY KEY (tenant_id, measurement_id),
    CONSTRAINT fk_quantity_measurement_definition
        FOREIGN KEY (tenant_id, definition_id)
        REFERENCES planning_scope_quantity_definition(tenant_id, definition_id) ON DELETE RESTRICT,
    CONSTRAINT uq_quantity_measurement_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS ix_quantity_measurement_history
    ON planning_quantity_measurement(tenant_id, definition_id, as_of_date DESC, recorded_at DESC);

CREATE OR REPLACE FUNCTION reject_planning_quantity_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Planning quantity definitions and measurements are immutable; create a new revision or correction record';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_scope_quantity_definition_immutable ON planning_scope_quantity_definition;
CREATE TRIGGER trg_scope_quantity_definition_immutable
    BEFORE UPDATE OR DELETE ON planning_scope_quantity_definition
    FOR EACH ROW EXECUTE FUNCTION reject_planning_quantity_mutation();

DROP TRIGGER IF EXISTS trg_quantity_measurement_immutable ON planning_quantity_measurement;
CREATE TRIGGER trg_quantity_measurement_immutable
    BEFORE UPDATE OR DELETE ON planning_quantity_measurement
    FOR EACH ROW EXECUTE FUNCTION reject_planning_quantity_mutation();

-- Composite uniqueness lets correction links prove that both measurements belong
-- to the same tenant and exact definition revision.
ALTER TABLE planning_quantity_measurement
    ADD CONSTRAINT uq_quantity_measurement_tenant_definition
    UNIQUE (tenant_id, definition_id, measurement_id);

CREATE TABLE IF NOT EXISTS planning_quantity_measurement_correction (
    tenant_id UUID NOT NULL,
    correction_id UUID NOT NULL,
    definition_id UUID NOT NULL,
    original_measurement_id UUID NOT NULL,
    replacement_measurement_id UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    recorded_by VARCHAR(240) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    idempotency_key VARCHAR(240) NOT NULL,
    PRIMARY KEY (tenant_id, correction_id),
    CONSTRAINT fk_quantity_correction_original
        FOREIGN KEY (tenant_id, definition_id, original_measurement_id)
        REFERENCES planning_quantity_measurement(tenant_id, definition_id, measurement_id) ON DELETE RESTRICT,
    CONSTRAINT fk_quantity_correction_replacement
        FOREIGN KEY (tenant_id, definition_id, replacement_measurement_id)
        REFERENCES planning_quantity_measurement(tenant_id, definition_id, measurement_id) ON DELETE RESTRICT,
    CONSTRAINT ck_quantity_correction_distinct_measurements
        CHECK (original_measurement_id <> replacement_measurement_id),
    CONSTRAINT uq_quantity_correction_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS ix_quantity_correction_lookup
    ON planning_quantity_measurement_correction(tenant_id, definition_id, original_measurement_id, recorded_at);

DROP TRIGGER IF EXISTS trg_quantity_measurement_correction_immutable ON planning_quantity_measurement_correction;
CREATE TRIGGER trg_quantity_measurement_correction_immutable
    BEFORE UPDATE OR DELETE ON planning_quantity_measurement_correction
    FOR EACH ROW EXECUTE FUNCTION reject_planning_quantity_mutation();

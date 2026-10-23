-- Review outcomes are immutable events. The projection uses the latest decision per correction;
-- absence of a decision means pending, and no correction is accepted by default.
CREATE TABLE IF NOT EXISTS planning_quantity_correction_decision (
    tenant_id UUID NOT NULL,
    decision_id UUID NOT NULL,
    definition_id UUID NOT NULL,
    correction_id UUID NOT NULL,
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    decided_at TIMESTAMPTZ NOT NULL,
    decided_by VARCHAR(240) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    idempotency_key VARCHAR(240) NOT NULL,
    PRIMARY KEY (tenant_id, decision_id),
    CONSTRAINT fk_quantity_decision_correction
        FOREIGN KEY (tenant_id, definition_id, correction_id)
        REFERENCES planning_quantity_measurement_correction(tenant_id, definition_id, correction_id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_quantity_decision_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS ix_quantity_decision_history
    ON planning_quantity_correction_decision(tenant_id, definition_id, correction_id, decided_at, decision_id);

DROP TRIGGER IF EXISTS trg_quantity_correction_decision_immutable ON planning_quantity_correction_decision;
CREATE TRIGGER trg_quantity_correction_decision_immutable
    BEFORE UPDATE OR DELETE ON planning_quantity_correction_decision
    FOR EACH ROW EXECUTE FUNCTION reject_planning_quantity_mutation();

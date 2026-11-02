-- Operator recovery is a privileged, auditable transition. Preserve the original
-- terminal failure on the operation row and record each recovery separately.
CREATE TABLE construction_consultancy_evidence_operation_recovery_audit (
    tenant_id VARCHAR(100) NOT NULL,
    audit_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    reason VARCHAR(2000) NOT NULL CHECK (length(btrim(reason)) > 0),
    idempotency_key VARCHAR(200) NOT NULL CHECK (length(btrim(idempotency_key)) > 0),
    previous_status VARCHAR(24) NOT NULL CHECK (previous_status = 'TERMINAL_FAILURE'),
    resulting_status VARCHAR(24) NOT NULL CHECK (resulting_status = 'PENDING'),
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, audit_id),
    CONSTRAINT uq_construction_evidence_recovery_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_construction_evidence_recovery_operation
        FOREIGN KEY (tenant_id, operation_id)
        REFERENCES construction_consultancy_evidence_operation (tenant_id, operation_id)
);

CREATE INDEX idx_construction_evidence_recovery_history
    ON construction_consultancy_evidence_operation_recovery_audit
       (tenant_id, operation_id, occurred_at, audit_id);

-- Enforce append-only audit semantics at the database boundary as well.
CREATE OR REPLACE FUNCTION reject_construction_evidence_recovery_audit_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'consultancy evidence recovery audit is append-only';
END;
$$;

CREATE TRIGGER trg_construction_evidence_recovery_audit_immutable
    BEFORE UPDATE OR DELETE ON construction_consultancy_evidence_operation_recovery_audit
    FOR EACH ROW EXECUTE FUNCTION reject_construction_evidence_recovery_audit_mutation();

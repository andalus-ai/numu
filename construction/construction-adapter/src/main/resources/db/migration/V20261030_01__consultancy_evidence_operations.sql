-- Durable, tenant-scoped evidence attachment saga operations.
-- Document writes happen outside this database transaction; this table records
-- Construction's retryable intent and a fenced lease for competing workers.
CREATE TABLE IF NOT EXISTS construction_consultancy_evidence_operation (
    tenant_id VARCHAR(100) NOT NULL CHECK (length(btrim(tenant_id)) > 0),
    operation_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL CHECK (length(btrim(idempotency_key)) > 0),
    deliverable_id UUID NOT NULL,
    document_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    relationship VARCHAR(160) NOT NULL CHECK (length(btrim(relationship)) > 0),
    requested_by UUID NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (
        status IN ('PENDING', 'LINKED', 'RETRYABLE_FAILURE', 'TERMINAL_FAILURE')
    ),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_failure VARCHAR(2000) NULL,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    worker_id VARCHAR(160) NULL,
    lease_token UUID NULL,
    locked_until TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, operation_id),
    CONSTRAINT uq_construction_consultancy_evidence_idempotency
        UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_construction_consultancy_evidence_deliverable
        FOREIGN KEY (tenant_id, deliverable_id)
        REFERENCES construction_consultancy_deliverable (tenant_id, deliverable_id),
    CONSTRAINT ck_construction_consultancy_evidence_lease
        CHECK (
            (worker_id IS NULL AND lease_token IS NULL AND locked_until IS NULL)
            OR
            (worker_id IS NOT NULL AND lease_token IS NOT NULL AND locked_until IS NOT NULL)
        ),
    CONSTRAINT ck_construction_consultancy_evidence_linked_failure
        CHECK (status <> 'LINKED' OR last_failure IS NULL)
);

CREATE INDEX IF NOT EXISTS idx_construction_consultancy_evidence_claimable
    ON construction_consultancy_evidence_operation
       (tenant_id, available_at, created_at, operation_id)
    WHERE status IN ('PENDING', 'RETRYABLE_FAILURE');

CREATE INDEX IF NOT EXISTS idx_construction_consultancy_evidence_expired_lease
    ON construction_consultancy_evidence_operation
       (tenant_id, locked_until)
    WHERE lease_token IS NOT NULL;

-- Operations are mutable workflow state, unlike the immutable evidence links
-- in Document. Prevent accidental cross-tenant reads through the primary key
-- and deliverable FK; application queries must always bind tenant_id.

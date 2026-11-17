-- Scan decisions are scoped to an immutable document version. A new version
-- starts PENDING and never inherits the previous version's clean verdict.
CREATE TABLE document_version_scan_results (
    tenant_id UUID NOT NULL,
    document_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    scan_status VARCHAR(20) NOT NULL
        CHECK (scan_status IN ('PENDING', 'CLEAN', 'INFECTED', 'FAILED')),
    scanner VARCHAR(200),
    signature_version VARCHAR(200),
    failure_code VARCHAR(200),
    scanned_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, document_id, document_version_id),
    CONSTRAINT fk_document_scan_version
        FOREIGN KEY (tenant_id, document_id, document_version_id)
        REFERENCES document_versions (tenant_id, document_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_document_scan_result_shape CHECK (
        (scan_status = 'PENDING' AND scanned_at IS NULL AND scanner IS NULL
            AND signature_version IS NULL AND failure_code IS NULL)
        OR
        (scan_status IN ('CLEAN', 'INFECTED') AND scanned_at IS NOT NULL
            AND scanner IS NOT NULL AND length(trim(scanner)) > 0 AND failure_code IS NULL)
        OR
        (scan_status = 'FAILED' AND scanned_at IS NOT NULL
            AND scanner IS NOT NULL AND length(trim(scanner)) > 0
            AND failure_code IS NOT NULL AND length(trim(failure_code)) > 0)
    )
);

INSERT INTO document_version_scan_results (
    tenant_id, document_id, document_version_id, scan_status
)
SELECT tenant_id, document_id, id, 'PENDING'
FROM document_versions;

CREATE INDEX ix_document_scan_pending
    ON document_version_scan_results (updated_at, tenant_id, document_id)
    WHERE scan_status IN ('PENDING', 'FAILED');

-- Durable queue. Workers claim rows with SKIP LOCKED and lease fencing tokens.
CREATE TABLE document_scan_jobs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    document_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'DEAD')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMPTZ,
    lease_token UUID,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_document_scan_job_version UNIQUE (tenant_id, document_id, document_version_id),
    CONSTRAINT fk_document_scan_job_result
        FOREIGN KEY (tenant_id, document_id, document_version_id)
        REFERENCES document_version_scan_results (tenant_id, document_id, document_version_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_document_scan_job_lease_pair CHECK ((lease_until IS NULL) = (lease_token IS NULL)),
    CONSTRAINT ck_document_scan_job_terminal CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    )
);

CREATE INDEX ix_document_scan_jobs_claim
    ON document_scan_jobs (next_attempt_at, created_at, id)
    WHERE status IN ('PENDING', 'PROCESSING');


-- Existing versions are also queued once. The unique key makes this safe if a
-- migration is retried by an operator after a partial deployment.
INSERT INTO document_scan_jobs (id, tenant_id, document_id, document_version_id)
SELECT gen_random_uuid(), tenant_id, document_id, document_version_id
FROM document_version_scan_results
ON CONFLICT (tenant_id, document_id, document_version_id) DO NOTHING;

-- Every future immutable version receives its own pending verdict and durable
-- scan job in the same transaction that creates the version row.
CREATE OR REPLACE FUNCTION enqueue_document_version_scan()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO document_version_scan_results (
        tenant_id, document_id, document_version_id, scan_status
    ) VALUES (
        NEW.tenant_id, NEW.document_id, NEW.id, 'PENDING'
    );

    INSERT INTO document_scan_jobs (
        id, tenant_id, document_id, document_version_id
    ) VALUES (
        gen_random_uuid(), NEW.tenant_id, NEW.document_id, NEW.id
    );

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_document_version_enqueue_scan
AFTER INSERT ON document_versions
FOR EACH ROW
EXECUTE FUNCTION enqueue_document_version_scan();

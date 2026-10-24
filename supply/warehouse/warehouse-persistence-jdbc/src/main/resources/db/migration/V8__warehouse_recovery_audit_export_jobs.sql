CREATE TABLE warehouse_recovery_audit_export_job (
    export_id VARCHAR(120) PRIMARY KEY,
    tenant_id VARCHAR(120) NOT NULL,
    requested_by VARCHAR(200) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('REQUESTED', 'RUNNING', 'VERIFYING', 'COMPLETED', 'FAILED')),
    continuation_cursor TEXT,
    exported_event_count BIGINT NOT NULL DEFAULT 0 CHECK (exported_event_count >= 0),
    exported_byte_count BIGINT NOT NULL DEFAULT 0 CHECK (exported_byte_count >= 0),
    artifact_location VARCHAR(2000),
    failure_code VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    lease_owner VARCHAR(200),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT warehouse_recovery_export_job_state_check CHECK (
      (status = 'RUNNING' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL)
      OR (status <> 'RUNNING' AND lease_owner IS NULL AND lease_until IS NULL)
    ),
    CONSTRAINT warehouse_recovery_export_job_terminal_check CHECK (
      (status = 'COMPLETED' AND artifact_location IS NOT NULL) OR status <> 'COMPLETED'
    )
);
CREATE INDEX warehouse_recovery_export_job_claim_idx
    ON warehouse_recovery_audit_export_job (status, lease_until, created_at, export_id);
CREATE INDEX warehouse_recovery_export_job_tenant_idx
    ON warehouse_recovery_audit_export_job (tenant_id, created_at DESC, export_id DESC);

CREATE TABLE warehouse_recovery_audit_export_access (
    event_id VARCHAR(120) PRIMARY KEY,
    tenant_id VARCHAR(120) NOT NULL,
    export_id VARCHAR(120) NOT NULL,
    actor_id VARCHAR(200) NOT NULL,
    action VARCHAR(24) NOT NULL CHECK (action IN ('REQUEST', 'CLAIM', 'RESUME', 'DOWNLOAD', 'VERIFY', 'VERIFY_FAILED')),
    outcome VARCHAR(24) NOT NULL CHECK (outcome IN ('ALLOWED', 'DENIED', 'SUCCESS', 'FAILURE')),
    reason_code VARCHAR(120),
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX warehouse_recovery_export_access_tenant_idx
    ON warehouse_recovery_audit_export_access (tenant_id, export_id, occurred_at DESC);

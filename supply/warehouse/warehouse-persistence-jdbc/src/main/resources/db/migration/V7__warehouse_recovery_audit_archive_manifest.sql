CREATE TABLE warehouse_recovery_audit_archive_manifest (
    export_id VARCHAR(120) PRIMARY KEY,
    tenant_id VARCHAR(120) NOT NULL,
    artifact_location VARCHAR(2000) NOT NULL,
    sha256_hex CHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL CHECK (byte_size >= 0),
    event_count BIGINT NOT NULL CHECK (event_count >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(200) NOT NULL,
    CONSTRAINT warehouse_recovery_archive_sha256_check CHECK (sha256_hex ~ '^[0-9a-f]{64}$')
);

CREATE INDEX warehouse_recovery_archive_tenant_created_idx
    ON warehouse_recovery_audit_archive_manifest (tenant_id, created_at DESC, export_id DESC);

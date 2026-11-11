CREATE TABLE feedback_attachments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    feedback_id UUID NOT NULL,
    document_id VARCHAR(128) NOT NULL,
    document_version_id VARCHAR(128) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(160) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256 CHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-fA-F]{64}$'),
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING_SCAN', 'AVAILABLE', 'REJECTED', 'DELETED')),
    rejection_reason VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT feedback_attachments_tenant_id_id_uq UNIQUE (tenant_id, id),
    CONSTRAINT feedback_attachments_feedback_fk
        FOREIGN KEY (tenant_id, feedback_id)
        REFERENCES feedback_items (tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT feedback_attachments_document_version_uq
        UNIQUE (tenant_id, feedback_id, document_id, document_version_id),
    CONSTRAINT feedback_attachments_rejection_reason_ck
        CHECK ((status = 'REJECTED' AND rejection_reason IS NOT NULL)
            OR (status <> 'REJECTED' AND rejection_reason IS NULL)),
    CONSTRAINT feedback_attachments_time_ck CHECK (updated_at >= created_at)
);

CREATE INDEX feedback_attachments_tenant_feedback_created_idx
    ON feedback_attachments (tenant_id, feedback_id, created_at, id);

CREATE INDEX feedback_attachments_pending_scan_idx
    ON feedback_attachments (tenant_id, created_at)
    WHERE status = 'PENDING_SCAN';

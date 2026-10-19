CREATE TABLE documents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    document_type VARCHAR(80) NOT NULL,
    classification VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('ACTIVE', 'PUBLISHED', 'ARCHIVED', 'DELETED')),
    filename VARCHAR(1024) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size >= 0),
    custom_attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE document_versions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    document_id UUID NOT NULL,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    sha256_hash CHAR(64) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_document_version_number UNIQUE (tenant_id, document_id, version_number),
    CONSTRAINT uq_document_version_id UNIQUE (tenant_id, document_id, id),
    CONSTRAINT fk_document_version_document FOREIGN KEY (tenant_id, document_id)
        REFERENCES documents(tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE document_upload_sessions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    document_type VARCHAR(80) NOT NULL,
    classification VARCHAR(40) NOT NULL,
    filename VARCHAR(1024) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    expected_size BIGINT NOT NULL CHECK (expected_size >= 0),
    expected_sha256 CHAR(64),
    custom_attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'EXPIRED', 'REJECTED')),
    completed_document_id UUID,
    completed_version_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_upload_session_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_upload_session_document FOREIGN KEY (tenant_id, completed_document_id)
        REFERENCES documents(tenant_id, id),
    CONSTRAINT fk_upload_session_version FOREIGN KEY (tenant_id, completed_document_id, completed_version_id)
        REFERENCES document_versions(tenant_id, document_id, id),
    CONSTRAINT ck_upload_session_completion CHECK (
        (status = 'COMPLETED' AND completed_document_id IS NOT NULL AND completed_version_id IS NOT NULL)
        OR
        (status <> 'COMPLETED' AND completed_document_id IS NULL AND completed_version_id IS NULL)
    )
);

CREATE TABLE document_links (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    document_id UUID NOT NULL,
    aggregate_type VARCHAR(150) NOT NULL,
    aggregate_id VARCHAR(150) NOT NULL,
    relationship VARCHAR(150) NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_document_link_document FOREIGN KEY (tenant_id, document_id)
        REFERENCES documents(tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE document_outbox (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    event_id UUID NOT NULL UNIQUE,
    aggregate_type VARCHAR(150) NOT NULL,
    aggregate_id VARCHAR(150) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD')),
    last_error TEXT
);

CREATE INDEX ix_documents_tenant_status ON documents (tenant_id, status);
CREATE INDEX ix_document_versions_document ON document_versions (tenant_id, document_id, version_number DESC);
CREATE INDEX ix_upload_sessions_pending ON document_upload_sessions (tenant_id, status, expires_at);
CREATE INDEX ix_document_outbox_pending ON document_outbox (status, next_attempt_at, created_at);

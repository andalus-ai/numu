CREATE TABLE content_media_asset (
 tenant_id UUID NOT NULL, media_asset_id UUID NOT NULL,
 document_id VARCHAR(128) NOT NULL, document_version_id VARCHAR(128) NOT NULL,
 file_name VARCHAR(512) NOT NULL, content_type VARCHAR(255) NOT NULL,
 media_kind VARCHAR(16) NOT NULL, byte_size BIGINT NOT NULL, sha256 CHAR(64) NOT NULL,
 width INTEGER, height INTEGER, duration_millis BIGINT,
 scan_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'QUARANTINED',
 alt_text VARCHAR(1000), caption VARCHAR(4000),
 created_by VARCHAR(128) NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY (tenant_id, media_asset_id),
 CONSTRAINT uq_content_media_document_version UNIQUE (tenant_id, document_id, document_version_id),
 CONSTRAINT ck_content_media_kind CHECK (media_kind IN ('IMAGE','VIDEO','AUDIO','DOCUMENT')),
 CONSTRAINT ck_content_media_size CHECK (byte_size >= 0),
 CONSTRAINT ck_content_media_sha256 CHECK (sha256 ~ '^[0-9a-fA-F]{64}$'),
 CONSTRAINT ck_content_media_scan CHECK (scan_status IN ('PENDING','CLEAN','INFECTED','FAILED')),
 CONSTRAINT ck_content_media_lifecycle CHECK (lifecycle_status IN ('QUARANTINED','AVAILABLE','REJECTED','ARCHIVED')),
 CONSTRAINT ck_content_media_available_clean CHECK (lifecycle_status <> 'AVAILABLE' OR scan_status = 'CLEAN'),
 CONSTRAINT ck_content_media_rejected_infected CHECK (lifecycle_status <> 'REJECTED' OR scan_status = 'INFECTED'),
 CONSTRAINT ck_content_media_dimensions CHECK ((width IS NULL OR width > 0) AND (height IS NULL OR height > 0) AND (duration_millis IS NULL OR duration_millis >= 0))
);
CREATE INDEX ix_content_media_tenant_created ON content_media_asset(tenant_id, created_at DESC);
CREATE INDEX ix_content_media_tenant_kind_status ON content_media_asset(tenant_id, media_kind, lifecycle_status);
CREATE TABLE content_media_reference (
 tenant_id UUID NOT NULL, reference_id UUID NOT NULL,
 content_id UUID NOT NULL, revision_number INTEGER NOT NULL,
 media_asset_id UUID NOT NULL, role VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY (tenant_id, reference_id),
 CONSTRAINT fk_content_media_reference_asset FOREIGN KEY (tenant_id, media_asset_id)
  REFERENCES content_media_asset(tenant_id, media_asset_id) ON DELETE RESTRICT,
 CONSTRAINT fk_content_media_reference_revision FOREIGN KEY (tenant_id, content_id, revision_number)
  REFERENCES content_revision(tenant_id, content_id, revision_number) ON DELETE RESTRICT,
 CONSTRAINT uq_content_media_reference UNIQUE (tenant_id, content_id, revision_number, media_asset_id, role),
 CONSTRAINT ck_content_media_reference_revision CHECK (revision_number >= 1)
);
CREATE INDEX ix_content_media_reference_content ON content_media_reference(tenant_id, content_id, revision_number);
CREATE INDEX ix_content_media_reference_asset ON content_media_reference(tenant_id, media_asset_id);

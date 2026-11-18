CREATE TABLE content_entry (
    tenant_id UUID NOT NULL,
    content_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    current_revision INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, content_id),
    CONSTRAINT ck_content_entry_status CHECK (status IN ('DRAFT', 'ARCHIVED')),
    CONSTRAINT ck_content_entry_revision_positive CHECK (current_revision >= 1)
);
CREATE TABLE content_revision (
    tenant_id UUID NOT NULL,
    content_id UUID NOT NULL,
    revision_number INTEGER NOT NULL,
    title VARCHAR(500) NOT NULL,
    slug VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    author_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, content_id, revision_number),
    CONSTRAINT fk_content_revision_entry FOREIGN KEY (tenant_id, content_id)
      REFERENCES content_entry (tenant_id, content_id) ON DELETE RESTRICT,
    CONSTRAINT ck_content_revision_number_positive CHECK (revision_number >= 1)
);
CREATE INDEX ix_content_entry_tenant_updated ON content_entry (tenant_id, updated_at DESC);
CREATE INDEX ix_content_revision_author ON content_revision (tenant_id, author_id, created_at DESC);
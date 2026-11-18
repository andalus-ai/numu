-- Schema identity is stored on each immutable revision, not only on the mutable entry.
-- Nullable columns preserve pre-M3 historical rows; all new application writes are schema-bound.
ALTER TABLE content_revision
    ADD COLUMN content_type_id UUID,
    ADD COLUMN schema_version INTEGER;

ALTER TABLE content_revision
    ADD CONSTRAINT ck_content_revision_schema_pair CHECK (
        (content_type_id IS NULL AND schema_version IS NULL) OR
        (content_type_id IS NOT NULL AND schema_version >= 1)
    );

ALTER TABLE content_revision
    ADD CONSTRAINT fk_content_revision_schema
    FOREIGN KEY (tenant_id, content_type_id, schema_version)
    REFERENCES content_type_schema (tenant_id, content_type_id, schema_version)
    ON DELETE RESTRICT;

CREATE INDEX ix_content_revision_schema
    ON content_revision (tenant_id, content_type_id, schema_version, content_id, revision_number);

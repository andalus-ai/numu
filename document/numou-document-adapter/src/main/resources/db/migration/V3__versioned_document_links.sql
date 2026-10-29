-- Evidence links may pin a specific immutable document version. Null preserves
-- compatibility for existing links whose version was not recorded.
ALTER TABLE document_links
    ADD COLUMN document_version_id UUID;

ALTER TABLE document_links
    ADD CONSTRAINT fk_document_link_version
    FOREIGN KEY (tenant_id, document_id, document_version_id)
    REFERENCES document_versions (tenant_id, document_id, id);

CREATE INDEX ix_document_links_aggregate
    ON document_links (tenant_id, aggregate_type, aggregate_id, linked_at, id);

CREATE INDEX ix_document_links_document_version
    ON document_links (tenant_id, document_id, document_version_id);

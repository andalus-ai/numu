-- A retry must not create a second row for the same evidence relationship.
-- Do not delete historical rows automatically: stop migration and reconcile duplicates
-- explicitly before enabling the unique indexes.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM document_links
        GROUP BY tenant_id, document_id, aggregate_type, aggregate_id, relationship
        HAVING COUNT(*) FILTER (WHERE document_version_id IS NULL) > 1
    ) THEN
        RAISE EXCEPTION 'duplicate legacy document links exist; reconcile before applying V5';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM document_links
        WHERE document_version_id IS NOT NULL
        GROUP BY tenant_id, document_id, document_version_id, aggregate_type, aggregate_id, relationship
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'duplicate version-pinned document links exist; reconcile before applying V5';
    END IF;
END;
$$;

CREATE UNIQUE INDEX uq_document_links_legacy_relationship
    ON document_links (tenant_id, document_id, aggregate_type, aggregate_id, relationship)
    WHERE document_version_id IS NULL;

CREATE UNIQUE INDEX uq_document_links_versioned_relationship
    ON document_links (tenant_id, document_id, document_version_id, aggregate_type, aggregate_id, relationship)
    WHERE document_version_id IS NOT NULL;

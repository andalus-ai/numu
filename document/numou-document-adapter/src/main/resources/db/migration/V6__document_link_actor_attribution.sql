-- Preserve the actor responsible for creating an immutable evidence relationship.
-- Existing historical links remain valid and explicitly have unknown actor attribution.
ALTER TABLE document_links
    ADD COLUMN linked_by UUID NULL;

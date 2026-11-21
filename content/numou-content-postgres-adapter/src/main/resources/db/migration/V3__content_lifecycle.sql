-- M3 expands the editorial lifecycle without rewriting content revisions.
ALTER TABLE content_entry
    DROP CONSTRAINT ck_content_entry_status;
ALTER TABLE content_entry
    ADD CONSTRAINT ck_content_entry_status
    CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'SCHEDULED', 'PUBLISHED', 'UNPUBLISHED', 'ARCHIVED'));

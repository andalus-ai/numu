-- A worker must retain the organization context that was authorized when
-- the evidence attachment was requested. Never infer it from a document UUID.
ALTER TABLE construction_consultancy_evidence_operation
    ADD COLUMN requesting_organization_id UUID;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM construction_consultancy_evidence_operation
         WHERE requesting_organization_id IS NULL
    ) THEN
        RAISE EXCEPTION 'existing consultancy evidence operations require an explicit requesting organization backfill';
    END IF;
END;
$$;

ALTER TABLE construction_consultancy_evidence_operation
    ALTER COLUMN requesting_organization_id SET NOT NULL;

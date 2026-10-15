-- Tenant-scope BOQ revision snapshots, items and revision counters.
-- Existing rows cannot be assigned a tenant safely from this source bundle: tenant ownership
-- must be backfilled from the authoritative live BOQ table before this migration is applied.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM construction_boq_revision_snapshot)
     OR EXISTS (SELECT 1 FROM construction_boq_revision_item)
     OR EXISTS (SELECT 1 FROM construction_boq_revision_counter) THEN
    RAISE EXCEPTION 'Tenant-scope migration requires empty revision tables or an operator-authored authoritative tenant backfill';
  END IF;
END $$;

ALTER TABLE construction_boq_revision_item DROP CONSTRAINT IF EXISTS fk_boq_revision_item_header;
ALTER TABLE construction_boq_revision_snapshot ADD COLUMN tenant_id TEXT;
ALTER TABLE construction_boq_revision_item ADD COLUMN tenant_id TEXT;
ALTER TABLE construction_boq_revision_counter ADD COLUMN tenant_id TEXT;
ALTER TABLE construction_boq_revision_snapshot ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE construction_boq_revision_item ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE construction_boq_revision_counter ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE construction_boq_revision_snapshot DROP CONSTRAINT construction_boq_revision_snapshot_pkey;
ALTER TABLE construction_boq_revision_item DROP CONSTRAINT construction_boq_revision_item_pkey;
ALTER TABLE construction_boq_revision_counter DROP CONSTRAINT construction_boq_revision_counter_pkey;
ALTER TABLE construction_boq_revision_snapshot ADD CONSTRAINT construction_boq_revision_snapshot_pkey PRIMARY KEY (tenant_id, boq_id, revision_number);
ALTER TABLE construction_boq_revision_item ADD CONSTRAINT construction_boq_revision_item_pkey PRIMARY KEY (tenant_id, boq_id, revision_number, item_id);
ALTER TABLE construction_boq_revision_counter ADD CONSTRAINT construction_boq_revision_counter_pkey PRIMARY KEY (tenant_id, boq_id);
ALTER TABLE construction_boq_revision_item ADD CONSTRAINT fk_boq_revision_item_header FOREIGN KEY (tenant_id, boq_id, revision_number) REFERENCES construction_boq_revision_snapshot (tenant_id, boq_id, revision_number) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_boq_revision_tenant_project ON construction_boq_revision_snapshot (tenant_id, project_id, boq_id, revision_number DESC);

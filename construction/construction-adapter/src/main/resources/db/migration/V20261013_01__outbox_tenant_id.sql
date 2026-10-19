-- Adds tenant scope to outbox records for tenant-aware relay filtering.
-- Existing rows remain NULL until operators backfill them from authoritative aggregate ownership.
ALTER TABLE construction_outbox_event
    ADD COLUMN IF NOT EXISTS tenant_id TEXT NULL;

CREATE INDEX IF NOT EXISTS idx_construction_outbox_tenant_pending
    ON construction_outbox_event (tenant_id, available_at, occurred_at)
    WHERE status IN ('PENDING', 'FAILED');

-- Before enforcing NOT NULL, backfill all legacy rows using the authoritative tenant-to-aggregate mapping.
-- Do not assign a guessed/default tenant to historical events.

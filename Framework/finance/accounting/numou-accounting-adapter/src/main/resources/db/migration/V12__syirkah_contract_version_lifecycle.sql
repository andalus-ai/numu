SET search_path TO accounting, public;

ALTER TABLE syirkah_contract_version
    ADD COLUMN IF NOT EXISTS version_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (version_status IN ('DRAFT','ACTIVE','SUPERSEDED','REJECTED')),
    ADD COLUMN IF NOT EXISTS activated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS activated_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS superseded_at TIMESTAMP WITH TIME ZONE;

-- Backfill the version currently active for existing contracts. Older versions with
-- signed evidence are historical/superseded; unsigned historical drafts stay DRAFT.
UPDATE syirkah_contract_version v
SET version_status = CASE
    WHEN c.lifecycle_status = 'ACTIVE' AND v.version = c.current_version THEN 'ACTIVE'
    WHEN v.signed_evidence_reference IS NOT NULL THEN 'SUPERSEDED'
    ELSE 'DRAFT'
END,
activated_at = CASE
    WHEN c.lifecycle_status = 'ACTIVE' AND v.version = c.current_version THEN v.created_at
    WHEN v.signed_evidence_reference IS NOT NULL THEN v.created_at
    ELSE NULL
END,
activated_by = CASE
    WHEN c.lifecycle_status = 'ACTIVE' AND v.version = c.current_version THEN v.created_by
    WHEN v.signed_evidence_reference IS NOT NULL THEN v.created_by
    ELSE NULL
END
FROM syirkah_contract c
WHERE c.tenant_id = v.tenant_id
  AND c.company_id = v.company_id
  AND c.contract_id = v.contract_id;

CREATE INDEX IF NOT EXISTS idx_syirkah_contract_version_status
    ON syirkah_contract_version (tenant_id, company_id, contract_id, version_status);

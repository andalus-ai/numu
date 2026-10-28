-- The akad formation record a syirkah was opened under (Hukum Muamalah Bab X, XI, XVIII): party capacity data,
-- lawfulness review, written sections, witnesses, and the policy the checks ran under. Written once, in the
-- transaction that opens the syirkah; never changed. Tenant-scoped with fail-closed RLS like V20261023_01.

CREATE TABLE IF NOT EXISTS syirkah_ledger_akad_formation (
    syirkah_id  uuid        PRIMARY KEY REFERENCES syirkah_ledger (syirkah_id),
    tenant_id   uuid        NOT NULL,
    formation   jsonb       NOT NULL,
    policy      jsonb       NOT NULL,
    recorded_at timestamptz NOT NULL
);

DROP TRIGGER IF EXISTS syirkah_akad_formation_immutable ON syirkah_ledger_akad_formation;
CREATE TRIGGER syirkah_akad_formation_immutable BEFORE UPDATE OR DELETE ON syirkah_ledger_akad_formation
    FOR EACH ROW EXECUTE FUNCTION syirkah_append_only();

ALTER TABLE syirkah_ledger_akad_formation ENABLE ROW LEVEL SECURITY;
ALTER TABLE syirkah_ledger_akad_formation FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS syirkah_ledger_akad_formation_tenant ON syirkah_ledger_akad_formation;
CREATE POLICY syirkah_ledger_akad_formation_tenant ON syirkah_ledger_akad_formation
    USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

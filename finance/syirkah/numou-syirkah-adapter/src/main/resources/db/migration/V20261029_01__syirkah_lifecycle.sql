-- Records behind each lifecycle change of a syirkah (Hukum Muamalah Bab X, XVI, XXIV, XXV): every partner's
-- consent with evidence (dissent kept), profit decision, hak syuf'ah answers, heirs' decisions. One row per akad
-- version the change produced, written in the transaction of the revision; never changed. Tenant-scoped with
-- fail-closed RLS like V20261023_01.

CREATE TABLE IF NOT EXISTS syirkah_ledger_lifecycle (
    syirkah_id       uuid        NOT NULL REFERENCES syirkah_ledger (syirkah_id),
    contract_version integer     NOT NULL,
    tenant_id        uuid        NOT NULL,
    kind             text        NOT NULL,
    change           jsonb       NOT NULL,
    recorded_at      timestamptz NOT NULL,
    PRIMARY KEY (syirkah_id, contract_version)
);

DROP TRIGGER IF EXISTS syirkah_lifecycle_immutable ON syirkah_ledger_lifecycle;
CREATE TRIGGER syirkah_lifecycle_immutable BEFORE UPDATE OR DELETE ON syirkah_ledger_lifecycle
    FOR EACH ROW EXECUTE FUNCTION syirkah_append_only();

ALTER TABLE syirkah_ledger_lifecycle ENABLE ROW LEVEL SECURITY;
ALTER TABLE syirkah_ledger_lifecycle FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS syirkah_ledger_lifecycle_tenant ON syirkah_ledger_lifecycle;
CREATE POLICY syirkah_ledger_lifecycle_tenant ON syirkah_ledger_lifecycle
    USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

-- Staff (ijarah) contracts engaged by a syirkah (Hukum Muamalah Bab XVII). Only contracts that passed the staff
-- check are written, each tied to the akad version it was checked against; append-only. Tenant-scoped with
-- fail-closed RLS like V20261023_01.

CREATE TABLE IF NOT EXISTS syirkah_ledger_staff (
    syirkah_id       uuid        NOT NULL REFERENCES syirkah_ledger (syirkah_id),
    sequence_no      integer     NOT NULL,
    tenant_id        uuid        NOT NULL,
    contract_version integer     NOT NULL,
    staff            jsonb       NOT NULL,
    recorded_at      timestamptz NOT NULL,
    PRIMARY KEY (syirkah_id, sequence_no)
);

DROP TRIGGER IF EXISTS syirkah_staff_immutable ON syirkah_ledger_staff;
CREATE TRIGGER syirkah_staff_immutable BEFORE UPDATE OR DELETE ON syirkah_ledger_staff
    FOR EACH ROW EXECUTE FUNCTION syirkah_append_only();

ALTER TABLE syirkah_ledger_staff ENABLE ROW LEVEL SECURITY;
ALTER TABLE syirkah_ledger_staff FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS syirkah_ledger_staff_tenant ON syirkah_ledger_staff;
CREATE POLICY syirkah_ledger_staff_tenant ON syirkah_ledger_staff
    USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

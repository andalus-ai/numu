-- Trading figures (Hukum Muamalah Bab XXII) a close was worked out from: sales, returns, discounts, opening and
-- counted closing stock, purchases, costs. One row per close, append-only, tenant-scoped with fail-closed RLS.

CREATE TABLE IF NOT EXISTS syirkah_ledger_trading (
    syirkah_id  uuid        NOT NULL REFERENCES syirkah_ledger (syirkah_id),
    close_id    uuid        NOT NULL,
    tenant_id   uuid        NOT NULL,
    figures     jsonb       NOT NULL,
    recorded_at timestamptz NOT NULL,
    PRIMARY KEY (syirkah_id, close_id)
);

DROP TRIGGER IF EXISTS syirkah_trading_immutable ON syirkah_ledger_trading;
CREATE TRIGGER syirkah_trading_immutable BEFORE UPDATE OR DELETE ON syirkah_ledger_trading
    FOR EACH ROW EXECUTE FUNCTION syirkah_append_only();

ALTER TABLE syirkah_ledger_trading ENABLE ROW LEVEL SECURITY;
ALTER TABLE syirkah_ledger_trading FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS syirkah_ledger_trading_tenant ON syirkah_ledger_trading;
CREATE POLICY syirkah_ledger_trading_tenant ON syirkah_ledger_trading
    USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

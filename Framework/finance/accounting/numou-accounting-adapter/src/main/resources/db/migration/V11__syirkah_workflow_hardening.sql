-- Auditable Syirkah lifecycle and settlement ledger.
SET search_path TO accounting, public;

ALTER TABLE syirkah_distribution_obligation
    ADD COLUMN IF NOT EXISTS allocation_basis VARCHAR(96) NOT NULL DEFAULT 'LEGACY_UNSPECIFIED';

ALTER TABLE syirkah_distribution_obligation
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(128) NOT NULL DEFAULT 'migration';

CREATE TABLE IF NOT EXISTS syirkah_distribution_settlement (
    tenant_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    result_id VARCHAR(128) NOT NULL,
    calculation_version INTEGER NOT NULL,
    party_id VARCHAR(128) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    amount NUMERIC(24, 6) NOT NULL CHECK (amount > 0),
    actor VARCHAR(128) NOT NULL,
    settled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, company_id, idempotency_key),
    FOREIGN KEY (tenant_id, company_id, result_id, calculation_version, party_id)
        REFERENCES syirkah_distribution_obligation
            (tenant_id, company_id, result_id, calculation_version, party_id)
);

CREATE INDEX IF NOT EXISTS idx_syirkah_settlement_party
    ON syirkah_distribution_settlement
        (tenant_id, company_id, result_id, calculation_version, party_id, settled_at);

CREATE TABLE IF NOT EXISTS syirkah_audit_event (
    event_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(48) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version INTEGER,
    event_type VARCHAR(64) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(128),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_syirkah_audit_aggregate
    ON syirkah_audit_event (tenant_id, company_id, aggregate_type, aggregate_id, occurred_at);

-- Exact duplicate business periods are almost always a double-counting error.
CREATE UNIQUE INDEX IF NOT EXISTS uq_syirkah_business_result_period
    ON syirkah_business_result (tenant_id, company_id, contract_id, period_start, period_end);

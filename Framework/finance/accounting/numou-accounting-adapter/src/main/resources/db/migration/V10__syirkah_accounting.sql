-- Syirkah accounting persistence foundation.
-- Calculations are immutable snapshots. This migration intentionally does not
-- auto-post journal entries; posting must use the existing ledger transaction boundary.
SET search_path TO accounting, public;

CREATE TABLE IF NOT EXISTS syirkah_contract (
    tenant_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    contract_id VARCHAR(128) NOT NULL,
    current_version INTEGER NOT NULL CHECK (current_version >= 1),
    lifecycle_status VARCHAR(24) NOT NULL CHECK (lifecycle_status IN ('DRAFT','REVIEW','SIGNED','ACTIVE','SUSPENDED','TERMINATED','CLOSED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(128) NOT NULL,
    PRIMARY KEY (tenant_id, company_id, contract_id)
);

CREATE TABLE IF NOT EXISTS syirkah_contract_version (
    tenant_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    contract_id VARCHAR(128) NOT NULL,
    version INTEGER NOT NULL CHECK (version >= 1),
    currency_code CHAR(3) NOT NULL,
    syirkah_type VARCHAR(24) NOT NULL CHECK (syirkah_type IN ('MUDHARABAH','MUSYARAKAH','INAN','ABDAN','WUJUH')),
    effective_from DATE NOT NULL,
    effective_until DATE NOT NULL,
    sharia_policy_version VARCHAR(128) NOT NULL,
    terms_hash CHAR(64) NOT NULL,
    signed_evidence_reference VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(128) NOT NULL,
    PRIMARY KEY (tenant_id, company_id, contract_id, version),
    FOREIGN KEY (tenant_id, company_id, contract_id)
        REFERENCES syirkah_contract (tenant_id, company_id, contract_id),
    CHECK (effective_until >= effective_from)
);

CREATE TABLE IF NOT EXISTS syirkah_contract_party (
    tenant_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    contract_id VARCHAR(128) NOT NULL,
    contract_version INTEGER NOT NULL,
    party_id VARCHAR(128) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    party_role VARCHAR(40) NOT NULL CHECK (party_role IN ('CAPITAL_PROVIDER','MANAGING_PARTNER','CAPITAL_AND_MANAGING_PARTNER','WORKING_PARTNER')),
    capital_contribution NUMERIC(24, 6) NOT NULL CHECK (capital_contribution >= 0),
    profit_nisbah_percent NUMERIC(7, 4) NOT NULL CHECK (profit_nisbah_percent >= 0 AND profit_nisbah_percent <= 100),
    PRIMARY KEY (tenant_id, company_id, contract_id, contract_version, party_id),
    FOREIGN KEY (tenant_id, company_id, contract_id, contract_version)
        REFERENCES syirkah_contract_version (tenant_id, company_id, contract_id, version)
);

CREATE TABLE IF NOT EXISTS syirkah_business_result (
    tenant_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    result_id VARCHAR(128) NOT NULL,
    contract_id VARCHAR(128) NOT NULL,
    contract_version INTEGER NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    currency_code CHAR(3) NOT NULL,
    total_revenue NUMERIC(24, 6) NOT NULL CHECK (total_revenue >= 0),
    direct_costs NUMERIC(24, 6) NOT NULL CHECK (direct_costs >= 0),
    operating_costs NUMERIC(24, 6) NOT NULL CHECK (operating_costs >= 0),
    other_costs NUMERIC(24, 6) NOT NULL CHECK (other_costs >= 0),
    evidence_reference VARCHAR(512) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(128) NOT NULL,
    PRIMARY KEY (tenant_id, company_id, result_id),
    FOREIGN KEY (tenant_id, company_id, contract_id, contract_version)
        REFERENCES syirkah_contract_version (tenant_id, company_id, contract_id, version),
    CHECK (period_end >= period_start)
);

CREATE TABLE IF NOT EXISTS syirkah_calculation_snapshot (
    tenant_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    result_id VARCHAR(128) NOT NULL,
    calculation_version INTEGER NOT NULL CHECK (calculation_version >= 1),
    contract_id VARCHAR(128) NOT NULL,
    contract_version INTEGER NOT NULL,
    policy_version VARCHAR(128) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    net_result NUMERIC(24, 6) NOT NULL,
    calculation_hash CHAR(64) NOT NULL,
    calculation_payload JSONB NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('CALCULATED','IN_REVIEW','APPROVED','POSTED','REJECTED')),
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    calculated_by VARCHAR(128) NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE,
    approved_by VARCHAR(128),
    posted_journal_id VARCHAR(128),
    PRIMARY KEY (tenant_id, company_id, result_id, calculation_version),
    FOREIGN KEY (tenant_id, company_id, result_id)
        REFERENCES syirkah_business_result (tenant_id, company_id, result_id),
    UNIQUE (tenant_id, company_id, result_id, calculation_hash),
    CHECK ((status NOT IN ('APPROVED','POSTED')) OR (approved_at IS NOT NULL AND approved_by IS NOT NULL)),
    CHECK ((status <> 'POSTED') OR posted_journal_id IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS syirkah_distribution_obligation (
    tenant_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    result_id VARCHAR(128) NOT NULL,
    calculation_version INTEGER NOT NULL,
    party_id VARCHAR(128) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    amount NUMERIC(24, 6) NOT NULL CHECK (amount >= 0),
    settled_amount NUMERIC(24, 6) NOT NULL DEFAULT 0 CHECK (settled_amount >= 0),
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING','PARTIALLY_SETTLED','SETTLED','CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, company_id, result_id, calculation_version, party_id),
    FOREIGN KEY (tenant_id, company_id, result_id, calculation_version)
        REFERENCES syirkah_calculation_snapshot (tenant_id, company_id, result_id, calculation_version),
    CHECK (settled_amount <= amount),
    CHECK ((status <> 'SETTLED') OR (settled_amount = amount))
);

CREATE TABLE IF NOT EXISTS syirkah_command_idempotency (
    tenant_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_type VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    result_reference VARCHAR(256),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, company_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_syirkah_result_contract_period
    ON syirkah_business_result (tenant_id, company_id, contract_id, period_start, period_end);
CREATE INDEX IF NOT EXISTS idx_syirkah_snapshot_status
    ON syirkah_calculation_snapshot (tenant_id, company_id, status, calculated_at);

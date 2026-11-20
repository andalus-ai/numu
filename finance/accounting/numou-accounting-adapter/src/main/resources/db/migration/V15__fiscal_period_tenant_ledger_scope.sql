-- V15: tenant/ledger-scoped fiscal periods with database-enforced non-overlap.
CREATE SCHEMA IF NOT EXISTS accounting;

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS accounting.fiscal_period (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    ledger_id VARCHAR(64) NOT NULL,
    period_number INTEGER NOT NULL CHECK (period_number > 0),
    period_name VARCHAR(160) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'CLOSED', 'LOCKED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_fiscal_period_date_range CHECK (end_date >= start_date),
    CONSTRAINT uq_fiscal_period_scope_id UNIQUE (tenant_id, ledger_id, id),
    CONSTRAINT ex_fiscal_period_no_overlap
        EXCLUDE USING gist (
            tenant_id WITH =,
            ledger_id WITH =,
            daterange(start_date, end_date, '[]') WITH &&
        )
);

CREATE INDEX IF NOT EXISTS idx_fiscal_period_scope_dates
    ON accounting.fiscal_period (tenant_id, ledger_id, start_date, end_date);

CREATE INDEX IF NOT EXISTS idx_fiscal_period_scope_status
    ON accounting.fiscal_period (tenant_id, ledger_id, status);

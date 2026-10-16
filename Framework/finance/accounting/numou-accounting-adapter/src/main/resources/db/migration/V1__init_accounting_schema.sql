-- V1__init_accounting_schema.sql
CREATE SCHEMA IF NOT EXISTS accounting;
SET search_path TO accounting, public;

CREATE TABLE IF NOT EXISTS ledger (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    ledger_type VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ledger_tenant ON ledger (tenant_id, ledger_type);

CREATE TABLE IF NOT EXISTS chart_of_accounts (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    account_code VARCHAR(64) NOT NULL,
    account_name VARCHAR(255) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    parent_account_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_account_tenant_code UNIQUE (tenant_id, account_code)
);

CREATE INDEX IF NOT EXISTS idx_coa_tenant ON chart_of_accounts (tenant_id, status);

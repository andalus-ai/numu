-- V4__trial_balance_projections.sql
SET search_path TO accounting, public;

CREATE TABLE IF NOT EXISTS trial_balance_projection (
    tenant_id VARCHAR(64) NOT NULL,
    ledger_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    total_debit NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_credit NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, ledger_id, account_id)
);

CREATE INDEX IF NOT EXISTS idx_tb_scope ON trial_balance_projection (tenant_id, ledger_id);

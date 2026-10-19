-- V2__journal_entry_tables.sql
SET search_path TO accounting, public;

CREATE TABLE IF NOT EXISTS journal_entry (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    ledger_id VARCHAR(64) NOT NULL,
    entry_number VARCHAR(64) NOT NULL,
    entry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    description VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    sharia_contract_type VARCHAR(64) DEFAULT 'NONE',
    created_by VARCHAR(64),
    submitted_by VARCHAR(64),
    approved_by VARCHAR(64),
    posted_by VARCHAR(64),
    reversal_of_entry_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_entry_tenant_number UNIQUE (tenant_id, ledger_id, entry_number)
);

CREATE INDEX IF NOT EXISTS idx_journal_scope ON journal_entry (tenant_id, ledger_id, status);
CREATE INDEX IF NOT EXISTS idx_journal_date ON journal_entry (entry_date);

CREATE TABLE IF NOT EXISTS journal_line (
    id BIGSERIAL PRIMARY KEY,
    journal_id VARCHAR(64) NOT NULL REFERENCES journal_entry(id) ON DELETE CASCADE,
    account_id VARCHAR(64) NOT NULL,
    debit_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    credit_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(255),
    sharia_contract VARCHAR(64),
    dimensions JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_line_journal ON journal_line (journal_id);
CREATE INDEX IF NOT EXISTS idx_line_account ON journal_line (account_id);

-- V6__accounts_receivable.sql
SET search_path TO accounting, public;

CREATE TABLE IF NOT EXISTS ar_customer_invoice (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    ledger_id VARCHAR(64) NOT NULL,
    period_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(64) NOT NULL,
    customer_ref VARCHAR(128) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_net NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_tax NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_gross NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ar_invoice_scope ON ar_customer_invoice (tenant_id, company_id, ledger_id, period_id);
CREATE INDEX IF NOT EXISTS idx_ar_invoice_customer ON ar_customer_invoice (tenant_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_ar_invoice_status ON ar_customer_invoice (tenant_id, status);

CREATE TABLE IF NOT EXISTS ar_customer_invoice_line (
    id BIGSERIAL PRIMARY KEY,
    invoice_id VARCHAR(64) NOT NULL REFERENCES ar_customer_invoice(id) ON DELETE CASCADE,
    account_code VARCHAR(64) NOT NULL,
    description VARCHAR(512) NOT NULL,
    net_amount NUMERIC(19, 4) NOT NULL,
    tax_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    dimensions JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ar_line_invoice ON ar_customer_invoice_line (invoice_id);

CREATE TABLE IF NOT EXISTS ar_customer_payment (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    ledger_id VARCHAR(64) NOT NULL,
    period_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(64) NOT NULL,
    invoice_id VARCHAR(64),
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    cash_account VARCHAR(64) NOT NULL,
    ar_account VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ar_payment_customer ON ar_customer_payment (tenant_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_ar_payment_status ON ar_customer_payment (tenant_id, status);

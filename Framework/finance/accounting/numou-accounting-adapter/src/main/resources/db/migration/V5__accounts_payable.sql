-- V5__accounts_payable.sql
SET search_path TO accounting, public;

CREATE TABLE IF NOT EXISTS ap_vendor_invoice (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    ledger_id VARCHAR(64) NOT NULL,
    period_id VARCHAR(64) NOT NULL,
    vendor_id VARCHAR(64) NOT NULL,
    vendor_ref VARCHAR(128) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_net NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_tax NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_gross NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ap_invoice_scope ON ap_vendor_invoice (tenant_id, company_id, ledger_id, period_id);
CREATE INDEX IF NOT EXISTS idx_ap_invoice_vendor ON ap_vendor_invoice (tenant_id, vendor_id);
CREATE INDEX IF NOT EXISTS idx_ap_invoice_status ON ap_vendor_invoice (tenant_id, status);

CREATE TABLE IF NOT EXISTS ap_vendor_invoice_line (
    id BIGSERIAL PRIMARY KEY,
    invoice_id VARCHAR(64) NOT NULL REFERENCES ap_vendor_invoice(id) ON DELETE CASCADE,
    account_code VARCHAR(64) NOT NULL,
    description VARCHAR(512) NOT NULL,
    net_amount NUMERIC(19, 4) NOT NULL,
    tax_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    dimensions JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ap_line_invoice ON ap_vendor_invoice_line (invoice_id);

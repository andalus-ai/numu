SET search_path TO accounting, public;

CREATE TABLE IF NOT EXISTS ar_receivable_invoice (
    id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    customer_ref VARCHAR(128) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    outstanding_balance NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ar_receivable_invoice_line (
    invoice_id VARCHAR(64) NOT NULL REFERENCES ar_receivable_invoice(id) ON DELETE CASCADE,
    line_no INTEGER NOT NULL,
    account_code VARCHAR(64) NOT NULL,
    description VARCHAR(512) NOT NULL,
    net_amount NUMERIC(19, 4) NOT NULL,
    tax_amount NUMERIC(19, 4) NOT NULL,
    PRIMARY KEY (invoice_id, line_no)
);

CREATE TABLE IF NOT EXISTS ar_receivable_payment (
    id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL
);

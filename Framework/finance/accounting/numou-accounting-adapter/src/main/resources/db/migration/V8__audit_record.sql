-- V8__audit_record.sql
SET search_path TO accounting, public;

CREATE TABLE IF NOT EXISTS audit_record (
    id VARCHAR(64) PRIMARY KEY,
    action VARCHAR(64) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64),
    correlation_id VARCHAR(128),
    request_id VARCHAR(128),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    target VARCHAR(256) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    details TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_record_target ON audit_record (target);
CREATE INDEX IF NOT EXISTS idx_audit_record_tenant_time ON audit_record (tenant_id, occurred_at);

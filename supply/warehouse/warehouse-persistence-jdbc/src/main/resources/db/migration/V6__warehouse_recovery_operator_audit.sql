-- Append-only audit trail for explicit operator actions on warehouse recovery records.
CREATE TABLE IF NOT EXISTS warehouse_recovery_operator_audit (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    command_key VARCHAR(255) NOT NULL,
    action VARCHAR(64) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    prior_version BIGINT NOT NULL CHECK (prior_version > 0),
    resulting_version BIGINT NOT NULL CHECK (resulting_version = prior_version + 1),
    correlation_id VARCHAR(200) NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_warehouse_recovery_operator_audit_tenant_time
    ON warehouse_recovery_operator_audit (tenant_id, occurred_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS ix_warehouse_recovery_operator_audit_command
    ON warehouse_recovery_operator_audit (tenant_id, command_key, occurred_at DESC, id DESC);

-- The audit table intentionally has no update/delete API. Database permissions should
-- grant the application role INSERT and SELECT, but not UPDATE or DELETE, where feasible.

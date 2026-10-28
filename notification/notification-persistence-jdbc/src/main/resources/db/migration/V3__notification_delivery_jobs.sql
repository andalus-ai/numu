CREATE TABLE notification_delivery_job (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    notification_id UUID NOT NULL REFERENCES notification_inbox(id),
    device_id UUID NOT NULL REFERENCES notification_push_device(id),
    provider VARCHAR(40) NOT NULL CHECK (length(btrim(provider)) > 0),
    idempotency_key VARCHAR(240) NOT NULL CHECK (length(btrim(idempotency_key)) > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'IN_FLIGHT', 'RETRY_WAIT', 'DELIVERED', 'DEAD')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_token UUID NULL,
    lease_until TIMESTAMPTZ NULL,
    delivered_at TIMESTAMPTZ NULL,
    last_error_code VARCHAR(120) NOT NULL DEFAULT '',
    CONSTRAINT notification_delivery_job_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT notification_delivery_job_lease_state CHECK (
        (status = 'IN_FLIGHT' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'IN_FLIGHT' AND lease_token IS NULL AND lease_until IS NULL)
    ),
    CONSTRAINT notification_delivery_job_delivery_state CHECK (
        (status = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR (status <> 'DELIVERED' AND delivered_at IS NULL)
    ),
    CONSTRAINT notification_delivery_job_times CHECK (
        next_attempt_at >= created_at
        AND (lease_until IS NULL OR lease_until > created_at)
        AND (delivered_at IS NULL OR delivered_at >= created_at)
    )
);

CREATE INDEX notification_delivery_job_due
    ON notification_delivery_job (next_attempt_at, created_at, id)
    WHERE status IN ('PENDING', 'RETRY_WAIT');

CREATE INDEX notification_delivery_job_expired_lease
    ON notification_delivery_job (lease_until, created_at, id)
    WHERE status = 'IN_FLIGHT';

CREATE INDEX notification_delivery_job_tenant_status
    ON notification_delivery_job (tenant_id, status, created_at DESC);

CREATE TABLE notification_inbox (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    notification_type VARCHAR(120) NOT NULL CHECK (length(btrim(notification_type)) > 0),
    title VARCHAR(200) NOT NULL CHECK (length(btrim(title)) > 0),
    body VARCHAR(4000) NOT NULL CHECK (length(btrim(body)) > 0),
    source_reference VARCHAR(500) NOT NULL DEFAULT '',
    idempotency_key VARCHAR(200) NOT NULL CHECK (length(btrim(idempotency_key)) > 0),
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ NULL,
    CONSTRAINT notification_inbox_read_after_create
        CHECK (read_at IS NULL OR read_at >= created_at),
    CONSTRAINT notification_inbox_recipient_idempotency
        UNIQUE (tenant_id, recipient_id, idempotency_key)
);

CREATE INDEX notification_inbox_unread_by_recipient
    ON notification_inbox (tenant_id, recipient_id, created_at DESC, id DESC)
    WHERE read_at IS NULL;

CREATE INDEX notification_inbox_by_recipient
    ON notification_inbox (tenant_id, recipient_id, created_at DESC, id DESC);
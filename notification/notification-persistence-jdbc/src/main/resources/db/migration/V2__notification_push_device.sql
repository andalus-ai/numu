CREATE TABLE notification_push_device (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    provider_token TEXT NOT NULL CHECK (length(btrim(provider_token)) > 0),
    token_fingerprint CHAR(64) NOT NULL UNIQUE,
    platform VARCHAR(16) NOT NULL CHECK (platform IN ('ANDROID', 'IOS', 'WEB')),
    app_instance VARCHAR(200) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    disabled_at TIMESTAMPTZ NULL,
    CONSTRAINT notification_push_device_time_order
        CHECK (updated_at >= created_at AND (disabled_at IS NULL OR disabled_at >= created_at))
);

CREATE INDEX notification_push_device_active_recipient
    ON notification_push_device (tenant_id, recipient_id, updated_at DESC, id DESC)
    WHERE disabled_at IS NULL;

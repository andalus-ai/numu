CREATE TABLE IF NOT EXISTS numou_omnichannel_inbound_event (
    tenant_id              VARCHAR(100) NOT NULL,
    provider_id            VARCHAR(100) NOT NULL,
    provider_account_id    VARCHAR(200) NOT NULL,
    external_event_id      VARCHAR(250) NOT NULL,
    event_type             VARCHAR(200) NOT NULL,
    payload_sha256         CHAR(64) NOT NULL,
    channel_type           VARCHAR(40) NOT NULL,
    branch_id              VARCHAR(200),
    correlation_id         VARCHAR(200) NOT NULL,
    received_at            TIMESTAMPTZ NOT NULL,
    provider_occurred_at   TIMESTAMPTZ,
    state                  VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_numou_omnichannel_inbound_event PRIMARY KEY (
        tenant_id, provider_id, provider_account_id, external_event_id
    ),
    CONSTRAINT ck_numou_omnichannel_inbound_event_digest
        CHECK (payload_sha256 ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT ck_numou_omnichannel_inbound_event_state
        CHECK (state IN (
            'RECEIVED', 'VERIFIED', 'REJECTED', 'PROCESSING',
            'PROCESSED', 'RETRYABLE_FAILURE', 'DEAD_LETTER'
        ))
);

CREATE INDEX IF NOT EXISTS ix_numou_omnichannel_inbound_event_state_received
    ON numou_omnichannel_inbound_event (state, received_at);

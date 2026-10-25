CREATE TABLE IF NOT EXISTS numou_omnichannel_webhook_replay (
    provider_id VARCHAR(100) NOT NULL,
    provider_account_id VARCHAR(200) NOT NULL,
    replay_key_sha256 CHAR(64) NOT NULL,
    reserved_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_numou_omnichannel_webhook_replay
        PRIMARY KEY (provider_id, provider_account_id, replay_key_sha256),
    CONSTRAINT ck_numou_omnichannel_webhook_replay_expiry
        CHECK (expires_at > reserved_at)
);

CREATE INDEX IF NOT EXISTS ix_numou_omnichannel_webhook_replay_expiry
    ON numou_omnichannel_webhook_replay (expires_at);

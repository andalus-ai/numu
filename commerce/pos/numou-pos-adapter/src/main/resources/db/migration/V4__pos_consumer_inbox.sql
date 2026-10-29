-- One durable receipt per logical consumer and tenant-scoped event.
CREATE TABLE IF NOT EXISTS pos_consumer_inbox (
    tenant_id UUID NOT NULL,
    consumer_name VARCHAR(160) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, consumer_name, event_id),
    CHECK (length(trim(consumer_name)) > 0)
);

CREATE INDEX IF NOT EXISTS ix_pos_consumer_inbox_processed_at
    ON pos_consumer_inbox (processed_at);

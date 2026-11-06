-- One row per billed cycle of a schedule, so a loaded schedule knows what was billed and what failed
-- (and a failed payment can be retried). Matches BillingCycleEntity; BillingSchemaTest checks it.

CREATE TABLE billing_cycles (
    id             UUID PRIMARY KEY,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    version        BIGINT,
    schedule_id    UUID           NOT NULL REFERENCES billing_schedules (id) ON DELETE CASCADE,
    cycle_number   INTEGER        NOT NULL CHECK (cycle_number >= 1),
    billing_date   TIMESTAMPTZ    NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL CHECK (amount >= 0),
    currency       VARCHAR(3)     NOT NULL,
    invoice_id     VARCHAR(255),
    status         VARCHAR(20)    NOT NULL,
    transaction_id VARCHAR(255),
    error_message  TEXT,
    processed_at   TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    UNIQUE (schedule_id, cycle_number)
);

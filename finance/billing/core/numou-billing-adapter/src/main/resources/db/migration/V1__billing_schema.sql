-- Billing schedules and usage records. The columns match BillingScheduleEntity and UsageRecordEntity
-- (including the BaseEntity columns); BillingSchemaTest checks that they stay in step.

CREATE TABLE billing_schedules (
    id                       UUID PRIMARY KEY,
    active                   BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ,
    version                  BIGINT,
    subscription_id          UUID           NOT NULL,
    customer_id              VARCHAR(255)   NOT NULL,
    customer_email           VARCHAR(255),
    frequency                VARCHAR(20)    NOT NULL,
    status                   VARCHAR(20)    NOT NULL,
    start_date               TIMESTAMPTZ    NOT NULL,
    end_date                 TIMESTAMPTZ,
    next_billing_date        TIMESTAMPTZ,
    last_billing_date        TIMESTAMPTZ,
    amount                   NUMERIC(19, 2) NOT NULL CHECK (amount >= 0),
    currency                 VARCHAR(3)     NOT NULL,
    payment_method_token     VARCHAR(255),
    current_cycle            INTEGER        NOT NULL DEFAULT 0 CHECK (current_cycle >= 0),
    total_cycles             INTEGER        NOT NULL DEFAULT 0 CHECK (total_cycles >= 0),
    failed_payment_count     INTEGER        NOT NULL DEFAULT 0 CHECK (failed_payment_count >= 0),
    max_failed_payments      INTEGER        NOT NULL DEFAULT 3 CHECK (max_failed_payments >= 1),
    send_email_notifications BOOLEAN        NOT NULL DEFAULT TRUE,
    send_sms_notifications   BOOLEAN        NOT NULL DEFAULT FALSE
);

-- the due-schedule query: ACTIVE and next_billing_date <= now
CREATE INDEX billing_schedules_due_idx ON billing_schedules (status, next_billing_date);
CREATE INDEX billing_schedules_customer_idx ON billing_schedules (customer_id);
CREATE INDEX billing_schedules_subscription_idx ON billing_schedules (subscription_id);

CREATE TABLE usage_records (
    id                UUID PRIMARY KEY,
    active            BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,
    version           BIGINT,
    customer_id       VARCHAR(255)     NOT NULL,
    subscription_id   VARCHAR(255),
    meter_id          VARCHAR(255)     NOT NULL,
    usage_date        TIMESTAMPTZ      NOT NULL,
    quantity          DOUBLE PRECISION NOT NULL,
    unit              VARCHAR(50)      NOT NULL,
    metadata_json     TEXT,
    source            VARCHAR(255),
    invoiced          BOOLEAN          NOT NULL DEFAULT FALSE,
    invoice_id        VARCHAR(255),
    aggregated_period VARCHAR(50),
    created_by        VARCHAR(255)
);

CREATE INDEX usage_records_uninvoiced_idx ON usage_records (customer_id, meter_id, usage_date) WHERE NOT invoiced;

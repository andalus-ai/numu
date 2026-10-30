-- Tenant-scoped accommodation rate plans and date-specific selling rules.
-- These tables intentionally do not own reservation inventory or booking state.

CREATE TABLE IF NOT EXISTS hospitality_stay_rate_plan (
    tenant_id          VARCHAR(128) NOT NULL,
    rate_plan_id       UUID NOT NULL,
    listing_id         UUID NOT NULL,
    unit_id            UUID NOT NULL,
    code               VARCHAR(64) NOT NULL,
    name               VARCHAR(160) NOT NULL,
    currency           CHAR(3) NOT NULL,
    base_nightly_rate  NUMERIC(19, 4) NOT NULL CHECK (base_nightly_rate >= 0),
    valid_from         DATE NOT NULL,
    valid_until        DATE NOT NULL,
    enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, rate_plan_id),
    CONSTRAINT uq_hospitality_rate_plan_code UNIQUE (tenant_id, listing_id, unit_id, code),
    CONSTRAINT uq_hospitality_rate_plan_scope UNIQUE (tenant_id, listing_id, unit_id, rate_plan_id),
    CHECK (valid_until > valid_from),
    CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX IF NOT EXISTS ix_hospitality_rate_plan_scope
    ON hospitality_stay_rate_plan (tenant_id, listing_id, unit_id, enabled, valid_from, valid_until);

CREATE TABLE IF NOT EXISTS hospitality_nightly_rate_override (
    tenant_id       VARCHAR(128) NOT NULL,
    listing_id      UUID NOT NULL,
    unit_id         UUID NOT NULL,
    rate_plan_id    UUID NOT NULL,
    stay_date       DATE NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL CHECK (amount >= 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, rate_plan_id, stay_date),
    CONSTRAINT fk_hospitality_rate_override_plan
        FOREIGN KEY (tenant_id, listing_id, unit_id, rate_plan_id)
        REFERENCES hospitality_stay_rate_plan (tenant_id, listing_id, unit_id, rate_plan_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_hospitality_rate_override_scope
    ON hospitality_nightly_rate_override (tenant_id, listing_id, unit_id, stay_date);

CREATE TABLE IF NOT EXISTS hospitality_stay_date_restriction (
    tenant_id              VARCHAR(128) NOT NULL,
    listing_id             UUID NOT NULL,
    unit_id                UUID NOT NULL,
    stay_date              DATE NOT NULL,
    min_stay_nights        INTEGER NOT NULL DEFAULT 1 CHECK (min_stay_nights > 0),
    max_stay_nights        INTEGER NOT NULL DEFAULT 0 CHECK (max_stay_nights >= 0),
    closed_to_arrival      BOOLEAN NOT NULL DEFAULT FALSE,
    closed_to_departure    BOOLEAN NOT NULL DEFAULT FALSE,
    stop_sell              BOOLEAN NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, listing_id, unit_id, stay_date),
    CHECK (max_stay_nights = 0 OR max_stay_nights >= min_stay_nights)
);

CREATE INDEX IF NOT EXISTS ix_hospitality_stay_restriction_scope
    ON hospitality_stay_date_restriction (tenant_id, listing_id, unit_id, stay_date);

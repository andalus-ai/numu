-- Mobility (ride-hailing) tables. Aggregates themselves (rides, drivers, wallets, locks) live in aggregate_document (V200).
-- Everything here is tenant-scoped with row-level security on app.tenant_id, like the rest of the platform.

-- ---------------------------------------------------------------------------
-- Live driver positions. UNLOGGED: no write-ahead log, so the constant stream of updates is cheap. The price is that the
-- table is emptied by a crash; drivers re-send their position within seconds, so dispatch recovers by itself.
-- fillfactor leaves room on each page for heap-only updates (a driver mostly stays in the same grid cell).
-- ---------------------------------------------------------------------------
CREATE UNLOGGED TABLE IF NOT EXISTS driver_location (
    tenant_id    uuid             NOT NULL,
    driver_id    uuid             NOT NULL,
    vehicle      text             NOT NULL,
    cell         text             NOT NULL,                  -- ~1 km grid cell key, see GeoCell
    lat          double precision NOT NULL,
    lng          double precision NOT NULL,
    heading      real             NOT NULL DEFAULT 0,
    speed_kmh    real             NOT NULL DEFAULT 0,
    recorded_at  timestamptz      NOT NULL,
    available    boolean          NOT NULL,
    PRIMARY KEY (tenant_id, driver_id)
) WITH (fillfactor = 70);

-- Dispatch's hot query: available drivers of one vehicle type in a handful of cells.
CREATE INDEX IF NOT EXISTS driver_location_search_idx ON driver_location (tenant_id, vehicle, cell) WHERE available;

-- ---------------------------------------------------------------------------
-- Demand: ride requests per grid cell per minute (the "demand" half of surge). Old buckets are pruned by a job.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ride_demand (
    tenant_id  uuid        NOT NULL,
    cell       text        NOT NULL,
    bucket     timestamptz NOT NULL,                         -- start of the minute
    n          integer     NOT NULL CHECK (n > 0),
    PRIMARY KEY (tenant_id, cell, bucket)
);

CREATE TABLE IF NOT EXISTS ride_surge_state (
    tenant_id   uuid        NOT NULL,
    cell        text        NOT NULL,
    multiplier  numeric(4,1) NOT NULL CHECK (multiplier >= 1.0),
    updated_at  timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, cell)
);

-- ---------------------------------------------------------------------------
-- Promotions and their per-rider redemption (a code may be used once per rider).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ride_promo (
    tenant_id     uuid         NOT NULL,
    code          text         NOT NULL CHECK (code ~ '^[A-Z0-9_-]{1,32}$'),
    promo_type    text         NOT NULL CHECK (promo_type IN ('PERCENT', 'FIXED')),
    value         numeric      NOT NULL CHECK (value > 0),
    max_discount  numeric,
    min_subtotal  numeric      NOT NULL DEFAULT 0,
    valid_from    timestamptz  NOT NULL,
    valid_until   timestamptz  NOT NULL,
    PRIMARY KEY (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS ride_promo_redemption (
    tenant_id  uuid NOT NULL,
    rider_id   uuid NOT NULL,
    code       text NOT NULL,
    ride_id    uuid NOT NULL,
    PRIMARY KEY (tenant_id, rider_id, code)                  -- the primary key IS the once-per-rider rule
);

-- ---------------------------------------------------------------------------
-- Wallet ledger: append-only history of every balance movement. Written in the same transaction as the wallet change.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wallet_ledger (
    tenant_id      uuid        NOT NULL,
    entry_id       uuid        NOT NULL,                     -- deterministic from (wallet, ref, kind): replays collide here
    wallet_id      uuid        NOT NULL,
    kind           text        NOT NULL CHECK (kind IN ('CREDIT', 'DEBIT', 'HOLD', 'CAPTURE', 'RELEASE')),
    amount         numeric     NOT NULL,
    balance_after  numeric     NOT NULL,
    ref            text        NOT NULL,
    memo           text        NOT NULL DEFAULT '',
    at             timestamptz NOT NULL,
    seq            bigint      NOT NULL,                     -- the wallet's own version: strictly increasing per wallet, defines the order (timestamps can tie)
    PRIMARY KEY (tenant_id, entry_id)
);
CREATE INDEX IF NOT EXISTS wallet_ledger_history_idx ON wallet_ledger (tenant_id, wallet_id, seq DESC);

CREATE OR REPLACE FUNCTION wallet_ledger_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'wallet_ledger is append-only' USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS wallet_ledger_no_change ON wallet_ledger;
CREATE TRIGGER wallet_ledger_no_change BEFORE UPDATE OR DELETE ON wallet_ledger
    FOR EACH ROW EXECUTE FUNCTION wallet_ledger_immutable();
DROP TRIGGER IF EXISTS wallet_ledger_no_truncate ON wallet_ledger;
CREATE TRIGGER wallet_ledger_no_truncate BEFORE TRUNCATE ON wallet_ledger
    FOR EACH STATEMENT EXECUTE FUNCTION wallet_ledger_immutable();

-- ---------------------------------------------------------------------------
-- Row-level security on every table above (fails closed with no tenant set).
-- ---------------------------------------------------------------------------
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['driver_location', 'ride_demand', 'ride_surge_state', 'ride_promo', 'ride_promo_redemption', 'wallet_ledger'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I', t || '_tenant', t);
        EXECUTE format($p$CREATE POLICY %I ON %I USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
                         WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)$p$, t || '_tenant', t);
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------
-- Partial indexes on the ride documents (kind = 'ride', stored in aggregate_document, see V200). Columns are reused as:
--   venue_id = driver holding the live offer (NULL when none)   ref_id = rider id   starts_at = requested_at
-- ---------------------------------------------------------------------------
-- dispatch sweeper: rides still searching
CREATE INDEX IF NOT EXISTS ride_searching_idx ON aggregate_document (tenant_id, starts_at)
    WHERE kind = 'ride' AND status = 'SEARCHING';
-- driver app: "is there an offer for me?"
CREATE INDEX IF NOT EXISTS ride_live_offer_idx ON aggregate_document (tenant_id, venue_id)
    WHERE kind = 'ride' AND status = 'SEARCHING' AND venue_id IS NOT NULL;
-- settlement safety net: ended rides whose money movements are not marked settled
CREATE INDEX IF NOT EXISTS ride_unsettled_idx ON aggregate_document (tenant_id, starts_at)
    WHERE kind = 'ride' AND status IN ('COMPLETED', 'CANCELLED', 'NO_DRIVERS') AND (doc->>'settled') = 'false';
-- rider history
CREATE INDEX IF NOT EXISTS ride_rider_history_idx ON aggregate_document (tenant_id, ref_id, starts_at DESC)
    WHERE kind = 'ride';

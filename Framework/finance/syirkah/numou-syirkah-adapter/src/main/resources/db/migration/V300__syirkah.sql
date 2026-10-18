-- Syirkah (Islamic partnership) accounting. Flyway. Requires V100 (platform tables) only for the outbox.
-- Design: contracts, partner movements and closes are APPEND-ONLY (triggers reject UPDATE/DELETE); the only
-- permitted change is a close going ACTIVE -> REVERSED by a correction. Movements form a hash chain that the
-- database re-verifies on insert. Every table is tenant-scoped with fail-closed RLS (transaction-local
-- app.tenant_id). The application role must be NOSUPERUSER NOBYPASSRLS.

CREATE TABLE IF NOT EXISTS syirkah_ledger (
    syirkah_id            uuid        PRIMARY KEY,
    tenant_id             uuid        NOT NULL,
    syirkah_type          text        NOT NULL CHECK (syirkah_type IN ('INAN','ABDAN','MUDHARABAH','WUJUH','MUFAWADHAH')),
    currency              char(3)     NOT NULL,
    amount_scale          smallint    NOT NULL CHECK (amount_scale BETWEEN 0 AND 6),
    status                text        NOT NULL CHECK (status IN ('ACTIVE','LIQUIDATING','TERMINATED')),
    closed_since_revision boolean     NOT NULL,
    liquidation           jsonb,
    version               integer     NOT NULL CHECK (version >= 0),   -- optimistic lock
    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS syirkah_ledger_tenant_idx ON syirkah_ledger (tenant_id, status);

CREATE TABLE IF NOT EXISTS syirkah_ledger_contract (
    syirkah_id     uuid    NOT NULL REFERENCES syirkah_ledger (syirkah_id),
    version        integer NOT NULL CHECK (version >= 1),
    tenant_id      uuid    NOT NULL,
    effective_from date    NOT NULL,
    canonical      text    NOT NULL,        -- frozen terms used in close hashes
    contract       jsonb   NOT NULL,        -- full akad (partners, capital, nisbah, clauses, witnesses)
    PRIMARY KEY (syirkah_id, version)
);

CREATE TABLE IF NOT EXISTS syirkah_ledger_movement (
    syirkah_id   uuid        NOT NULL REFERENCES syirkah_ledger (syirkah_id),
    sequence     bigint      NOT NULL CHECK (sequence >= 1),
    tenant_id    uuid        NOT NULL,
    movement_id  uuid        NOT NULL,
    partner_id   uuid        NOT NULL,
    type         text        NOT NULL CHECK (type IN ('CONTRIBUTION','CAPITAL_RETURN','LOSS_ALLOCATED','CAPITAL_RESTORED',
                     'PROFIT_ALLOCATED','PROFIT_RETAINED','PROFIT_DISTRIBUTED','MANAGER_LOSS_LIABILITY',
                     'LIABILITY_SETTLED','DEFICIT_TO_LIABILITY','OVERPAYMENT_TO_LIABILITY','BASIS_CLOSED')),
    amount       numeric     NOT NULL,      -- signed: reversals are negative amounts of the same type
    currency     char(3)     NOT NULL,
    close_id     uuid,
    reference    text        NOT NULL,
    occurred_at  timestamptz NOT NULL,
    prev_hash    char(64)    NOT NULL,
    hash         char(64)    NOT NULL,
    PRIMARY KEY (syirkah_id, sequence),
    CONSTRAINT syirkah_ledger_movement_idem_uq UNIQUE (syirkah_id, partner_id, type, reference)  -- replay is a no-op
);
CREATE INDEX IF NOT EXISTS syirkah_ledger_movement_partner_idx ON syirkah_ledger_movement (syirkah_id, partner_id);

CREATE TABLE IF NOT EXISTS syirkah_ledger_close (
    syirkah_id        uuid        NOT NULL REFERENCES syirkah_ledger (syirkah_id),
    close_id          uuid        NOT NULL,
    sequence          integer     NOT NULL CHECK (sequence >= 1),
    tenant_id         uuid        NOT NULL,
    period_start      date        NOT NULL,
    period_end        date        NOT NULL CHECK (period_end >= period_start),
    contract_version  integer     NOT NULL,
    status            text        NOT NULL CHECK (status IN ('ACTIVE','REVERSED')),
    supersedes        uuid,
    snapshot          jsonb       NOT NULL,   -- frozen financial result, allocation lines, fault finding
    first_movement    bigint      NOT NULL,
    last_movement     bigint      NOT NULL,
    closed_at         timestamptz NOT NULL,
    prev_hash         char(64)    NOT NULL,
    hash              char(64)    NOT NULL,
    PRIMARY KEY (syirkah_id, close_id),
    CONSTRAINT syirkah_ledger_close_seq_uq UNIQUE (syirkah_id, sequence),
    FOREIGN KEY (syirkah_id, contract_version) REFERENCES syirkah_ledger_contract (syirkah_id, version)
);

-- Journal idempotency: one journal entry per source movement, even under retries / replays.
CREATE TABLE IF NOT EXISTS syirkah_ledger_journal_posting (
    tenant_id        uuid        NOT NULL,
    source_key       text        NOT NULL,
    journal_entry_id uuid        NOT NULL,
    posted_at        timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, source_key)
);

-- ---------------------------------------------------------------------------
-- Immutability
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION syirkah_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only (% rejected)', TG_TABLE_NAME, TG_OP USING ERRCODE = 'restrict_violation';
END; $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS syirkah_ledger_contract_immutable ON syirkah_ledger_contract;
CREATE TRIGGER syirkah_ledger_contract_immutable BEFORE UPDATE OR DELETE ON syirkah_ledger_contract
    FOR EACH ROW EXECUTE FUNCTION syirkah_append_only();
DROP TRIGGER IF EXISTS syirkah_movement_immutable ON syirkah_ledger_movement;
CREATE TRIGGER syirkah_movement_immutable BEFORE UPDATE OR DELETE ON syirkah_ledger_movement
    FOR EACH ROW EXECUTE FUNCTION syirkah_append_only();
DROP TRIGGER IF EXISTS syirkah_movement_no_truncate ON syirkah_ledger_movement;
CREATE TRIGGER syirkah_movement_no_truncate BEFORE TRUNCATE ON syirkah_ledger_movement
    FOR EACH STATEMENT EXECUTE FUNCTION syirkah_append_only();

-- A close may only change status ACTIVE -> REVERSED; everything else is frozen.
CREATE OR REPLACE FUNCTION syirkah_ledger_close_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'syirkah_ledger_close is append-only (DELETE rejected)' USING ERRCODE = 'restrict_violation';
    END IF;
    IF (OLD.syirkah_id, OLD.close_id, OLD.sequence, OLD.tenant_id, OLD.period_start, OLD.period_end,
        OLD.contract_version, OLD.supersedes, OLD.snapshot, OLD.first_movement, OLD.last_movement, OLD.closed_at,
        OLD.prev_hash, OLD.hash)
       IS DISTINCT FROM
       (NEW.syirkah_id, NEW.close_id, NEW.sequence, NEW.tenant_id, NEW.period_start, NEW.period_end,
        NEW.contract_version, NEW.supersedes, NEW.snapshot, NEW.first_movement, NEW.last_movement, NEW.closed_at,
        NEW.prev_hash, NEW.hash)
       OR NOT (OLD.status = 'ACTIVE' AND NEW.status IN ('ACTIVE','REVERSED')) THEN
        RAISE EXCEPTION 'syirkah_ledger_close is frozen except ACTIVE -> REVERSED' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS syirkah_ledger_close_guard_trg ON syirkah_ledger_close;
CREATE TRIGGER syirkah_ledger_close_guard_trg BEFORE UPDATE OR DELETE ON syirkah_ledger_close
    FOR EACH ROW EXECUTE FUNCTION syirkah_ledger_close_guard();

-- ---------------------------------------------------------------------------
-- Hash-chain enforcement on insert: gapless sequence and prev_hash = hash of the previous movement/close.
-- (The aggregate computes hashes; the database refuses a broken chain even from a buggy writer.)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION syirkah_movement_chain() RETURNS trigger AS $$
DECLARE last_seq bigint; last_hash char(64);
BEGIN
    SELECT sequence, hash INTO last_seq, last_hash FROM syirkah_ledger_movement
     WHERE syirkah_id = NEW.syirkah_id ORDER BY sequence DESC LIMIT 1;
    IF last_seq IS NULL THEN
        IF NEW.sequence <> 1 OR NEW.prev_hash <> repeat('0', 64) THEN
            RAISE EXCEPTION 'first movement must have sequence 1 and genesis prev_hash' USING ERRCODE = 'check_violation';
        END IF;
    ELSIF NEW.sequence <> last_seq + 1 OR NEW.prev_hash <> last_hash THEN
        RAISE EXCEPTION 'movement chain broken at sequence %', NEW.sequence USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS syirkah_movement_chain_trg ON syirkah_ledger_movement;
CREATE TRIGGER syirkah_movement_chain_trg BEFORE INSERT ON syirkah_ledger_movement
    FOR EACH ROW EXECUTE FUNCTION syirkah_movement_chain();

CREATE OR REPLACE FUNCTION syirkah_ledger_close_chain() RETURNS trigger AS $$
DECLARE last_seq integer; last_hash char(64);
BEGIN
    SELECT sequence, hash INTO last_seq, last_hash FROM syirkah_ledger_close
     WHERE syirkah_id = NEW.syirkah_id ORDER BY sequence DESC LIMIT 1;
    IF last_seq IS NULL THEN
        IF NEW.sequence <> 1 OR NEW.prev_hash <> repeat('0', 64) THEN
            RAISE EXCEPTION 'first close must have sequence 1 and genesis prev_hash' USING ERRCODE = 'check_violation';
        END IF;
    ELSIF NEW.sequence <> last_seq + 1 OR NEW.prev_hash <> last_hash THEN
        RAISE EXCEPTION 'close chain broken at sequence %', NEW.sequence USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS syirkah_ledger_close_chain_trg ON syirkah_ledger_close;
CREATE TRIGGER syirkah_ledger_close_chain_trg BEFORE INSERT ON syirkah_ledger_close
    FOR EACH ROW EXECUTE FUNCTION syirkah_ledger_close_chain();

-- ---------------------------------------------------------------------------
-- Derived partner positions (never stored): fold of the movement ledger. Reconciliation source of truth.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW syirkah_ledger_position WITH (security_invoker = true) AS
SELECT syirkah_id, tenant_id, partner_id, currency,
       sum(amount * CASE type WHEN 'CONTRIBUTION' THEN 1 WHEN 'CAPITAL_RETURN' THEN -1 WHEN 'PROFIT_RETAINED' THEN 1
                              WHEN 'BASIS_CLOSED' THEN -1 ELSE 0 END)                                   AS capital_basis,
       sum(amount * CASE type WHEN 'CONTRIBUTION' THEN 1 WHEN 'CAPITAL_RETURN' THEN -1 WHEN 'LOSS_ALLOCATED' THEN -1
                              WHEN 'CAPITAL_RESTORED' THEN 1 WHEN 'PROFIT_RETAINED' THEN 1
                              WHEN 'DEFICIT_TO_LIABILITY' THEN 1 ELSE 0 END)                            AS capital_balance,
       sum(amount * CASE type WHEN 'PROFIT_ALLOCATED' THEN 1 WHEN 'PROFIT_RETAINED' THEN -1
                              WHEN 'PROFIT_DISTRIBUTED' THEN -1 WHEN 'OVERPAYMENT_TO_LIABILITY' THEN 1 ELSE 0 END) AS profit_payable,
       sum(amount * CASE type WHEN 'MANAGER_LOSS_LIABILITY' THEN 1 WHEN 'DEFICIT_TO_LIABILITY' THEN 1
                              WHEN 'OVERPAYMENT_TO_LIABILITY' THEN 1 WHEN 'LIABILITY_SETTLED' THEN -1 ELSE 0 END) AS liability
  FROM syirkah_ledger_movement
 GROUP BY syirkah_id, tenant_id, partner_id, currency;

-- ---------------------------------------------------------------------------
-- Row-level security (fail closed when app.tenant_id is unset)
-- ---------------------------------------------------------------------------
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['syirkah_ledger','syirkah_ledger_contract','syirkah_ledger_movement','syirkah_ledger_close',
                             'syirkah_ledger_journal_posting']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I', t || '_tenant', t);
        EXECUTE format($p$CREATE POLICY %I ON %I
            USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
            WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)$p$, t || '_tenant', t);
    END LOOP;
END $$;

-- Syirkah maker-checker approvals and transactional outbox (tenant-scoped, RLS fail-closed like V300).

CREATE TABLE IF NOT EXISTS syirkah_ledger_approval (
    approval_id uuid        PRIMARY KEY,
    tenant_id   uuid        NOT NULL,
    syirkah_id  uuid        NOT NULL,
    kind        text        NOT NULL CHECK (kind IN ('REVISE_CONTRACT','CORRECT_CLOSE','START_LIQUIDATION','LIQUIDATE')),
    payload     jsonb       NOT NULL,     -- the exact command the checker approves and that is executed
    maker       text        NOT NULL CHECK (btrim(maker) <> ''),
    proposed_at timestamptz NOT NULL,
    status      text        NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    checker     text,
    decided_at  timestamptz,
    note        text,
    CHECK ((status = 'PENDING') = (decided_at IS NULL))
);
CREATE INDEX IF NOT EXISTS syirkah_ledger_approval_pending_idx ON syirkah_ledger_approval (tenant_id, proposed_at)
    WHERE status = 'PENDING';

-- A request is frozen once decided; only PENDING -> APPROVED/REJECTED is possible, and only the decision columns move.
CREATE OR REPLACE FUNCTION syirkah_approval_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'syirkah_ledger_approval is append-only (DELETE rejected)' USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.status <> 'PENDING'
       OR (OLD.approval_id, OLD.tenant_id, OLD.syirkah_id, OLD.kind, OLD.payload, OLD.maker, OLD.proposed_at)
          IS DISTINCT FROM
          (NEW.approval_id, NEW.tenant_id, NEW.syirkah_id, NEW.kind, NEW.payload, NEW.maker, NEW.proposed_at) THEN
        RAISE EXCEPTION 'syirkah_ledger_approval is frozen once decided' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS syirkah_approval_guard_trg ON syirkah_ledger_approval;
CREATE TRIGGER syirkah_approval_guard_trg BEFORE UPDATE OR DELETE ON syirkah_ledger_approval
    FOR EACH ROW EXECUTE FUNCTION syirkah_approval_guard();

CREATE TABLE IF NOT EXISTS syirkah_ledger_outbox (
    seq          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    uuid        NOT NULL,
    event_id     uuid        NOT NULL,
    event_type   text        NOT NULL,
    syirkah_id   uuid,
    occurred_at  timestamptz NOT NULL,
    payload      jsonb       NOT NULL,
    published_at timestamptz,
    CONSTRAINT syirkah_ledger_outbox_event_uq UNIQUE (tenant_id, event_id)   -- replay is a no-op
);
CREATE INDEX IF NOT EXISTS syirkah_ledger_outbox_unpublished_idx ON syirkah_ledger_outbox (seq)
    WHERE published_at IS NULL;

-- Events are immutable; a relay may only stamp published_at once.
CREATE OR REPLACE FUNCTION syirkah_outbox_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'syirkah_ledger_outbox rows may not be deleted' USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.published_at IS NOT NULL
       OR (OLD.seq, OLD.tenant_id, OLD.event_id, OLD.event_type, OLD.syirkah_id, OLD.occurred_at, OLD.payload)
          IS DISTINCT FROM
          (NEW.seq, NEW.tenant_id, NEW.event_id, NEW.event_type, NEW.syirkah_id, NEW.occurred_at, NEW.payload) THEN
        RAISE EXCEPTION 'syirkah_ledger_outbox events are immutable (only published_at may be set once)'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS syirkah_outbox_guard_trg ON syirkah_ledger_outbox;
CREATE TRIGGER syirkah_outbox_guard_trg BEFORE UPDATE OR DELETE ON syirkah_ledger_outbox
    FOR EACH ROW EXECUTE FUNCTION syirkah_outbox_guard();

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['syirkah_ledger_approval','syirkah_ledger_outbox']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I', t || '_tenant', t);
        EXECUTE format($p$CREATE POLICY %I ON %I
            USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
            WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)$p$, t || '_tenant', t);
    END LOOP;
END $$;

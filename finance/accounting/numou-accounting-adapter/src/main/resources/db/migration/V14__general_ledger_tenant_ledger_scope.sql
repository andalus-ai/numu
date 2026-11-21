-- V14__general_ledger_tenant_ledger_scope.sql
-- Bring the original GL schema in line with the tenant- and ledger-aware domain model.
-- Existing rows are backfilled into the primary ledger before scoped uniqueness is enforced.

SET search_path TO accounting, public;

ALTER TABLE chart_of_accounts
    ADD COLUMN IF NOT EXISTS ledger_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS description VARCHAR(512),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS current_balance NUMERIC(24, 8) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS sharia_compliant BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE chart_of_accounts
SET ledger_id = 'primary'
WHERE ledger_id IS NULL OR BTRIM(ledger_id) = '';

UPDATE chart_of_accounts
SET currency = COALESCE(currency, 'IDR')
WHERE currency IS NULL;

ALTER TABLE chart_of_accounts
    ALTER COLUMN ledger_id SET NOT NULL,
    ALTER COLUMN currency SET NOT NULL;

-- Replace tenant-only uniqueness with tenant + ledger + account number.
ALTER TABLE chart_of_accounts
    DROP CONSTRAINT IF EXISTS uq_account_tenant_code;

CREATE UNIQUE INDEX IF NOT EXISTS uq_coa_tenant_ledger_code
    ON chart_of_accounts (tenant_id, ledger_id, account_code);

CREATE INDEX IF NOT EXISTS idx_coa_tenant_ledger_status
    ON chart_of_accounts (tenant_id, ledger_id, status, account_code);

-- Journal identity is already tenant + ledger scoped for entry numbers.
-- Add missing lifecycle fields required to reconstitute a journal aggregate.
ALTER TABLE journal_entry
    ADD COLUMN IF NOT EXISTS reversal_date TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS reversed_by VARCHAR(64);

-- Store tenant and ledger scope on every journal line so line reads and future
-- row-level security do not need to infer scope through a parent join.
ALTER TABLE journal_line
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ledger_id VARCHAR(64);

UPDATE journal_line jl
SET tenant_id = je.tenant_id,
    ledger_id = je.ledger_id
FROM journal_entry je
WHERE je.id = jl.journal_id
  AND (jl.tenant_id IS NULL OR jl.ledger_id IS NULL);

ALTER TABLE journal_line
    ALTER COLUMN tenant_id SET NOT NULL,
    ALTER COLUMN ledger_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_journal_line_tenant_ledger_account
    ON journal_line (tenant_id, ledger_id, account_id);

CREATE INDEX IF NOT EXISTS idx_journal_tenant_ledger_date
    ON journal_entry (tenant_id, ledger_id, entry_date, id);

-- Ensure journal lines cannot be attached across tenant/ledger boundaries.
-- The unique parent key is explicit to support the composite foreign key.
CREATE UNIQUE INDEX IF NOT EXISTS uq_journal_entry_scope_id
    ON journal_entry (tenant_id, ledger_id, id);

ALTER TABLE journal_line
    DROP CONSTRAINT IF EXISTS fk_journal_line_scope;

ALTER TABLE journal_line
    ADD CONSTRAINT fk_journal_line_scope
    FOREIGN KEY (tenant_id, ledger_id, journal_id)
    REFERENCES journal_entry (tenant_id, ledger_id, id)
    ON DELETE CASCADE;

-- Guard account parent relationships within the same tenant and ledger.
CREATE UNIQUE INDEX IF NOT EXISTS uq_coa_scope_id
    ON chart_of_accounts (tenant_id, ledger_id, id);

ALTER TABLE chart_of_accounts
    DROP CONSTRAINT IF EXISTS fk_coa_parent_scope;

ALTER TABLE chart_of_accounts
    ADD CONSTRAINT fk_coa_parent_scope
    FOREIGN KEY (tenant_id, ledger_id, parent_account_id)
    REFERENCES chart_of_accounts (tenant_id, ledger_id, id)
    DEFERRABLE INITIALLY DEFERRED;

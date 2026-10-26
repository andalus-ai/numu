-- Persist the canonical request fingerprint used to distinguish operation-specific parameters
-- (for example, the hold reference used by a capture) when an idempotency key is replayed.
-- Existing rows remain blank and use the compatibility comparison until their original request
-- payload can be reconstructed; historical ledger entries are never rewritten.
ALTER TABLE wallet_ledger
    ADD COLUMN IF NOT EXISTS request_fingerprint text NOT NULL DEFAULT '';

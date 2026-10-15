# PostgreSQL command idempotency adapter

## Included

- `PgCommandIdempotencyStore`: inserts a tenant/key reservation with `ON CONFLICT DO NOTHING`, then reads the winning row and compares both operation and request fingerprint.
- `RequestFingerprint`: SHA-256 helper for a **canonicalized** request representation.
- Unit tests for deterministic fingerprinting and payload differentiation.

## Critical transaction caveat

The provided store injects `PgPool` and each call can use a different connection. Therefore it does **not** make a reservation, business writes, and stored response atomic as one transaction. It is useful scaffolding and a standalone persistence adapter, but it must not be treated as the final command-execution guarantee.

Before production, refactor to a transaction-aware adapter constructed with or passed the same Mutiny `SqlConnection`/transaction context as the business operation. Within one transaction:

1. Reserve `(tenant_id, idempotency_key)`; the primary key arbitrates concurrent requests.
2. If existing, compare operation and fingerprint. A mismatch is `409 Conflict`; a matching `COMPLETED` row replays the stored response.
3. If matching `PROCESSING`, return an explicit in-progress response or coordinate a bounded wait. Do not execute the business command a second time.
4. Apply business writes and outbox inserts.
5. Store the response and set `COMPLETED` before commit.
6. Commit once. On rollback, reservation and business writes roll back together.

## Fingerprint rules

`RequestFingerprint.sha256` hashes the UTF-8 bytes as supplied. For JSON, the caller must first use a canonical representation; ordinary JSON serialization may differ in property order or numeric formatting. Include the operation and any semantic route/version inputs in the fingerprint input or compare them separately as the store does with `operation`.

## Operational requirements

- Tenant ID must come from trusted authenticated context, not a client-controlled header alone.
- Bound key length and retention. Cleanup only expired records after the replay window has passed.
- Never store secrets or unbounded sensitive response bodies in `response_json`.
- Monitor duplicate-key conflicts, mismatched fingerprints, PROCESSING rows older than expected, and cleanup lag.
- Add PostgreSQL integration tests for two concurrent reservations, matching replay, key reuse with changed operation/fingerprint, completion with wrong fingerprint, rollback, and expiry cleanup.

## Verification boundary

This bundle has not been compiled against the real Syirkah Maven modules and the adapter has not been exercised against PostgreSQL. In particular, the transaction-aware version and integration tests are still required before production use.

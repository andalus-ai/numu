# M19 — Immutable proof-of-delivery evidence

## Goal

Add a tenant-scoped, append-only proof-of-delivery (POD) record that can be safely submitted by courier handhelds, partner callbacks, or retrying integrations without creating duplicate evidence. This is an evidence boundary, not a claim that every shipment lifecycle transition or external provider is already wired to it.

## Included

- A PostgreSQL ledger keyed by tenant and proof ID, with tenant-scoped source/idempotency uniqueness.
- Required shipment and delivery-attempt references, delivery timestamp, recipient label, evidence digest, actor reference, source, idempotency key, and canonical payload fingerprint.
- An append-only database trigger and checks against blank/oversized identifiers and invalid timestamps.
- An application use case that validates the command before delegating to the persistence port.
- A JDBC adapter that performs insert/replay/conflict classification transactionally.
- Unit and migration-contract tests.

## Idempotency contract

- First submission: `ACCEPTED`.
- Same tenant + source + idempotency key + same fingerprint: `REPLAYED`, returning the original proof ID.
- Same key with a different fingerprint: `IDEMPOTENCY_CONFLICT`; the original evidence is never overwritten.
- A caller must compute a stable fingerprint from the canonical business payload. The adapter compares it but does not claim to cryptographically verify the caller's digest.

## Evidence and privacy

This schema stores a digest/reference for the actual evidence rather than image bytes. Upload storage, signed URL issuance, retention/deletion policy, consent, and malware scanning remain responsibilities of the configured evidence service. Avoid storing government IDs, payment-card data, or unnecessary recipient personal information in `recipient_label`.

## Integration and verification gates

- Bind the application port in the production composition root and ensure tenant identity comes from authenticated context.
- Ensure evidence references point to an authenticated, tenant-authorized evidence object before accepting production submissions.
- Integrate accepted POD with the shipment state machine and transactional event/outbox in a subsequent lifecycle milestone; do not mark a shipment delivered merely because an untrusted client supplied this record.
- Run Courier Maven tests and the migration against PostgreSQL in CI, including concurrent same-key inserts, changed-fingerprint conflict, tenant isolation, append-only enforcement, and retry after connection loss.

Tests authored in this milestone are not represented as executed or passing unless CI reports that result.

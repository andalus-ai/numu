# M53 — Tenant-scoped shipment booking idempotency

## Contract
- A caller may supply an idempotency key up to 200 characters, scoped by tenant.
- The application computes a SHA-256 fingerprint from the booking payload, excluding generated shipment identity and request timestamp.
- A retry with the same key and fingerprint returns the originally persisted shipment.
- Reusing a key with a different fingerprint fails explicitly.
- The reservation and shipment/parcel writes share one PostgreSQL transaction.

## Compatibility
Existing constructors remain source-compatible and retain legacy non-idempotent behavior. New external integrations should supply an idempotency key. The Shipment capability remains framework-neutral.

## Verification required
Run the courier domain, application, and PostgreSQL adapter Maven reactors. Include concurrent same-key retries, replay after response loss, payload conflict, tenant isolation, and transaction rollback tests. This milestone is not production-verified until these checks pass.

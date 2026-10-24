# Numou Omnichannel — Milestone 9: Atomic Verified Inbox Transition

## Purpose

Close the critical gap between signature verification and durable worker eligibility. The PostgreSQL adapter must persist an opaque payload reference and move an event from RECEIVED to VERIFIED in one atomic, digest-checked database statement.

## Contract

`InboundEventVerificationStore.markVerified` takes the scoped event key, expected SHA-256 digest, opaque payload reference, and verification timestamp. It returns a safe disposition:

- `VERIFIED`: this call performed the transition.
- `ALREADY_VERIFIED`: the same digest and payload reference were already committed.
- `NOT_FOUND`: no event exists under the scoped key.
- `DIGEST_MISMATCH`: the supplied digest differs from the inbox fingerprint.
- `INVALID_STATE`: the record exists but is not eligible for verification.
- `CONCURRENT_CHANGE`: state changed while this operation was being evaluated; caller should reload/reconcile rather than dispatch.

Only successful outcomes include the opaque payload reference. No raw payload or signature material is stored in the inbox row.

## Database behavior

Migration V3 adds `payload_reference` and `verified_at`, plus a check that a VERIFIED record must have both. The adapter uses one PostgreSQL statement with a conditional UPDATE and a classified fallback result. The row becomes eligible for the existing worker only after the payload reference and verification timestamp are committed with state VERIFIED.

## Required ingress ordering

1. Authenticate provider account and resolve tenant/branch using trusted configuration.
2. Enforce request limits and verify provider signature/freshness against the exact raw bytes.
3. Compute the SHA-256 digest of those exact bytes.
4. Persist bytes in a tenant-isolated, encrypted payload store with finite retention.
5. Atomically claim/deduplicate the event in the inbox.
6. For the sole accepted claim, call `markVerified` with the same digest and opaque payload reference.
7. Dispatch only after a successful durable transition. Duplicate deliveries should not trigger a second dispatch.
8. On failure between payload persistence and verification, apply retention cleanup; never bypass the durable state check.

## Important limitations

This milestone does not provide a concrete encrypted payload-store adapter, provider-specific signature implementations, endpoint framework, or dispatch worker that loads and invokes existing domain application ports. Those remain required before production enablement. A crash after payload persistence but before verification may leave an orphan payload, so expiry cleanup and reconciliation are operational requirements.

## Validation

Run the core module tests and PostgreSQL adapter integration tests in CI. Database tests must cover first transition, idempotent repeat, missing key, digest mismatch, invalid state, competing calls, migration constraints, and confirmation that workers cannot claim RECEIVED rows.

# Numou Omnichannel — M5: Atomic Inbox Claim Contract

## Scope
Adds a provider-neutral port and value objects for durable, atomic deduplication of inbound events. It does not implement storage, a broker, a webhook endpoint or retries.

## Contracts
- `InboundEventKey` scopes an external event ID by tenant, stable provider ID and provider account.
- `InboxClaimDisposition` distinguishes a new accepted event, a same-payload duplicate and an event-ID/payload conflict.
- `InboxClaimResult` carries the stored payload digest for duplicate/conflict outcomes and only permits processing for the accepted claimant.
- `InboundEventInbox` defines the atomic persistence boundary.

## Required adapter guarantees
- Enforce a unique constraint or equivalent atomic insert for the composite key.
- Concurrent claims for the same key must not both return ACCEPTED.
- Same key + same SHA-256 digest is a duplicate; same key + different digest is a conflict that must be quarantined/alerted, never overwritten.
- Commit the inbox record before business dispatch; downstream processing must use the owning Numou application ports.
- Verify provider authenticity and resolve tenant/branch authorization before dispatch.
- Keep raw payload retention, redaction, access control, bounded retries and dead-letter replay in the infrastructure/runtime implementation.

## Validation
Unit tests cover identity normalization, invalid provider identity, processing eligibility and required stored digest. Tests and the Maven reactor have not been run in this environment; CI must validate the M4 dependency and module compatibility before merge.

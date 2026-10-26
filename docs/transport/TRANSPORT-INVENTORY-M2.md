# Transport inventory — M2

## Scope

This milestone introduces a deployment-neutral domain policy for passenger capacity on a scheduled departure. It is deliberately separate from route/schedule authoring and from ticket/payment execution.

- A journey is a half-open route segment: `[fromSequence, toSequence)`.
- Passenger capacity is consumed only on the legs traversed by that journey.
- Active holds and confirmed passengers both consume capacity; expired and released holds do not.
- Hold creation is idempotent by key and request fingerprint. Reusing a key for a different request is a conflict.
- Expired/released holds remain in the aggregate history; capacity release does not erase audit records.
- A departure's inventory can only be opened after the departure is published.

## Ownership and integration

Transport owns scheduled departure inventory because capacity is specific to the route segment and dated departure. The existing Reservation capability owns generic resource/time-slot booking and has its own aggregate and ledger. Its current contract does not express a passenger's origin/destination segment, so this milestone does not force transport bookings into facility/day slots or duplicate Reservation's booking lifecycle.

Payment, wallet movements, accounting journals, ticket issuance, seat assignment, refunds and provider payouts remain separate contracts.

## Persistence and concurrency boundary

The aggregate is immutable and versioned so a repository can persist one coherent inventory snapshot. **This domain object alone is not a cross-process atomicity guarantee.** A production persistence adapter must save the inventory version and holds in one transaction, enforce a tenant/departure-scoped unique idempotency key, and reject stale versions (optimistic locking) or serialize updates. The next persistence milestone must prove concurrent requests cannot oversell, including overlapping journey segments, and must test transaction rollback, tenant isolation, expiry, and replay behavior against the selected database.

## Verification status

Unit tests cover segment overlap, oversell rejection, idempotency, expiry, confirmation, release, audit retention, and publication gating. CI and Maven execution must pass before this milestone is called verified or production-ready.

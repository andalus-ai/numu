# Transport inventory — M4: Booking application workflow

## Scope

This milestone adds the application boundary over M3's inventory aggregate and persistence port. It does not add a transport HTTP endpoint, ticket issuance, payment capture/refunds, or runtime-specific scheduler wiring.

## Implemented

- `transport-application` provides a framework-neutral `DepartureBookingService`.
- Hold creation validates through the domain aggregate, stores a deterministic SHA-256 request fingerprint, and uses the inventory's tenant/departure-scoped idempotency key.
- Replaying the same key and request returns the canonical hold without a second allocation. Reusing a key for a different request is rejected by the domain.
- Confirm and release commands reload the current aggregate and preserve hold history.
- A command that discovers due holds persists their EXPIRED transitions before continuing. A confirmation that races the expiry deadline cannot discard the expiry audit transition.
- Optimistic concurrency conflicts trigger a bounded three-attempt loop. Every attempt reloads the aggregate and re-runs the domain operation; stale snapshots are never re-saved.
- The SPI exposes a bounded due-inventory scan. The PostgreSQL adapter queries only inventories with due HELD rows, orders results deterministically, and enforces a maximum batch size.
- `expireDueHolds(batchSize)` can be called repeatedly by a host scheduler. Multiple instances can safely process the same candidates because writes use the existing compare-and-set contract.

## Contract and deployment boundary

The application service depends only on Transport domain and SPI. It does not depend on HTTP, CDI, a scheduler library, a payment provider, or a ticketing system. A standalone app or modular monolith can invoke it directly; a distributed deployment can expose it through an adapter.

The reusable Reservation capability was inspected. Its current resource/party/time-slot semantics do not fully express a passenger journey's ordered stop segment and per-segment capacity. This milestone therefore does not force an unsafe mapping or create a duplicate Reservation implementation. A future adapter should be added only after a public Reservation contract can preserve these invariants.

Payment authorization/capture, ticket issuance, durable outbox events, API authorization, and runtime scheduler wiring remain separate follow-up work. In particular, a hold is only a capacity claim; it is not proof of payment or an issued ticket.

## Verification

- Application unit tests cover idempotent replay, key misuse, optimistic conflict retry, expiry-before-confirm behavior, and repeated expiry processing.
- PostgreSQL Testcontainers coverage includes the bounded due-inventory query.
- Run the narrow reactor with `mvn -B -ntp -f transport/pom.xml verify` on Java 21. PostgreSQL integration coverage requires Docker; a skipped Testcontainers suite is not equivalent to a successful database verification.

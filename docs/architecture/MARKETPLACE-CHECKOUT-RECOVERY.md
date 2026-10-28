# Marketplace checkout recovery

## Scope

This milestone adds a bounded, tenant-scoped recovery entry point for durable checkout records already persisted by the marketplace checkout saga. It uses the existing public Payment application port through `PaymentCapabilityCheckoutAdapter`; it does not introduce a second payment ledger or query payment persistence directly.

## Recovery behavior

- The PostgreSQL adapter returns the oldest tenant-scoped checkouts in `AWAITING_PAYMENT`, `RECONCILIATION_REQUIRED`, or `COMPENSATION_REQUIRED` state.
- The recovery batch is explicitly bounded (1–1000 candidates) and attempts each row independently.
- Awaiting/ambiguous payment checkouts query the Payment capability by tenant and payment ID. If the original create response was lost, the existing stable payment idempotency key is reused.
- A verified successful payment moves checkout to `READY_FOR_FULFILLMENT`; a known terminal failure starts idempotent reservation release; unresolved states retain reservations.
- Failed compensation attempts remain eligible for another batch. Release commands reuse the same checkout/line-scoped idempotency key.
- Results include counts for ready, failed, compensation-required, reconciliation-required, still-awaiting, and unexpected errors.

## Security and deployment

The worker API requires a tenant ID on every query and mutation. The host composition must authenticate the scheduler/operator and authorize which tenant it may process. This module does not add a global cross-tenant scan or background scheduler; a deployment can invoke `recoverTenant` from its own scheduler, job runner, or operator action.

## Deliberate limitations

- `RESERVING_INVENTORY` rows are not automatically replayed after a process crash. The current checkout snapshot does not persist enough typed inventory request data (warehouse reference and reservation expiry) to safely reconstruct the command. Add a forward migration and immutable request snapshot before automating this recovery.
- `RESERVATION_OUTCOME_UNKNOWN` remains unresolved until an inventory capability can authoritatively query a reservation command key and distinguish committed, rejected, and still-unknown outcomes.
- The repository does not currently expose a neutral, reusable inventory reservation application contract suitable for this marketplace adapter. Do not bind checkout directly to another module's private tables or an industry-specific inventory service. The next integration milestone must define/use the inventory capability's public contract and provide durable idempotency, tenant enforcement, reservation lookup, and idempotent release.
- `READY_FOR_FULFILLMENT` is a readiness signal only. This milestone does not create shipment/fleet bookings, issue stock, or mark delivery complete.

## Verification

The application test suite covers a bounded recovery attempt for an ambiguous payment while ensuring inventory reservations are retained. PostgreSQL integration coverage checks that recovery candidate queries are tenant-scoped and only return recoverable states. CI must verify the affected Maven reactor and Testcontainers tests before production rollout.

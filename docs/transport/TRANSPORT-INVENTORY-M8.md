# Transport M8 — Durable checkout and Payment capability integration

## Status and scope

M8 adds an optional checkout saga that connects a trusted server-side fare quote, a Transport capacity hold, and the public Payment application contract. It does not turn a hold into an order, ticket, or settled payment.

The repository currently has no clearly published, neutral Order application contract suitable for this integration. The implementation therefore does not depend on Marketplace private application services or invent a universal order entity. Order lifecycle integration is a separate follow-up after the owning contract is audited.

## Architecture and optionality

- `TransportCheckoutService` lives in Transport application and depends on Transport-owned SPI ports.
- `TransportCheckoutRepository` persists the checkout correlation in the Transport-owned PostgreSQL schema with tenant-scoped idempotency and optimistic version checks.
- `TransportFareQuotePort` must be implemented by the host's trusted pricing/fare policy. The API never accepts an amount from the passenger.
- `TransportPaymentPort` is a narrow provider-neutral Transport port.
- `transport-payment-adapter` is an optional adapter to `tech.kayys.payment.api.PaymentApplicationPort`. Transport domain and core application do not depend on Payment implementation or persistence.
- Checkout HTTP endpoints and CDI composition are enabled only when the build-time property `numou.transport.checkout.enabled=true` is set and the host supplies both the trusted fare quote provider and a `PaymentApplicationPort` implementation. If those providers are missing, enabling the feature must fail startup rather than use permissive/default implementations.

## API

When enabled:

- `POST /v1/transport/departures/{departureId}/checkouts` — requires `Idempotency-Key`; body includes `fromSequence`, `toSequence`, and `passengerCount`. Returns HTTP 202 with checkout status, amount/currency, hold expiry, payment ID, and customer action URI when the payment provider supplies one.
- `GET /v1/transport/checkouts/{checkoutId}` — reads tenant-scoped checkout state.
- `POST /v1/transport/checkouts/{checkoutId}/refresh` — queries the authoritative Payment application contract and advances the checkout saga.

Every endpoint requires authentication, active tenant membership, and an explicit policy decision. Checkout resource permissions use `transport.booking.checkout`; create uses `CREATE`, read uses `READ`, and refresh uses `PERFORM`. Tenant and actor identifiers still come only from the verified host security context.

## Idempotency and failure recovery

- The checkout idempotency key is unique within a tenant. Reusing it for a different departure, segment, or passenger count is a conflict.
- The fare amount and currency are obtained from the trusted fare quote and persisted before capacity allocation/payment initiation.
- Checkout, hold, and payment use deterministic downstream idempotency keys derived from the durable checkout ID. Replays recover the existing hold/payment instead of allocating or charging twice.
- Checkout persistence uses compare-and-set version updates; stale writers reload and re-evaluate.
- Payment outcomes are read from the Payment application contract, never from a browser redirect or unverified callback.
- A verified successful payment causes Transport to confirm the hold before the checkout is marked paid. A failed/cancelled/expired payment releases the hold.
- If payment succeeds after the hold is no longer confirmable, checkout becomes `RECONCILIATION_REQUIRED`; it is not reported as a successful booking. The operator/refund workflow is not automated by M8.
- Provider timeouts remain ambiguous. Retrying uses the same payment idempotency key and reconciles the existing operation; a timeout is not treated as proof of failure.

## Persistence

Migration `V2__transport_checkout.sql` creates a Transport-owned table with:

- tenant-scoped checkout and idempotency identity;
- immutable request fingerprint and fare quote snapshot;
- hold and payment correlation, including customer action URI;
- lifecycle state and optimistic version;
- unique tenant/payment correlation and an index for pending/manual reconciliation work.

Payment and inventory tables are not modified or shared. The Payment capability continues to own payment persistence and provider verification.

## Verification added

- Application tests cover same-key replay, changed-request conflict, tenant isolation, successful payment confirmation, failed-payment capacity release, and late-payment reconciliation.
- PostgreSQL adapter tests cover checkout persistence, payment action URI round-trip, tenant-scoped keys, duplicate idempotency rejection, and stale-write conflicts.
- The Transport CI workflow installs the public Payment API contract before verifying the Transport reactor.

## Remaining before production

1. Run the Transport reactor and inspect CI results; Testcontainers tests may be skipped if Docker is unavailable.
2. Exercise the checkout endpoints with the host's real authentication, membership, authorization, fare quote, and Payment providers.
3. Configure payment reconciliation/operational alerts and an explicit refund/manual-resolution process for `RECONCILIATION_REQUIRED`.
4. Define and integrate a neutral Order contract before claiming end-to-end order-to-payment fulfillment.
5. Ticket issuance, seat assignment beyond capacity hold, notifications, refunds, and order creation remain out of scope.

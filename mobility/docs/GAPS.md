# What is still missing before a real launch

Status after two build passes. **Built** = implemented and covered by the 415 passing tests (framework wiring excepted, see item 1).
Items are ordered by how much they block a launch.

## Closed since the first gap list (built and tested)
GPS trace storage with polyline evidence and removal of per-fix ride rewrites · route-deviation detection · "share my ride" links · support refunds with caps ·
driver earnings statements · in-app chat with number hiding · fraud/collusion signals · top-up intents with verified provider callbacks (Midtrans, Xendit, generic HMAC) ·
payout lifecycle with exactly-once reversal · public-endpoint security (per-IP limits, no login) · retention purge and rider erasure (SQL, tested on PostgreSQL) ·
nightly maintenance CronJob · address labels carried through booking · separate PIN secret · ledger ordering by wallet version · money-safe JSON.

## P0: still blocks launch

**1. Compile and integration-test the framework layer (about 60 files).** Everything touching Quarkus, Vert.x, Hibernate Reactive, Kafka, Micrometer or OpenTelemetry
was written without those libraries and has never been compiled or run (the business logic beneath it is tested). Budget a real pass: fix compile errors, then
Testcontainers tests (Postgres + Kafka + an OIDC server) for: tenant isolation through HTTP, RLS with the real application role, outbox -> Kafka -> consumer -> inbox,
tenant/MDC context across Mutiny thread hops, SSE, the public and webhook endpoints (including that `permit` paths really skip authentication and are still rate-limited).

**2. Verify the payment providers against their sandboxes.** The Midtrans signature rule, Xendit callback token, and the Snap/Invoice HTTP calls were written from the
providers' public descriptions and are tested only against my own implementation of them. Run each provider's sandbox end to end (create charge, pay, receive callback,
expiry, partial/over-payment, refunds) and confirm field names and status mappings. Add an IP allow-list for callbacks if the provider publishes one.

**3. Automate real money out.** Payouts are *recorded and reversible*, but the transfer itself is a manual bank batch by finance (`ManualPayoutProvider`). To automate: a
disbursement-API adapter, bank-account verification (name match), payout schedule/limits, failure and return handling, reconciliation of provider settlement reports against
the ledger, a general-ledger export, VAT on platform fees and driver income-tax reporting.

**4. Legal and regulatory (not engineering).** Holding rider/driver balances is e-money/payment activity in Indonesia (Bank Indonesia licensing, or a licensed partner holds the
funds); transport platforms have licensing, tariff-band and driver-status rules; PDP-law obligations (consent records, breach process, DPO, data-subject request handling: the
erasure and retention functions exist, the request workflow and audit trail around them do not).

**5. Rider-driver voice and push.** Chat exists; masked calling does not (phone numbers are deliberately never exposed, so there is no way to phone). `RideNotifications`
only logs: wire FCM/APNs (offers need sub-second delivery; polling `GET /driver/offer` works but is slow and wasteful at scale).

**6. Driver onboarding and KYC.** Approve/reject exists. Missing: document capture and verification (ID, licence, vehicle registration, insurance), liveness/selfie, background
checks, vehicle inspection, periodic re-verification, and the ops UI to review them.

**7. Safety operations.** SOS and route-deviation raise events and alerts. Missing: an ops console showing the live ride and letting a human act, emergency-contact
notification, an incident workflow, optional audio recording, authority integration. A staffed response process matters more than the button.

**8. The clients and the back office.** No rider app, driver app or ops/admin console exists (the API is complete). Also needed: tariff, surge-zone and promo management UI
(today code defaults and SQL), support tooling beyond refunds (disputes, lost items), reporting/BI.

## P1: needed to run it well
* **Load, soak and chaos testing** (none done): 10k drivers at one fix per 4 s, dispatch latency in a demand spike, sweeper contention across pods, Kafka lag, failover.
  The ride-document write amplification is fixed (trace is insert-only), but chat messages and offers still rewrite small documents.
* **Location scale**: Postgres UNLOGGED is fine to a few thousand updates/s; plan Redis GEO behind `DriverLocationIndex` before you need it.
* **Maps**: deploy/size OSRM or a commercial API with traffic-aware ETAs; map-matching of GPS; geocoding and autocomplete (the API takes coordinates and free-text labels).
* **Multi-city**: one circular service area and one tariff today. Needed: per-city areas (polygons), tariffs, surge zones, airport/station rules, tolls, operating hours.
* **Fraud depth**: device attestation (Play Integrity / App Attest), mock-location detection, promo budgets and per-device limits, account-takeover protection, chargebacks.
  Current signals are cheap heuristics and only *flag*; tune thresholds on real data.
* **Dispatch quality**: sequential nearest-ranked offers is a solid baseline; batch matching, driver destination mode, adaptive timeouts and pre-positioning incentives come next.
* **Operations**: Grafana dashboards, SLOs (time-to-match p95, booking success), on-call runbooks, backups with tested PITR restore, multi-AZ and DR, Kafka retention and DLQ process,
  advisory-lock leadership for the sweeper (currently every pod sweeps; safe but redundant), external penetration test, WAF/DDoS at the edge.
* **Data lifecycle**: partition/archive `aggregate_document` and `wallet_ledger` by month (retention covers traces, demand, idempotency, outbox/inbox only).

## P2: product breadth (not started)
Scheduled rides, shared rides, multi-stop, delivery/food, corporate accounts, driver incentives/bonuses (statement category exists, no engine), referral programmes,
in-app support chat, multi-language, accessibility.

## Known limitations of what is built
* Wallet hold is 1.2x the fare; a metered trip can exceed it and the difference becomes rider debt (blocks the next booking). A destination change does not resize the hold.
* Flat commission rate and fixed 80/20 cancellation-fee split; no per-driver tiers; no promo budget caps or stacking.
* Fraud signals are heuristics with fixed thresholds; a regular commuter legitimately riding the same driver can trip PAIR_REPEAT (it only flags).
* Chat number-hiding catches digit runs, emails and links; deliberately spelled-out numbers ("zero eight one...") pass.
* Share links reveal live driver position to anyone holding the link until it expires; the rider chooses the duration (max 6 h) and the link can only be created, not revoked.
* Erasure keeps wallet-ledger rows (financial records, pseudonymous ids only) and coarsened ~1 km coordinates for analytics; legal review should confirm that is acceptable.
* The pickup PIN secret must not be rotated while rides are in progress (rotate the quote secret freely).
* Ratings: a driver's rating of a rider is stored on the ride only; there is no rider reputation or blocking.
* Event ordering is not guaranteed across outbox retries (consumers are idempotent); the in-process rate limiters are per replica.
* Tariffs in `FareSchedule.indonesiaDefaults()` are illustrative placeholders, not a published or legally reviewed tariff.

## Defects found and fixed along the way (so you know they existed)
JSON `BigDecimal` scale loss (`28000` returned as `2.8E+4`) · ledger entries ordered by timestamp could mis-state the closing balance (now ordered by wallet version) ·
lazy-evaluation bugs that only appear with real (non-synchronous) Mutiny · flaky privacy assertions that scanned random UUIDs · a driver-release race on duplicate accepts ·
wrong-PIN counters rolled back by exceptions · shared secret for quotes and PINs · per-GPS-fix ride rewrites · maintenance functions callable by the application role.


## Capability-boundary audit — reuse and duplication review (2026-10-26)

This review follows the repository's `AGENTS.md`, ADR-003, and `docs/architecture/DOMAIN-CAPABILITY-MATRIX.md`. It is a source-tree and contract review, not proof that all framework wiring compiles. A similarly named concept is not automatically a duplicate: extract only when lifecycle, invariants, tenant/security semantics, and compatibility requirements match.

### Current Mobility capability map

| Area | Current owner | Boundary assessment |
|---|---|---|
| Ride request, quote acceptance, dispatch offers, pickup PIN/geofence, trip lifecycle, trace, sharing, ride chat, cancellation, ratings, SOS, refund workflow | `mobility-ride-*` | Keep ride-specific orchestration here. Do not move the Ride aggregate into generic Reservation or Order merely because all represent a booking/transaction. |
| Driver registration/approval, online state, ride availability and ride-specific acceptance/rating statistics | `mobility-driver-*` | Keep ride eligibility and online/dispatch state here. Compare the driver/vehicle model with `logistics/numou-fleet`; do not create a second fleet-maintenance or vehicle-assignment lifecycle inside Mobility. |
| Fare quotes, metered fare, surge/cancellation policy, promo math and ride settlement allocation | `mobility-pricing` + ride application | Fare and ride-settlement policy are ride-specific. Compare shared money/rounding primitives with `commerce/pricing`, `finance/billing/core/numou-billing-pricing`, and finance currency before extracting; do not merge unlike tariff/settlement invariants into one universal pricing model. |
| Rider/driver/platform balances, holds/captures, append-only ride ledger, debt/overdraft, top-up intent, payout, earnings statement | `mobility-wallet-*` | Likely the strongest reuse candidate, but **not a safe wholesale replacement** with generic Payment or Accounting. Keep ride wallet balance/ledger invariants local until a reviewed finance/wallet capability can represent holds, debt, platform shards, cash commission, and reconciliation. Use `finance/payment` for provider payment execution/integration where its contract fits; use Accounting for financial posting/reporting rather than making it the ride's operational wallet. |
| Generic geo primitives, service area, cells, polyline simplification, routing port | `mobility-kernel` | Some types are generic candidates for a platform geo/routing capability if other consumers already need the same semantics. Keep driver matching radii, pickup geofences, GPS plausibility, and trip-specific corridor policy in Mobility. Avoid extracting based on one consumer alone. |
| Postgres persistence | `mobility-persistence` plus per-capability in-memory adapters | Composition works as a current packaging choice, but this module imports ride, driver, wallet and pricing contracts together. Treat it as a deployment adapter/composition boundary, not a reusable business capability. If separate deployment is later required, split adapters by owner without changing domain rules. |
| HTTP resources, Quarkus producers, event consumers, sweeper, provider adapters | `mobility-api` and `mobility-integration` | Runtime concerns, not domain owners. Keep provider signatures, HTTP types and framework wiring out of domain/application artifacts. `NoopRideNotifications` is a development fallback, not a production notification capability. |

### Reuse from existing Numou capabilities

| Existing capability | Reuse guidance | Do not duplicate inside Mobility |
|---|---|---|
| Identity and authorization (`identity/`, `security/`) | Use authenticated principal, tenant membership, roles/policies, and audit contracts. Keep rider/driver profiles as Mobility-specific projections/references. | Credentials, authentication sessions, generic user identity, generic role/permission engine. |
| Payment and gateway (`finance/payment/`) | Integrate external payment execution and provider contracts through a Mobility-owned port/adapter. Verify support for authorize/hold/capture/reversal, asynchronous callbacks, refunds and idempotency before switching. | Provider connectivity and generic payment-instrument processing where the shared capability already covers it. |
| Accounting, finance currency and billing | Reuse currency/money/rounding primitives only when precision and semantics match; publish ride settlement postings through an explicit accounting integration when available. | General ledger, tax/reporting, invoices or generic accounting entries as Mobility-owned substitutes. Mobility's trip fare allocation and wallet operational ledger remain distinct until a compatible contract is demonstrated. |
| Notification/messaging and event reliability | Replace logging-only ride notifications with the shared notification delivery capability; use shared outbox/inbox/event infrastructure for durable events and retries. | Generic email/SMS/push delivery, event transport and retry machinery. Ride-specific templates, recipient rules and urgency remain Mobility policy. |
| Document/storage | Use the shared document capability for driver KYC files, licence/registration/insurance evidence, verification provenance and retention policies. | A new generic binary/document store in Mobility. KYC decision state and eligibility policy remain Mobility-owned. |
| Fleet (`logistics/numou-fleet`) | Compare driver and vehicle identity, registration, availability, and assignment responsibilities. Prefer references/adapters if fleet ownership and lifecycle are already authoritative. | A second general vehicle master, maintenance schedule, fleet assignment, or fleet status lifecycle. A ride-hailing driver's online/eligible-to-accept state is not the same as vehicle maintenance status. |
| Reservation (`reservation/`) and Order/Commerce | Reuse generic time-slot reservation only for a capability with the same hold/expiry/concurrency semantics. Ride request + live dispatch is not a conventional confirmed reservation; generic order semantics may be useful for purchase history but not a substitute for the Ride aggregate. | A second generic reservation engine, or forcing dispatch/offer state into a generic reservation status enum. |
| Reviews/ratings, CRM/support | Reuse shared review primitives only if they support bilateral, post-trip, ride-context ratings and moderation rules; use CRM/support for case management where contracts fit. | Generic customer/driver review store or a second case-management platform without checking existing contracts. Ride-specific eligibility, anti-abuse, and trip linkage stay in Mobility. |
| Maps/routing/location | Reuse a stable routing/geocoding/location adapter if one exists with suitable ETA, traffic, privacy, and service-area semantics. Keep `RoutingPort` as the boundary. | A second vendor client or geospatial service without comparing existing adapters. Straight-line fallback is not production traffic-aware routing. |

### Findings and severity

1. **High — wallet/payment/accounting boundaries need an explicit integration contract.** Mobility's wallet is more than a provider-payment wrapper: it owns holds, ride captures, driver cash debt, platform shard balancing, append-only entries, payouts and earnings. Replacing it with `finance/payment` or Accounting directly would conflate distinct invariants. First document a provider-payment port and an accounting-posting contract; then assess whether a reusable operational wallet capability already exists or should be extracted from Mobility.
2. **High — framework/runtime verification remains a launch blocker.** `GAPS.md` already records that roughly 60 Quarkus/Vert.x/Hibernate Reactive/Kafka/metrics/tracing files were not compiled or integration-tested in the documented pass. Boundary refactors should not be mixed with that verification pass, and no architecture audit should be treated as runtime verification.
3. **Medium — Driver and Fleet overlap is plausible, not yet proven duplication.** Compare `mobility-driver-domain/Vehicle` and driver profile/availability with `logistics/numou-fleet` contracts and usages. Extract only shared vehicle master/maintenance/assignment semantics; preserve Mobility's ride-acceptance eligibility and live location behavior.
4. **Medium — geo primitives may be shared, but ride dispatch policy is not.** Inventory actual non-Mobility consumers of `LatLng`, service areas, routing and geospatial helpers before creating a generic module. Keep the existing port until a second real consumer and compatible semantics are confirmed.
5. **Medium — notifications are currently an adapter gap.** `NoopRideNotifications` / logging behavior is not production push delivery. Integrate the shared notification capability rather than implementing FCM/APNs delivery as a second generic notification system.
6. **Medium — mobility-persistence is a cross-capability adapter bundle.** This is not inherently wrong for a modular monolith. Keep it if it is an intentional deployment unit; if capabilities need independent deployment, split the persistence adapter by owner, not by arbitrary class count.
7. **Low/verify — fare/pricing overlap.** Shared money/rounding may be reusable; fare schedules, surge, metered fare, promos and ride settlement allocation have mobility-specific rules. No extraction should happen until public contracts and property tests demonstrate semantic equivalence.

### Proposed implementation sequence

1. **Inventory contracts and dependency graph:** inspect public APIs, POM dependencies and actual consumers for Identity, Payment, Accounting, Notification, Document, Fleet, Reservation/Order, Pricing and Maps. Mark each relationship as reuse, adapter, reference-only, or intentionally owned by Mobility.
2. **Resolve only proven duplication:** prioritize provider-payment integration and shared notification delivery; separately compare driver/vehicle ownership with Fleet. No mass package move or speculative “common” module.
3. **Add architecture guards:** domain/application modules must not depend on Quarkus/REST, provider SDKs, or another capability's persistence internals; the runtime may depend inward on the selected adapters. Verify optional capability dependencies do not leak transitively to the whole suite.
4. **Verify the runtime before expanding product scope:** compile the Mobility reactor, fix framework/API mismatches, then run PostgreSQL/Kafka/OIDC integration tests for tenant isolation, outbox/inbox, callbacks, SSE and notification delivery. Record actual command output.
5. **Validate product completeness separately:** rider/driver/ops clients, KYC evidence, live SOS operations, push/masked calling, multi-city policy, load testing and scheduled/shared/multi-stop rides remain product/launch gaps, not reasons to duplicate platform capabilities.

**Scope note:** this is an ownership and source-tree audit. The matrix intentionally distinguishes confirmed current ownership from candidates requiring contract-level inspection. No code, schema, public API or module dependency was changed by this audit text alone.


## Contract-level reuse audit — wallet/payment and driver/fleet (2026-10-26)

This follow-up inspects concrete domain contracts in the current `main` source tree. It is still a source review; no build or runtime test was executed here.

### 1. Mobility Wallet vs shared Payment and Accounting

**Evidence inspected**
- `mobility/wallet/mobility-wallet-domain/.../Wallet.java`
- `mobility/wallet/mobility-wallet-domain/pom.xml`
- `mobility/wallet/mobility-wallet-application/pom.xml`
- `finance/payment/numou-payment-core/.../Payment.java`
- `finance/payment/numou-payment-core/.../PaymentRefund.java`

**Decision: keep the Mobility operational wallet as a separate domain for now; integrate shared payment execution behind a port. Do not replace it with the current Payment entity.**

The Mobility `Wallet` is a versioned domain aggregate with currency fraction digits, available-balance holds, idempotent operation references, captures that can leave a negative balance/shortfall after service delivery, overdraft policy, frozen state, and owner kinds for rider/driver/platform. Those are operational balance and settlement invariants.

The inspected shared `finance/payment` `Payment` is a Quarkus Hibernate/Panache persistence entity for a payment transaction: invoice, payment method, amount, provider/gateway references, payment status and metadata. `PaymentRefund` records gateway refund state. The inspected contract does not itself provide the wallet's per-owner balance, hold lifecycle, overdraft/debt, cash-ride commission accounting, or immutable operational ledger semantics. A name match is not contract equivalence.

**Boundary to implement**
- Mobility application owns a small `ExternalPaymentPort` (or equivalent existing port if one already exists) for top-up/payment-intent execution, provider status, refund/reversal and callback correlation.
- A shared payment adapter implements that port and translates provider states; it must not mutate Mobility wallet balances directly from untrusted callbacks.
- Wallet credit occurs only after verified, idempotent confirmation, keyed by a stable provider/payment reference. Duplicate callbacks and retries must be harmless.
- Ride fare capture, cash commission/debt, driver earnings and platform allocations remain Mobility settlement rules until a separately reviewed reusable wallet/ledger contract can represent the same invariants.
- Add a separate accounting-posting integration only when the accounting contract and chart-of-accounts mapping are known. Reconcile postings to wallet movements; do not treat provider payment records as the general ledger.
- Before switching adapters, test duplicate/out-of-order callbacks, partial and failed refunds, currency/fraction-digit mismatches, provider timeouts, reversals, and tenant/merchant isolation.

**Important risk:** `Wallet` keeps only the last `MAX_REFS = 500` operation references in the aggregate. That is a bounded replay window, not a permanent global idempotency ledger. Before launch, verify whether persistence/outbox/inbox provides durable uniqueness for older replayed references and whether this bounded behavior is intentional. Do not silently assume the in-memory set alone guarantees forever-idempotency.

### 2. Mobility Driver/Vehicle vs Fleet

**Evidence inspected**
- `mobility/driver/mobility-driver-domain/.../Driver.java`
- `mobility/driver/mobility-driver-domain/.../Vehicle.java`
- `logistics/numou-fleet/.../domain/model/Driver.java`
- `logistics/numou-fleet/.../domain/model/Vehicle.java`

**Decision: confirmed overlap in the broad concepts, but not a safe direct aggregate substitution. Establish ownership/reference integration before adding further vehicle master or maintenance behavior to Mobility.**

Mobility's driver aggregate is deliberately focused on ride-hailing: onboarding approval/rejection, ONLINE/OFFLINE/ON_TRIP state, atomic claim/release against a ride, dispatch acceptance/cancellation signals and ride rating. Its embedded `Vehicle` is a compact ride-recognition record (type, plate, model, color).

Fleet's driver aggregate is a workforce/fleet record with employment, licence, training, incidents, schedules, assignments and availability. Fleet's vehicle aggregate is substantially broader: registration/VIN, insurance, specifications, current driver/assignment, odometer/usage, maintenance history, documents, alerts and telematics. These represent overlapping driver/vehicle master data but different aggregate responsibilities and lifecycle depth.

**Boundary to implement**
- Fleet should be the candidate system of record for fleet-owned vehicle identity, registration/compliance evidence, maintenance/inspection status and general vehicle assignment where the operator actually manages a fleet.
- Mobility should retain ride-specific driver approval/eligibility, real-time online state, current ride claim, dispatch ranking signals and rider-facing recognition snapshot.
- Link via stable external `VehicleId` / `DriverId` references and an adapter/projection, not direct cross-domain entity imports or shared database writes.
- A private/independent driver-partner who is not managed as part of an operator fleet must remain supportable; Fleet cannot become a mandatory runtime dependency for all Mobility deployments.
- Define an eligibility contract: Mobility must reject a vehicle/driver when required compliance is expired, vehicle is unavailable/maintenance-blocked, or a fleet assignment is incompatible. Do not conflate Fleet's `AVAILABLE/ASSIGNED` status with Mobility's `ONLINE/ON_TRIP` state.
- Avoid duplicating KYC document storage; use shared Document for evidence files and let Fleet/Mobility retain their own domain decisions and references.

### Next implementation gates

1. Inspect existing Mobility wallet SPI and integration wiring before adding any new port; reuse an existing compatible port rather than duplicating it.
2. Trace the actual shared payment gateway capabilities and current wallet top-up flow end-to-end; add an adapter only where a concrete gap is confirmed.
3. Define a versioned Fleet-to-Mobility eligibility/reference contract and migration strategy before changing persisted Mobility driver/vehicle fields.
4. Add contract tests for callback idempotency and vehicle eligibility transitions before enabling either integration in production.
5. Keep these changes in one coherent milestone PR after the contract and runtime tests are ready; this document does not claim the integrations have been implemented.

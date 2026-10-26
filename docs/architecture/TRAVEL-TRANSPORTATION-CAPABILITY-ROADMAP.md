# Travel & Transportation Capability Roadmap

**Repository:** `andalus-ai/numu`  
**Status:** Proposed implementation roadmap; implementation status must be verified milestone by milestone  
**Architecture:** ADR-003 reusable capability modules; capability-first, deployment-neutral

## 1. Vision

Build reusable travel and passenger-transport booking capabilities for customer-facing applications and provider/operator systems. Initial commercial focus is Indonesia, with regional and enterprise expansion supported by provider adapters, configurable policies, localization, and deployment-neutral contracts.

This is a capability family, not one mandatory travel application and not a requirement to deploy every module as a microservice. The same business rules must support standalone applications, modular monoliths, selected microservices, cloud, and on-premises/enterprise compositions.

## 2. Product scope

### Passenger and trip discovery
- Search by origin, destination, date/time, passenger count, journey type, and supported service attributes.
- Discover fixed-route services, scheduled departures, point-to-point transfer offers, charters, and external provider inventory through explicit adapters.
- Support one-way, return, and multi-leg itineraries where providers and fare rules permit.
- Present availability and price with source, freshness, currency, and fare-condition information; never represent stale or unverified inventory as confirmed.

### Scheduled transport and shuttle operations
- Operators, service offerings, routes, ordered stops, service calendars, departure instances, vehicles/capacity references, and service exceptions.
- Passenger capacity and seat/space inventory, optional seat maps, passenger manifests, boarding validation, and departure/arrival operational states.
- Recurring employee, campus, hotel, airport, tourism, intercity, and community shuttle services.
- Service disruption, cancellation, reassignment, passenger notification, and documented recovery workflows.

### Reservations and ticketing
- Temporary inventory holds with explicit expiry and safe release.
- Booking/order lifecycle, passengers, itinerary legs, selected service and fare snapshots, ticket/confirmation references, and booking history.
- Idempotent booking and payment retries; concurrency-safe capacity allocation; prevention of overselling.
- Changes, cancellation, refunds, no-show, partial itinerary failure, provider rejection, and compensation policies.
- Booking confirmation must only follow the authoritative inventory/provider confirmation required by the chosen fulfillment model.

### Provider and operator workspace
- Provider onboarding and scoped operator/branch/terminal access.
- Service, schedule, fare, capacity, availability, blackout, booking, manifest, boarding, and disruption management.
- Contracted provider offers and commission/fee policies with effective dates and auditable revisions.
- Reconciliation of provider booking state, payment state, ticket issuance, refunds, and settlements.

### Travel orchestration (later milestone)
- Itinerary planning and assembly across transport, accommodation, activities, and other separately owned capabilities.
- A trip/itinerary aggregates references and traveler intent; it does not absorb hotel inventory, payment execution, ride dispatch, or other capability-owned lifecycles.
- Multi-provider checkout must explicitly model partial success, timeout, cancellation, compensation, and operator-visible exceptions.

## 3. Capability boundaries

Keep concepts in the capability that owns their invariants. Names below are proposed ownership seams, not a claim that matching modules already exist.

| Capability / boundary | Owns | Must not own |
|---|---|---|
| Travel & itinerary | Traveler trip intent, itinerary composition, leg ordering, traveler-facing trip status and cross-provider coordination | Provider seat inventory, ride dispatch, hotel room inventory, accounting ledger |
| Transport service catalog | Reusable service offers, route/stop and timetable references, searchable transport attributes | Booking/payment state or live driver dispatch |
| Scheduled transport operations | Departures, service instances, manifests, boarding and operational disruption lifecycle | Generic booking orchestration for every industry |
| Transport inventory | Capacity/seat allocation, temporary holds, expiry, concurrency and release rules for transport departures | General-purpose inventory or hotel-room rules unless semantic equivalence is proven |
| Reservation / booking | Reservation lifecycle, confirmation/cancellation/change orchestration and booking history where an existing generic capability fits | A competing generic reservation implementation; provider-specific state without an adapter/port |
| Provider adapters | Mapping external provider search, availability, booking, cancellation, ticketing and reconciliation semantics to versioned ports | Hiding provider-specific uncertainty or claiming unsupported capabilities |
| Mobility | On-demand ride request, dispatch, driver eligibility/operational state, trip lifecycle and ride-specific fare policies | Fixed-departure shuttle inventory or a second general reservation engine |
| Finance / payment / wallet / accounting | Existing payment execution, wallet policies and accounting responsibilities through documented contracts | Booking-specific eligibility rules or direct cross-capability table writes |
| Customer, identity, notification, documents, maps/geography | Existing shared contracts where present and semantically appropriate | Duplicated identity, notification, location, payment, or document infrastructure |

Before implementing a new module, inspect current Maven reactor roots, public contracts, tests, ADRs, and the actual Mobility, Reservation, Finance/Wallet, Customer, Notification, Geography/Maps, Fleet, Hospitality, and commerce modules. Reuse only when semantics and lifecycle genuinely match. Record gaps rather than assuming a named capability exists or is production-ready.

## 4. Packaging and deployment

- Keep domain models and policies independent of HTTP, CDI/runtime wiring, persistence frameworks, brokers, cloud SDKs, and vendor-specific APIs.
- Publish explicit domain/application ports and versioned integration contracts; use in-process composition for a modular monolith and remote adapters when distribution is justified.
- Consumers declare only the capability artifacts they need. Do not make travel, booking, provider integrations, or infrastructure dependencies transitive across the whole framework.
- Tenant scope, actor authorization, correlation, idempotency, audit, and data-access policy must be enforced at application boundaries.
- Use optimistic/pessimistic concurrency or equivalent database guarantees for inventory claims; define hold expiration and race behavior explicitly.
- Use durable outbox/inbox or equivalent reliable event delivery where asynchronous integration requires it. Events must support deduplication and observable retry/failure handling.
- Provider-specific adapters must declare support for search, availability, holds, booking, ticketing, changes, cancellation, refund, and reconciliation independently; do not pretend every provider supports every operation.
- Provide composition examples for standalone shuttle operator, modular-monolith travel marketplace, and distributed marketplace with external transport providers.

## 5. Core domain and state-machine requirements

Define state machines and invariants before APIs and persistence. At minimum specify:
- Inventory: available -> held -> confirmed, with hold expiry/release and explicit handling of provider-authoritative inventory.
- Booking: draft/requested -> pending confirmation -> confirmed/ticketed, with separate failed, expired, cancellation-pending, cancelled, change-pending, and refund states as required by real provider semantics.
- Departure/service: planned -> open for sale -> boarding -> departed -> arrived, with cancelled/disrupted states and policy-defined transitions.
- Payment, booking, ticket issuance, and refund are separate lifecycles correlated by stable identifiers; none should be inferred solely from another's status.
- Return and multi-leg journeys require leg-level state and explicit partial-failure/compensation semantics.
- Money uses currency-aware exact decimal/minor-unit rules, with immutable fare/fee/tax snapshots and explicit rounding policy.
- Time uses unambiguous instants plus the relevant service timezone; recurring schedules must handle timezone and calendar exceptions.
- Passenger personal data is minimized, access-controlled, retention-aware, and not copied indiscriminately into events/logs.
- Every externally retriable write is idempotent within a defined tenant/caller scope and bound to a stable request intent.

## 6. Indonesia-first, region-ready requirements

- Configurable local currencies, languages, time zones, date/number formats, and provider-specific fare/tax/fee policies.
- Local payment methods and settlement behavior through existing payment/finance contracts and adapters; no provider secrets or payment credentials in domain models.
- Configurable passenger identification/document requirements by route, operator, transport mode, and applicable jurisdiction; do not hardcode one universal rule.
- Provider/operator contract, commission, cancellation, refund, and service-disruption terms must be versioned and snapshotted at booking time.
- Accessibility, baggage, child/infant, group, and special-assistance policies should be explicit extension points instead of hard-coded universal assumptions.
- Support Indonesian operational needs first without making Indonesia-only assumptions mandatory in neutral domain contracts.

## 7. Milestones

### M0 — Repository audit and capability map
- Read `AGENTS.md`, ADR-003, the packaging/composition contract, capability matrix, relevant capability roadmaps, and all applicable nested agent instructions.
- Inventory actual modules, public contracts, reactor roots, schemas/migrations, tests, CI entry points, and open PRs.
- Map overlap/gaps across Mobility, Reservation, Fleet, Hospitality, Finance/Wallet, Payment, Customer, Notifications, maps/geography, and existing provider integrations.
- Produce an evidence-based ownership matrix and ADR for any durable boundary decision.
- **Acceptance:** no new competing core module is introduced before overlap review; each proposed dependency points to an existing public contract or is explicitly recorded as a gap.

### M1 — Transport service catalog and scheduled service model
- Implement only the confirmed missing catalog/schedule contracts and domain invariants.
- Model operators, service offers, routes/stops, service calendars, departure instances, and schedule revisions where supported by actual needs.
- Add unit tests, tenant isolation tests, architecture boundary checks, and persistence/migration tests for any stored model.
- **Acceptance:** domain tests run without network infrastructure; a shuttle operator can define and publish a recurring scheduled service; no dependency on Mobility dispatch internals.

### M2 — Concurrency-safe capacity and reservation integration
- Reuse an existing reservation capability if its contract fits; otherwise document why transport-specific inventory/booking behavior requires an extension.
- Implement hold expiry/release, concurrent claims, idempotent retries, capacity invariants, confirmation and cancellation semantics.
- **Acceptance:** parallel booking tests cannot oversell capacity; retries do not create duplicate bookings, movements, tickets, or events; expiry and rollback are tested.

### M3 — Passenger checkout, ticketing, and lifecycle
- Integrate payment execution and refunds through existing contracts.
- Add passenger records, immutable booking/fare snapshots, confirmations, ticket references, changes, cancellations, and auditable lifecycle history.
- **Acceptance:** booking/payment/ticket states remain distinct; provider or payment failure produces deterministic, recoverable outcomes.

### M4 — Provider and operator workflows
- Operator console/API contracts for schedules, capacity, manifests, boarding, disruption, and booking operations.
- Provider adapter ports and at least one contract-tested adapter or in-house provider implementation.
- **Acceptance:** provider-specific capabilities and failure modes are explicit; booking reconciliation detects divergence and supports safe retries.

### M5 — Customer booking and shuttle applications
- Customer search, offer comparison, traveler details, checkout, booking history, changes/cancellation, ticket access, and disruption notifications.
- Provider workflows for scheduled shuttle, airport transfer, corporate/employee shuttle, and charter where supported by the domain.
- **Acceptance:** end-to-end tests cover a complete booking and operational boarding journey; user-facing screens never treat search results as confirmed inventory.

### M6 — Travel itinerary and multi-provider orchestration
- Assemble references to transport and other existing travel capabilities into a traveler itinerary.
- Model partial confirmation, compensation, timeout, cancellation, and support/operator intervention across providers.
- **Acceptance:** integration tests prove one trip can reference independently owned reservations without sharing their persistence models; partial failure is observable and recoverable.

### M7 — Production readiness and deployment profiles
- Threat model, authorization matrix, privacy/retention review, audit review, rate limits, abuse controls, observability, metrics, traces, operational runbooks, backup/restore, and disaster-recovery expectations.
- Verify standalone, modular-monolith, and distributed adapter compositions using the same business contracts.
- Load/concurrency testing for search, holds, booking writes, and reconciliation; migration and compatibility testing.
- **Acceptance:** documented reproducible test commands and actual results; no claim of production readiness until release gates and operational evidence pass.

## 8. Cross-cutting acceptance gates

Every milestone that changes behavior must include:
1. Domain tests for valid/invalid transitions and boundary conditions.
2. Tenant isolation and authorization tests for application-facing operations.
3. Idempotency, duplicate-event, and audit tests for retriable side effects.
4. Concurrency and transaction/rollback tests where inventory, booking, money, or state changes race.
5. Architecture tests that prevent reverse dependencies and private persistence access.
6. Migration/compatibility tests when persistence or public contracts change.
7. Verification of the narrowest affected Maven reactor, then dependent reactors and CI as practical. Report environment failures separately from code failures.
8. At least two consumer compositions over time—for example, a shuttle operator application and a travel marketplace—without duplicated domain rules.

## 9. Explicit non-goals

- Do not rewrite the existing Mobility module into a travel booking engine.
- Do not create a duplicate generic Reservation, Payment, Wallet, Customer, Notification, Maps, Fleet, or Hospitality capability.
- Do not require all deployments to use microservices, a particular database, a cloud vendor, or one external travel aggregator.
- Do not add fake provider integrations or report simulated availability as live availability.
- Do not declare a milestone complete based on documentation, generated scaffolding, compilation alone, or an unverified CI status.
- Do not merge changes without explicit user authorization.

## 10. Delivery strategy

Deliver one coherent pull request per meaningful milestone, not one PR per tiny edit. Start with M0 evidence and boundary decisions; implement only after actual modules and contracts are inspected. Keep unrelated repository repairs separate, and make each milestone reviewable with tests, compatibility notes, and a clear statement of what remains incomplete.

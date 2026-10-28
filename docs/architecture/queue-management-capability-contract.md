# Queue Management Capability Contract

**Status:** Proposed M0 contract; implementation and runtime behavior are not yet verified  
**Scope:** Reusable queue management for customer waiting, field-service dispatch coordination, and operational work routing  
**Repository:** `andalus-ai/numu`  
**Related:** [ADR-003 — Reusable capability modules](ADR-003-reusable-capability-modules.md), [Domain capability matrix](DOMAIN-CAPABILITY-MATRIX.md), [M0 audit issue #1020](https://github.com/andalus-ai/numu/issues/1020)

## 1. Decision proposal

Introduce Queue Management as a **logical reusable capability**, not automatically as a standalone deployable service. It should own queue membership, queue ordering/policy evaluation, queue-entry lifecycle, and the coordination semantics that are genuinely shared across consumers.

The exact Maven reactor/module shape is intentionally not fixed until the complete public-contract, dependency, and build audit is finished. Follow ADR-003: capability boundaries do not prescribe deployment topology, and domain rules must work in standalone, modular-monolith, and distributed compositions.

### Why not put all queues into Scheduling, Support, or Field Service?

The inspected repository already has distinct capabilities and lifecycles:

- `scheduling/pom.xml` defines a `numou-scheduling` reactor with domain, SPI, application, and adapter modules. Its domain includes `Schedule`, `Shift`, `WorkPattern`, and `ResourceAvailability`.
- `scheduling/scheduling-domain/.../Schedule.java` models a tenant-scoped schedule with draft/published/cancelled behavior; that is not the same lifecycle as a waiting ticket or dispatch offer.
- `workforce/pom.xml` defines a separate Workforce reactor.
- `vehicle-service/pom.xml` defines a vehicle-service extension.
- `support/pom.xml` defines a Support bounded context.
- `logistics/pom.xml` defines fleet/courier modules, and the repository contains courier dispatch waves and asset maintenance work orders.
- `docs/architecture/customer-capability-contract.md` explicitly establishes that source transaction capabilities own orders, bookings, shipments, work orders, invoices, and payments.

These findings support a dedicated queue boundary, but do not prove that every existing queue-like primitive has been found. Before implementation, finish searches of source, public APIs, tests, POM dependency edges, and migration conventions.

## 2. Ownership boundaries

| Concern | Authoritative owner | Queue Management relationship |
|---|---|---|
| Queue definitions, entry admission, ordering and queue policy | Queue Management | Own |
| Waiting-ticket state and queue transfer history | Queue Management | Own for waiting/service queues |
| Dispatch offers, queue position, requeue and escalation | Queue Management, within its defined dispatch contract | Own coordination state; do not own the underlying job |
| Customer/person/organization identity and customer role | Party / Customer | Store stable references only |
| Leads, opportunities and sales activities | CRM | Optional adapter/consumer |
| Work patterns, schedules, shifts and resource availability | Scheduling | Query/coordinate through public contracts; do not duplicate |
| Workforce identity, skills and employment-related state | Workforce | Query/coordinate through public contracts; do not duplicate |
| Vehicle service job/work order | Vehicle Service or owning work-order capability | Source capability owns job lifecycle |
| Shipment and courier dispatch wave | Courier / Logistics | Source capability owns shipment and wave lifecycle |
| Support case lifecycle | Support | Source capability owns case lifecycle |
| Notifications and delivery providers | Notification / channel adapters | Send updates; provider failure must not corrupt queue state |
| Workflow orchestration | Workflow or owning application | Queue Management is not a universal workflow engine |

Queue Management must not require CRM or Customer to run. A caller may join a queue using an external party/customer reference, an appointment reference, a work-item reference, or an anonymous token where the business policy permits it.

## 3. Model separate queue profiles

Share primitives only where semantics are the same. Do not force these profiles into a single universal state machine.

### 3.1 Waiting/service queue

Suggested entry states:

`WAITING -> CALLED -> IN_SERVICE -> COMPLETED`

Additional transitions:
- `WAITING -> CANCELLED`
- `WAITING -> NO_SHOW`
- `CALLED -> WAITING` (explicit recall/requeue policy)
- `WAITING | CALLED -> TRANSFERRED` (records source and destination queue and audit history)
- `IN_SERVICE -> WAITING` only through an explicit authorized return-to-queue operation

The queue policy defines whether an entry may be recalled, transferred, rejoined, or reopened. Repeated completion/cancellation requests must be idempotent where the command contract says so.

### 3.2 Dispatch queue

Suggested coordination states:

`ELIGIBLE -> OFFERED -> ACCEPTED -> IN_PROGRESS -> COMPLETED`

Additional transitions:
- `OFFERED -> REJECTED -> ELIGIBLE`
- `OFFERED -> EXPIRED -> ELIGIBLE`
- `ACCEPTED -> REASSIGNED` only through an authorized transition with a reason
- `ELIGIBLE | OFFERED -> CANCELLED` when the source work item is cancelled or an authorized caller withdraws it

These are queue-coordination states, not replacements for the authoritative work-order, shipment, inspection, or service-case lifecycle. A dispatch entry must reference a stable source capability and source record ID.

### 3.3 Operational work queue

This profile routes a reference to a case, task, claim, approval, discrepancy, or other source-owned work item. Queue Management may record routing, eligibility, ownership/claim, aging, and escalation; the source capability remains authoritative for business completion and domain-specific states.

## 4. Queue policy and ordering

A queue definition should include:
- tenant and queue identity; optional branch, site, service, and operating-calendar references;
- queue profile and configured lifecycle policy version;
- supported admission modes (walk-in, appointment check-in, API, kiosk/QR, staff-created, source-event);
- ordering strategy and tie-break rules;
- eligibility and resource constraints where relevant;
- service-level targets and escalation policy;
- visibility/access policy and retention requirements.

Ordering strategies may include FIFO, appointment window, deadline/SLA, priority class, or a policy-composed ranking. The policy must be deterministic and versioned so an operator can explain why an entry was ordered or assigned.

**Precedence invariant:** legal, safety-critical, accessibility, and contractual constraints must be evaluated before discretionary commercial/VIP priority. A VIP flag cannot bypass a hard eligibility or safety rule. Every authorized manual override records actor, timestamp, reason, previous decision, new decision, and policy version.

Avoid a giant hard-coded priority enum for every industry. Use a small generic priority/constraint model plus validated policy inputs; specialized rules remain in domain adapters or explicit policy extensions.

## 5. Commands and public contracts

Names below are conceptual; the final Java types and API format must follow repository conventions after the audit.

Core commands:
- `CreateQueue`, `UpdateQueuePolicy`, `PauseQueue`, `ResumeQueue`
- `JoinQueue`, `CheckInAppointment`, `CancelQueueEntry`
- `CallNext`, `StartService`, `CompleteService`, `MarkNoShow`
- `TransferEntry`, `RequeueEntry`
- `EnqueueWorkItem`, `OfferAssignment`, `AcceptAssignment`, `RejectAssignment`, `ReassignWorkItem`, `EscalateEntry`

Every mutating command must define:
- trusted tenant context and authorization boundary;
- actor identity or explicit system actor;
- correlation/causation context where applicable;
- idempotency key for retryable externally initiated operations;
- expected version or concurrency precondition where stale writes matter;
- clear result and conflict semantics.

Do not trust a tenant ID supplied solely by an untrusted request body. Resolve tenant from the authenticated security context and verify referenced resources are accessible within that tenant.

Suggested events (names are illustrative):
- `QueueCreated`, `QueuePolicyChanged`, `QueueEntryJoined`, `QueueEntryCalled`
- `ServiceStarted`, `ServiceCompleted`, `QueueEntryTransferred`, `QueueEntryCancelled`, `QueueEntryMarkedNoShow`
- `WorkItemEnqueued`, `AssignmentOffered`, `AssignmentAccepted`, `AssignmentRejected`, `AssignmentReassigned`, `QueueEntryEscalated`

Events should include stable queue/entry IDs, tenant scope, occurrence time, policy version, and correlation/causation metadata as appropriate. Do not publish duplicate business events when an idempotent retry produces no new state transition. Use durable outbox/inbox or equivalent delivery patterns where the deployment's reliability requirements warrant them.

## 6. Concurrency, fairness, and retry guarantees

- `CallNext` must atomically select and claim at most one eligible waiting entry per successful command.
- Concurrent calls must not produce duplicate service claims; use repository-level atomicity/locking/optimistic version checks appropriate to the adapter.
- Dispatch offers must prevent two active accepted assignments for the same exclusive work item unless the source policy explicitly supports multi-resource assignment.
- Idempotent retries return the original outcome or an equivalent stable result; they must not issue duplicate tickets, create duplicate active offers, or emit duplicate transition events.
- A failed notification does not roll back a committed queue transition. Delivery retries are observable and deduplicated at the adapter boundary.
- Requeue, transfer, and reassignment preserve the audit trail and make the effective ordering policy explicit.
- Fairness is policy-driven and testable; aging may prevent starvation, but must not violate hard safety, skill, access, or contractual constraints.

## 7. Integration contracts

Adapters are optional. No core queue-domain dependency on CRM, Customer, Scheduling, Workforce, Vehicle Service, Support, Courier, HTTP, CDI, a database, a broker, or a notification vendor is permitted.

| Integration | Queue Management uses | Queue Management must not take over |
|---|---|---|
| Customer / Party | Stable party/customer references and permitted display data | Identity, customer profile, consent source of truth |
| CRM | Optional customer/service context and follow-up reference | Pipeline, opportunity, activity lifecycle |
| Scheduling | Appointment reference, service window, resource availability query | Schedule and shift lifecycle |
| Workforce | Resource/skill eligibility and assignment reference | Employee identity, employment and skill source of truth |
| Vehicle Service | Work-order/service-job reference, job class and status events via contract | Repair order, inspection and service completion rules |
| Support | Case reference and routing metadata | Support-case lifecycle |
| Courier / Cargo | Shipment, depot or dispatch-wave reference | Shipment custody and dispatch-wave lifecycle |
| Notification | Delivery requests for status/position/assignment changes | Queue state or provider delivery guarantees |

In-process application ports support modular monoliths. Distributed compositions use versioned HTTP/event contracts. Both call the same domain rules; do not fork policy logic by deployment type.

## 8. Customer and operator experiences (later milestones)

The core contract supports, but does not itself implement, these experiences:
- staff queue console and counter controls;
- customer QR check-in, web join, kiosk ticket, appointment check-in;
- privacy-safe queue position and estimated wait display;
- staff/dispatch board with skills, availability, workload, and assignment status;
- multi-channel notifications through available providers;
- operational dashboard for wait time, service time, abandonment, SLA attainment, requeue rate, throughput, and resource utilization.

ETA is an estimate with a documented method and confidence/last-updated timestamp, not a guaranteed promise. Location tracking is optional, consent/purpose-aware, and should not be required for ordinary waiting queues.

## 9. Indonesia-first operating requirements

- Bahasa Indonesia localization, configurable locale and time zone, branch-level calendars, public holidays, and local date/time/number formatting.
- QR-based check-in and low-friction web flows; WhatsApp/SMS/push are provider adapters and are only enabled when a supported integration is configured.
- Explicit handling of poor connectivity, repeated scans, delayed messages, and offline staff actions; synchronization conflicts must be visible and auditable.
- Respect accessibility and service entitlements in policy configuration.
- Minimize precise location collection; define purpose, access, retention, and audit before field-service location features are enabled.
- Branch and service-area configuration must not assume every consumer is a physical counter or uses GPS.

## 10. Security, audit, and privacy

- Tenant isolation applies to queue definitions, entries, history, metrics, and references; IDs are not authorization.
- Every staff action is authorized for the tenant, queue, branch/site, and action being performed.
- Cross-tenant reference attempts must not disclose whether another tenant's record exists.
- Record material policy decisions, manual overrides, transfer, cancellation, no-show, assignment, and reassignment with actor/reason metadata.
- Define retention and deletion/anonymization rules for customer-facing tickets and location-derived data.
- Never expose customer personal data on public queue displays; use opaque ticket tokens and authorized views.

## 11. M0 audit and implementation sequence

### M0 — Evidence and contract (current)
1. Inventory queue-like models, work-order/dispatch flows, scheduling/workforce APIs, source events, tenant/security/idempotency conventions, migration patterns, and relevant tests.
2. Record actual Maven reactor roots and dependency direction. The repository has no root `pom.xml`; `core/framework/pom.xml` is the shared parent, while capability roots such as `scheduling/pom.xml` declare their own reactors.
3. Confirm whether the smallest suitable design is a new `queue-management/` reactor or an existing capability extension; record evidence and dependency graph.
4. Define queue profiles, transition rules, public ports/events, concurrency guarantees, and adapter seams.
5. Establish exact baseline test commands and their results before implementation.

### M1 — Domain foundation
- Queue definition and versioned policy model.
- Waiting-queue entry lifecycle and deterministic policy evaluation.
- Tenant-scoped identities, explicit transitions, domain events, and unit tests.
- Idempotency/concurrency contract and repository port design; no UI/provider coupling.

### M2 — Persistence and application contracts
- Repository adapter/migrations using the repository's established persistence conventions.
- Transaction-safe call-next and entry mutations.
- Application commands/queries, authorization integration, audit and outbox/inbox as appropriate.
- Integration and migration tests.

### M3 — Dispatch coordination
- Source work-item references, eligibility/skill checks via ports, offers/accept/reject/expire, requeue/reassignment, SLA escalation.
- Explicit adapters to Scheduling and Workforce; do not import their persistence entities.
- Concurrency and retry tests.

### M4 — Customer/staff experience and integrations
- Queue console/display contracts, QR/web/kiosk entry points, appointment check-in.
- Optional Customer/CRM/Vehicle Service/Support/Courier adapters.
- Notification adapter contracts and delivery failure visibility.

### M5 — Production readiness
- Load and contention tests, tenant-isolation/security tests, migration/recovery validation, metrics/tracing/logging, operational runbooks, deployment profile tests, and compatibility checks.

Deliver work in coherent milestone PRs. Do not create one PR per small file or split a deployable service solely because a logical capability exists.

## 12. M0 acceptance checklist

- [ ] Evidence includes concrete source paths, public ports/events, POM dependency edges, tests, and migration conventions.
- [ ] Existing queue-like primitives are classified as reusable, source-owned, or not suitable for reuse.
- [ ] The chosen reactor/module shape is justified and has no reverse dependency from neutral queue core into an industry extension.
- [ ] State-transition tables and invalid-transition behavior are testable.
- [ ] Duplicate retries and concurrent call-next/assignment have explicit expected outcomes.
- [ ] Tenant isolation and trusted-tenant resolution are defined.
- [ ] Priority precedence and override audit are explicit.
- [ ] Notification failure does not corrupt queue state.
- [ ] Actual Maven test entry points and baseline status are recorded.
- [ ] No code or CI result is claimed unless changed/run and verified.

**Verification note:** This contract is a proposed architecture artifact based on initial path/POM/domain inspection. A complete code search, dependency audit, and Maven test run have not yet been completed. It must not be read as evidence that Queue Management has been implemented.


## 13. Additional repository evidence: reliability primitives to reuse

A repository-wide path inventory (7,125 tracked file entries in the current `main` tree; GitHub tree response was not truncated) found many queue-adjacent domain artifacts but no obvious existing dedicated Queue Management reactor by name. This is useful evidence, not proof that no queue behavior exists under other names.

The audit also found a platform Reliability capability that should be evaluated for reuse before any new idempotency or event-delivery infrastructure is written:

- `core/framework/reliability/pom.xml` defines `numou-reliability` with API, SPI, application, and adapter modules.
- `reliability-api` documents contracts for outbox, inbox, idempotency, retry policy, and failure classification.
- `reliability-spi/.../IdempotencyStorePort.java` defines atomic scoped key claims, result recording, and claim release.
- `reliability-application/.../IdempotentExecutor.java` provides an executor that returns recorded outcomes for completed duplicates and rejects a duplicate while the first attempt is still running.
- `core/framework/aggregate/aggregate-persistence/.../OutboxDomainEventSink.java` and the reliability adapter are additional candidates to inspect for transactional event publication.
- Existing Asset and Construction code also contain idempotency/outbox implementations. These are useful evidence for edge cases and persistence patterns, but Queue Management should not depend on a private domain adapter merely to copy those patterns.

**Design consequence:** Queue Management should use the platform Reliability public contract where its semantics fit and avoid inventing a parallel idempotency/outbox subsystem. The exact dependencies and behavior must be validated against the current reliability API and its tests. Queue-specific atomic selection/claiming remains a queue repository/concurrency concern; a generic idempotency key alone does not make `CallNext` atomic.

## 14. M0 audit status and open verification

Confirmed from source/POM inspection:
- The repository has no root `pom.xml`; reactor entry points are capability-specific and the shared parent is `core/framework/pom.xml`.
- Scheduling has separate domain/SPI/application/adapter modules, and its domain is resource-agnostic.
- Workforce, Vehicle Service, Support, Logistics, CRM, and Notification have separate capability roots or module boundaries.
- Reliability already has public contracts and implementations for idempotency and durable delivery concerns.
- Source-owned work-order, shipment, and dispatch-wave models exist and must not be re-owned by Queue Management.

Not yet confirmed:
- Complete source-level search for queue behavior under non-obvious names.
- Full dependency graph and all public contracts for the listed capabilities.
- Actual module test commands and baseline CI/test results.
- Persistence adapter selection, transaction boundaries, and migration conventions for a new queue reactor.
- Runtime security composition and tenant-context enforcement for the proposed public commands.

The M0 issue remains open until these are resolved or explicitly documented as follow-up decisions. Do not start M1 production implementation solely on the initial path inventory.

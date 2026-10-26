# Vehicle Service & Workshop Management

**Status:** Proposed capability roadmap  
**Repository:** `andalus-ai/numu`  
**Architecture baseline:** `AGENTS.md`, ADR-003, and `DOMAIN-CAPABILITY-MATRIX.md`

## Goal

Provide a production-oriented vehicle service management system for independent mechanics, multi-branch workshops, dealer service centers, fleet operators, and customers. The capability must compose with Numou's reusable platform capabilities and must not turn the neutral framework into an automotive-only framework.

This document defines the target boundaries and implementation milestones. It does not claim that the capabilities or integrations below already exist.

## Product surfaces and actors

- **Customer:** maintain vehicle profiles, request/book services, choose a location and available slot, approve estimates, track service progress, view service records, pay invoices, manage reminders, and request follow-up.
- **Workshop/provider owner or manager:** configure branches, service catalogues, operating hours, capacity, bays, staff, pricing policies, approvals, parts, suppliers, invoices, warranties, and reports.
- **Service advisor/front desk:** intake vehicles, record concerns and mileage, inspect, prepare estimates, communicate with customers, and manage handover.
- **Mechanic/technician:** view assigned jobs, record diagnosis and labor, request parts, capture inspection evidence, update job steps, and submit work for quality review.
- **Dispatcher/service manager:** balance mechanic skills, bay/equipment capacity, promised completion times, priorities, and work-in-progress.
- **Platform operator:** configure tenant/provider lifecycle, policy, integrations, audit, and operational health.

## Capability ownership

| Capability | Owns | Must not own |
|---|---|---|
| Customer and vehicle registry | Customer-facing vehicle identity, VIN/registration references, make/model/year, odometer observations, ownership/authorized-driver relationships, vehicle profile history | Workshop job state, payment ledger, document binary storage |
| Service catalogue and pricing policy | Service definitions, labor/parts requirements, durations, applicability rules, branch overrides, price revisions | Generic product catalogue or accounting ledger |
| Reservation and appointment | Booking requests, slot holds, confirmation, reschedule/cancel, booking status, customer/provider policies, idempotency | Technician execution lifecycle or duplicate calendar implementation |
| Capacity and resource scheduling | Working calendars, skills, shift availability, service bays, equipment, capacity constraints, allocation and conflict prevention | Automotive service semantics that belong in the extension |
| Workshop work order | Intake, complaint/symptom, diagnosis, estimate revision, customer authorization, job cards, work steps, assignment, progress, quality checks, completion and handover | Reservation availability, inventory stock ledger, invoice/payment ledger |
| Maintenance plans and service history | Service intervals, due calculations, mileage/time triggers, completed service records, warranty and recall references, reminders to schedule | Vehicle telematics claims unless a verified integration supplies readings |
| Parts and inventory integration | Required/reserved/issued/returned parts through the existing inventory contract; substitutions and backorders | A second stock ledger |
| Estimate, invoice and payment integration | Estimates, approval evidence, invoice references, payment/refund status through existing commercial/finance/payment contracts | A new payment processor or shadow accounting ledger |
| Customer communication | Domain-triggered notifications, templates, channel preferences, delivery attempts and status through the shared notification capability | Booking/work-order source of truth or direct provider-specific messaging in domain code |
| Workshop reporting | Operational read models for utilization, turnaround, comeback rate, workload, revenue references, parts usage and customer retention | A second authoritative write model |

These boundaries are target ownership. Before implementation, inspect the actual reactor modules and public APIs and reuse only contracts whose semantics match.

## Core workflows

### Appointment lifecycle

1. Customer selects a branch, vehicle, service, preferred time window and optional notes.
2. Availability is calculated from branch hours, service duration, required skills, bays/equipment, existing reservations, buffers and configured capacity.
3. A short-lived slot hold prevents competing requests from confirming the same capacity.
4. The customer confirms; the system records an idempotent reservation and sends a confirmation notification.
5. Reschedule, cancellation, no-show and provider-initiated changes follow explicit policy and produce auditable history.
6. A confirmed reservation can create or link to a work order without making either aggregate the owner of the other's lifecycle.

### Workshop execution

1. Check-in captures vehicle condition, odometer, customer concerns, photos/documents, and consent.
2. Initial inspection and diagnosis produce a versioned estimate.
3. Customer approval is explicit and version-bound; changing price/scope after approval requires renewed approval according to policy.
4. Work is assigned based on mechanic skills, availability, and resource capacity.
5. Parts are reserved/issued via inventory contracts; unavailable parts create an observable blocked state.
6. Mechanics record labor, findings, parts, work steps and evidence.
7. Quality control verifies required checks before completion.
8. Invoice/payment are linked by stable references; handover records completion, warranty terms and customer acknowledgement.
9. Service history and maintenance due dates are updated from accepted completion facts, not from a mere appointment or draft work order.

### Preventive maintenance and reminders

- Support both time-based and distance-based service intervals, with explicit units and provenance for odometer observations.
- Calculate due/overdue state deterministically from policy version, last accepted service and the latest trusted mileage reading.
- Deduplicate reminder scheduling and delivery using stable business keys; respect tenant and customer communication preferences.
- Do not assume vehicle telemetry, OEM recall feeds, or registration/VIN lookup services are available without an explicit adapter.

## Domain invariants and production requirements

- Tenant/provider/branch scope and authorization are checked at every application boundary; IDs alone never grant access.
- A slot cannot be confirmed beyond the capacity policy for its resources and time range. Concurrent booking attempts must not overbook a single-capacity resource.
- Slot holds expire safely; confirmation, cancellation, rescheduling, and retries are idempotent.
- Reservation, work order, estimate, invoice and payment are distinct lifecycles connected by explicit IDs and contracts.
- Customer approval identifies the exact estimate revision and scope approved.
- Completed work and service-history facts are auditable; corrections append new facts or revisions rather than silently rewriting history.
- Money uses currency-aware precision; durations and odometer readings use explicit units. No floating-point money arithmetic.
- Domain modules remain free of HTTP, CDI/runtime wiring, persistence frameworks, broker clients, cloud SDKs and provider-specific SDKs.
- External side effects use application ports and reliable outbox/inbox or equivalent delivery where warranted. Retries, terminal failures and reconciliation are observable.
- PII, vehicle identifiers, photos, inspection evidence and service records follow access policy, retention policy and audit requirements.
- Notifications are not treated as proof that the underlying business transition succeeded.
- Operational metrics identify their definitions, time windows and source basis.

## Suggested module layout

Use existing Numou module naming and reactor conventions after repository inspection; these names express ownership, not a mandate to create duplicate capabilities.

- `vehicle-domain`: vehicle profile, mileage observations and ownership/authorization relationships.
- `vehicle-application`: use cases and ports for vehicle registry.
- `service-catalog-domain` / `service-catalog-application`: service definitions, applicability and pricing revisions if no suitable existing catalogue contract exists.
- `service-reservation-domain` / `service-reservation-application`: appointment policies, slot holds, booking lifecycle and ports.
- Reuse or extend the existing neutral scheduling/resource capability for calendars, skills, bays, equipment and capacity; do not create a competing generic scheduler.
- `workshop-domain` / `workshop-application`: work orders, diagnostics, estimate approval references, job steps, inspection and handover.
- `vehicle-maintenance-domain` / `vehicle-maintenance-application`: maintenance plans, due calculations, service history and reminder intents.
- Infrastructure/runtime adapters: persistence, API, event delivery, notification, document, inventory, finance/payment and optional external data integrations, kept independently selectable.

Do not create every artifact in this list blindly. First map the actual repository and reuse the closest existing public capability contracts.

## Integration contracts

Prefer stable identifiers and public application ports/events, never another capability's persistence entities or tables. Candidate events include:

- `ServiceReservationRequested`, `ServiceReservationConfirmed`, `ServiceReservationRescheduled`, `ServiceReservationCancelled`
- `VehicleCheckedIn`, `DiagnosisRecorded`, `EstimateIssued`, `EstimateApproved`, `WorkOrderStarted`, `WorkOrderBlocked`, `WorkOrderReadyForQualityCheck`, `WorkOrderCompleted`, `VehicleHandedOver`
- `MaintenanceDueCalculated`, `MaintenanceReminderRequested`
- `PartsReservationRequested`, `PartsIssued`, `InvoiceRequested`, `PaymentStatusChanged`

Event names are proposed contract examples, not existing repository events. Final schemas must include tenant context, aggregate identity, event identity/version, occurrence time, correlation/causation and idempotency semantics as appropriate. PII should be minimized in event payloads.

## Milestone plan

### M0 — Repository audit and capability map

- Read all applicable `AGENTS.md` instructions and relevant ADRs.
- Locate the real Maven reactor roots, module conventions, tests and public APIs for identity/tenant, customer/CRM, scheduling, notification, document storage, inventory, finance/payment, audit and workflow.
- Record reusable contracts, gaps, dependency cycles and any overlapping automotive/service modules.
- Deliverable: a reviewed ownership/dependency map and explicit implementation scope. No duplicate capability is created during this audit.

**Acceptance:** every proposed dependency points to an observed public contract or is explicitly marked as a new capability; reactor/build commands are based on actual POMs.

### M1 — Vehicle registry and service catalogue

- Implement vehicle profile and ownership/authorized-user rules.
- Add explicit odometer observations with source, timestamp and unit; reject invalid regressions unless recorded as a controlled correction.
- Implement service definitions, required skills/resources, duration estimates, applicability and versioned price policies.
- Add domain tests, tenant/authorization application tests, persistence mapping and architecture dependency tests.

**Acceptance:** no HTTP/persistence dependency in domain; cross-tenant access is denied; service and price revisions preserve history.

### M2 — Reservation and capacity-safe scheduling

**Current implementation slice:** `vehicle-service-application` defines customer appointment use cases to find candidate slots and book a service. It resolves an active, tenant-scoped service offering, carries its estimated duration and resource requirements into the reservation port, and requires a tenant-scoped idempotency key. Availability is advisory; the reservation adapter must enforce capacity atomically on commit.

- Reuse the existing reservation capability as the capacity authority; do not create a second booking aggregate or ledger.
- Implement an adapter to the reservation application only after its current capacity/resource contract can enforce service-specific duration and required resource tags. The existing generic reservation API currently uses its own booking rules/resource directory and does not by itself prove those workshop-specific constraints are enforced.
- Next, implement expiring slot holds, confirm/reschedule/cancel/no-show policies, and provider API adapters according to existing conventions.
- Cover branch calendars, holidays, time zones, buffers, mechanic skills, bay/equipment requirements and concurrent booking attempts.

**Acceptance:** concurrency tests prove capacity is never exceeded; expired holds release capacity; retries do not duplicate reservations or events; integration tests prove service duration and resource tags are enforced by the reservation adapter.

### M3 — Workshop work orders and customer approvals

- Implement check-in, diagnosis, inspection checklists, versioned estimates, approval evidence, work steps, assignment, blocked states, quality review, completion and handover.
- Connect reservation, documents and customer communication through explicit contracts.
- Ensure changes to approved scope/price trigger policy-based renewed approval.

**Acceptance:** invalid transitions fail; approvals are revision-bound; work orders can exist for walk-ins without fabricating an appointment.

### M4 — Maintenance plans, history and notifications

- Implement time/mileage maintenance rules and accepted service history.
- Generate deduplicated notification intents and schedule reminders using the existing notification capability.
- Support opt-outs/preferences, delivery status, retries, and configurable channels without embedding channel SDKs in domain code.

**Acceptance:** repeated evaluation does not create duplicate reminders; only accepted completed work updates maintenance history; notification failure does not roll back completed service.

### M5 — Parts, finance and operational reporting

- Integrate parts reservation/issue/return with the existing inventory owner.
- Integrate estimates/invoices/payments/refunds using the existing finance/commercial/payment contracts.
- Add warranty/comeback tracking, mechanic utilization, workshop throughput, turnaround, parts consumption and revenue read models.
- Add reconciliation paths for partial failures and payment/inventory discrepancies.

**Acceptance:** no duplicate stock or ledger; payment retries are idempotent; reports expose metric definitions and source timestamps.

### M6 — Production hardening and rollout

- Complete authorization, tenant isolation, audit, privacy/retention, rate limits, input validation, observability, outbox/inbox, retry/backoff and dead-letter/replay procedures.
- Run the narrow affected reactor tests, persistence/concurrency integration tests, architecture tests, and dependent reactor/CI checks.
- Document deployment profiles, configuration/secrets, migrations, backup/restore and operational runbooks.
- Validate one independent workshop workflow and one multi-branch workflow end-to-end.

**Acceptance:** reproducible build/test evidence, migration and rollback plan, no unverified claims about external providers, and no accidental transitive dependency on unrelated adapters.

## Verification checklist

- [ ] Applicable `AGENTS.md` and architecture contracts reviewed.
- [ ] Actual existing modules and public contracts mapped before adding new capabilities.
- [ ] Domain tests cover invariants and invalid transitions.
- [ ] Architecture tests protect dependency direction.
- [ ] Application tests cover tenant isolation and authorization.
- [ ] Persistence tests cover transaction behavior and concurrent slot allocation.
- [ ] Idempotency and event delivery/replay tests cover all side-effecting commands.
- [ ] Customer and provider flows cover cancellation, reschedule, no-show, estimate rejection, parts delay, refund and notification failure.
- [ ] Affected Maven reactor commands and results are recorded accurately.
- [ ] External integrations are optional adapters with explicit configuration and failure behavior.

# Attendee & Participant Capability — M0 Audit and M1 Foundation Contract

- **Status:** M0 audit completed; M1 implementation boundary proposed
- **Repository:** `andalus-ai/numu`
- **Related:** `AGENTS.md`, ADR-003, `DOMAIN-CAPABILITY-MATRIX.md`, `CAPABILITY-PACKAGING-AND-COMPOSITION.md`

## 1. Objective

Provide a reusable participant profile consumed by Events, Training, Education, Membership, Workforce, Mobility and other applications. The capability identifies a participant; it does not own authentication, Party/Person master data, organization hierarchy, geographic truth, binary storage, event tickets, payment or authorization.

An attendee is not necessarily the purchaser, customer, account owner or authenticated user. One person can participate in many programs without duplicate identities.

## 2. M0 repository findings

Inspected the current `main` branch:

- Root README confirms capability-first architecture and multiple Maven reactor roots; do not assume a root Maven build.
- Root AGENTS.md requires source inspection, explicit ownership, reuse of public contracts, no speculative module creation, and no PR without explicit user approval.
- ADR-003 and the Capability Packaging and Composition Contract require deployment-neutral domain rules, inward dependency direction and explicit public contracts.
- `core/framework/pom.xml` is the framework reactor and includes foundation, event, social-graph and other framework capabilities.
- `identity/pom.xml` contains identity domain/application/adapter modules. Its domain POM describes a User aggregate and authentication-oriented values. This is not evidence of a reusable Person/Party or Attendee aggregate; do not overload authenticated User as attendee.
- `documents/pom.xml` provides document-storage SPI and local/S3/config adapters. Reuse it for profile images/documents; do not store binary content in attendee aggregates.
- `core/framework/event/pom.xml` defines event contracts/adapters. Reuse the existing event envelope/publication contract.
- `DOMAIN-CAPABILITY-MATRIX.md` assigns neutral geographic primitives to Geo and provider-specific spatial storage/adapters as extensions. It does not prove a complete branch coverage service exists.
- Default-branch code search for attendee/participant and likely Person/Party terms returned no indexed matches. This is not proof that no related implementation exists; complete source-tree inspection remains required before adding production modules.

### Ownership disposition

| Concern | Owner / decision |
|---|---|
| Authentication principal/credentials | Existing Identity; do not duplicate |
| Person/Party identity | Reuse public Party/Person contract if found; unresolved |
| Attendee/Participant profile | New reusable capability proposed |
| Organization/branch hierarchy | Audit existing Organization contract first |
| Address/geographic primitives | Reuse Geo/Address contracts |
| Coverage/territory | Separate Coverage capability/extension |
| Policy/authorization | Reuse Security/Policy contracts where applicable |
| Images/documents | Existing Documents storage |
| Event registration/ticketing/attendance | Existing Events/Ticketing where applicable |
| Relationships | Existing Social Graph |
| Notifications/workflows | Existing Notification and Workflow |

## 3. Domain boundary

The proposed capability owns participant profile metadata and lifecycle, external references to authoritative Party/Person records, participant-specific custom attributes, profile media/document references (not binary storage), and participant preferences/consent references where justified.

It does not own authentication credentials, a second Party/Person/Customer/Employee/Student master, branch hierarchy, geographic datasets, coverage, policy authoring, event registration, ticket inventory, payment, document bytes, notification delivery or social graph persistence.

## 4. M1 logical model

### ParticipantProfile

- `id`: stable capability-owned identifier;
- `tenantId`: mandatory trusted tenant scope;
- `partyReference`: optional stable reference to authoritative Party/Person owner; never a copied aggregate;
- `profileKind`: extensible classification, not a universal industry enum;
- `displayName`: participant-facing name;
- `preferredName`, `preferredLanguage`: optional;
- `status`: Draft, Active, Suspended, Archived;
- `createdAt`, `updatedAt`, `revision`: lifecycle and concurrency metadata.

Do not place date of birth, gender, nationality, precise GPS, health/accessibility details or identity-document numbers in the core aggregate by default. Sensitive extensions must be separately permissioned, purpose-bound and opt-in.

### ParticipantIdentifier

Participant ID, identifier namespace/type, opaque external reference, issuer/source and optional verification state/timestamp. Never use email, phone or mutable display name as the stable identity key.

### ParticipantAttribute

Participant ID, namespaced key, schema/version reference, typed value, visibility/classification, provenance and update timestamp. Validate schemas at the application boundary; arbitrary JSON must not become an authorization input.

## 5. Invariants

1. Tenant scope is mandatory and derived from trusted authenticated context; caller-supplied tenant IDs are not authority.
2. Every read, write, lookup, deduplication and mutation is tenant-scoped.
3. A profile may reference Party/Person but cannot mutate that owner or copy its authoritative fields.
4. Provisional profiles may exist only where a consuming workflow permits them; linking/merging requires authorization and audit.
5. Status transitions are explicit and validated.
6. Repeated commands with the same idempotency scope do not duplicate profiles or events.
7. Optimistic concurrency prevents silent lost updates.
8. Audit identifies actor/service, tenant, action, timestamp, correlation and reason where applicable.
9. PII minimization, purpose limitation, retention and erasure are explicit; consent is not blanket authorization.
10. Cross-capability references use stable IDs/public contracts, never private entities or direct table access.

## 6. M1 use cases and events

Initial use cases: CreateParticipantProfile, UpdateParticipantProfile, ActivateParticipantProfile, SuspendParticipantProfile, ArchiveParticipantProfile, GetParticipantProfile, LinkPartyReference, AddParticipantIdentifier, AddParticipantAttribute and RemoveParticipantAttribute.

Initial events: `ParticipantProfileCreated`, `ParticipantProfileUpdated`, `ParticipantProfileActivated`, `ParticipantProfileSuspended`, `ParticipantProfileArchived`, `ParticipantPartyReferenceLinked` and `ParticipantPartyReferenceUnlinked`.

Commands carry trusted tenant/actor context plus idempotency/correlation metadata. Events carry event/aggregate IDs, tenant, schema version, occurred-at, correlation/causation and actor provenance. Never put raw sensitive fields, credentials, identity documents or precise location in general-purpose event payloads. Publish through existing event/outbox contracts where supported.

## 7. Artifact boundary

Do not add Maven artifacts until complete source-tree and reactor audit confirms ownership and parent paths. If no existing capability owns this behavior, expected minimal roles are:
- domain: pure Java aggregate, values, invariants and events;
- application: use cases, ports, tenant/actor context and authorization orchestration;
- persistence/transport adapters: only when concrete storage/API requirements and runtime composition are confirmed.

Domain must not depend on Quarkus, CDI, HTTP, Hibernate, PostgreSQL, PostGIS, Kafka or storage vendors. Reusability alone does not justify a microservice.

## 8. M1 acceptance criteria

- [ ] Complete source-tree search for Party, Person, Contact, Customer and participant/attendee ownership across all reactors.
- [ ] Inspect Organization and Security public contracts and authorization integration.
- [ ] Confirm foundation ID, clock, event and result primitives.
- [ ] Implement pure Java profile and lifecycle invariants.
- [ ] Implement application commands/queries and tenant/actor context.
- [ ] Test transitions, tenant scoping, identity linking and idempotency boundaries.
- [ ] Add ArchUnit dependency-direction tests.
- [ ] Document API/event compatibility and PII classification before adapters.
- [ ] Build/test the exact affected reactor and report actual evidence.
- [ ] Update capability ownership matrix with confirmed dependencies.

## 9. Later milestones

- M2 Media & Documents: references, visibility, retention, storage integration.
- M3 Geographic profile: address/location references, source, accuracy, capture time and consent; no continuous tracking by default.
- M4 Organization & Branch: hierarchy and time-bounded participant assignments, after Organization audit.
- M5 Coverage: administrative/radius/polygon coverage, ownership, inclusion/exclusion, overlap resolution and revisions; reuse Geo and add PostGIS only in a justified adapter.
- M6 Policy & Eligibility: explainable decisions, bindings, precedence and exceptions; integrate existing Security policy authority.
- M7 Registration: event/program registration and group/guardian flows; consume Events contracts.
- M8 Attendance: check-in/out, QR/NFC/RFID adapters, offline sync and idempotency.
- M9 Relationships: consume Social Graph with privacy controls.
- M10 Analytics: derived projections with metric provenance, isolated from transactional aggregates.
- M11 Governance: delegated administration, privacy operations, retention and audit reporting.
- M12 Runtime packaging: standalone/modular/distributed composition, deployment, observability, backup/recovery and compatibility.

Every milestone begins with a source/contract audit of the capabilities it integrates. This roadmap does not authorize duplicate owners.

## 10. Open decisions

1. Which existing module owns canonical Party/Person/Contact? Current inspected evidence does not settle this.
2. Does Organization expose branch/organizational-unit contracts?
3. Which Security API supplies trusted actor/tenant context and evaluates resource/attribute policy?
4. Which persistence reactor and migration conventions should own participant storage?
5. What are the API/event compatibility conventions for this capability?

Resolve these with source evidence before creating the production Maven module. This avoids a second identity, organization or policy authority and keeps M1 durable and reusable.

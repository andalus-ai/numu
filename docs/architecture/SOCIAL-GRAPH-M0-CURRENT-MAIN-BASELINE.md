# Social Graph M0 — Current-main baseline and rebase gate

- **Status:** M0 continuation note; current-main source revalidation required before M1
- **Date:** 2026-11-04
- **Repository:** `andalus-ai/numu`
- **Branch:** `social-graph/m0-current-main`
- **Base:** `main`
- **Historical audit:** [Social Graph M0 audit on the previous branch](https://github.com/andalus-ai/numu/blob/social-graph/m0-foundation-architecture/docs/architecture/SOCIAL-GRAPH-M0-REPOSITORY-AUDIT.md)
- **Historical architecture:** [Social Graph Foundation Architecture](https://github.com/andalus-ai/numu/blob/social-graph/m0-foundation-architecture/docs/architecture/SOCIAL-GRAPH-FOUNDATION-ARCHITECTURE.md)

## 1. Why this branch exists

The previous working branch `social-graph/m0-foundation-architecture` was compared with current `main` on 2026-11-04:

- Previous branch: 9 commits ahead of `main`.
- Previous branch: 51 commits behind `main`.
- GitHub compare reported the branch's 9 commits as two documentation files only.
- Merge base: `668de78f776d98a3c92351dcd463356a63771f69`.
- Current `main` tip observed during the audit: `758a7d4ba69f68ba6be7410c8bfabdc0348bd689`.
- Previous branch tip: `470a02755746872021aac3ed01aa43227ae652a7`.

The old branch was not force-updated or rewritten. This continuation branch starts from current `main` so subsequent audit and implementation work is not based on a 51-commit-old tree.

## 2. Current repository instructions and build topology

The current `main` copy of `AGENTS.md` was re-read. It continues to require:

- capability ownership and public-contract inspection before adding code;
- real Maven reactor inspection (there is no repository-root Maven build);
- infrastructure-neutral domain code and intentional dependencies;
- explicit tenant/actor authorization, idempotency, and durable side-effect handling;
- actual test/CI evidence, with no false completion claims;
- no PR without explicit user approval.

Current Maven parent/aggregator files inspected on the previous branch are consistent with current `main`'s CI topology, but must be revalidated against the current tree before M1. The relevant reactor roots remain separate: `core/framework/pom.xml`, `identity/pom.xml`, `organization/pom.xml`, `workforce/pom.xml`, and `security/pom.xml`. The framework parent declares Java 21. Do not invoke Maven at repository root.

## 3. Current CI evidence and limitations

The current `.github/workflows/ci.yml` was inspected on `main`. It uses Java 21 and explicitly orchestrates separate reactor builds rather than a root Maven build.

Relevant existing CI paths include:

- Framework/logistics job: installs framework parent, foundation, Geo, cache, observability contracts, security contracts, and framework reactor; installs Identity with tests deferred for downstream bootstrap; installs Organization; later verifies Identity independently.
- Workforce job: installs framework parent and foundation, then runs `mvn -B -ntp -f workforce/pom.xml verify`.
- Support job: installs security and framework contracts, installs Identity contracts with tests deferred, then verifies Support.
- There is no Social Graph reactor or Social Graph-specific CI job in the inspected workflow.

This is workflow configuration evidence only. No run for this new branch has been executed, and no Maven build or PostgreSQL integration test was run in this audit session.

## 4. M0 work still required on current main

The previous audit's identity, relationship, consent, authorization, persistence, and transaction findings are useful historical leads, not automatically current-main facts. Before M1, revalidate at least:

1. Ecosystem Party and PartyRelationship domain/application/persistence contracts and migrations.
2. Party-to-authenticated-subject association and current Identity actor/tenant resolution.
3. OrganizationPartyAssociation and OrganizationMembership identifiers and lifecycle.
4. PersonRef consumers and any explicit PersonRef-to-Party mapping added since the old audit.
5. Workforce, Customer, CRM, Mobility, Logistics, Jobs, and Marketplace public subject/eligibility contracts.
6. Current security authorization APIs, consent/privacy ownership, tenant policy and data deletion/export constraints.
7. Current UnitOfWork, PgUnitOfWork, outbox append adapter, transaction/session composition, migration versioning and PostgreSQL test patterns.
8. Existing graph/Neo4j dependencies and any graph-related implementation added to current main.
9. Actual current Maven parent/dependency closure and CI baseline outcomes for affected reactors.

A checked-out repository or CI runner is required for a full `git grep`, schema inventory, Maven build, and PostgreSQL transaction proof. GitHub code-search absence is not evidence that a source symbol or use does not exist.

## 5. M1 remains gated

Do not add graph tables, relationship aggregates, or Neo4j dependencies until the current-main revalidation and transaction gate are complete.

The previous architecture's two candidate transaction paths remain hypotheses to verify, not selected implementations:

- Panache/Hibernate Reactive graph persistence with `PgUnitOfWork` and `PgOutboxStore.append` in the same active session; or
- JDBC graph persistence and transaction-aware outbox append using one caller-owned JDBC connection.

Select only one after current code and integration-test evidence. Required PostgreSQL tests remain: atomic graph/outbox commit, rollback on either write failure, duplicate event idempotency, and tenant metadata preservation.

## 6. Acceptance criteria for closing this M0 continuation

- [ ] All relevant current-main source contracts and migrations revalidated with exact paths.
- [ ] Identifier mappings and unresolved aliases documented from current code.
- [ ] Current authorization, privacy, consent and tenant policy owners identified.
- [ ] Current persistence/outbox transaction implementation selected only after compile/runtime proof.
- [ ] Exact Maven reactor commands and current CI outcomes recorded; no unrun command marked passed.
- [ ] M1 domain/API/event/migration/test slice reviewed against current-main contracts.
- [ ] No PR opened until explicitly approved.


## 7. Current-main domain contract revalidation (2026-11-04)

This section records direct source-file inspection against current `main`. It supersedes older assumptions only where stated; it does not claim a complete repository-wide identifier scan.

### 7.1 Canonical Party and Party relationships

Inspected current-main source:

- `core/framework/ecosystem/ecosystem-domain/src/main/java/tech/kayys/numou/ecosystem/domain/model/Party.java`
- `core/framework/ecosystem/ecosystem-domain/src/main/java/tech/kayys/numou/ecosystem/domain/party/PartyId.java`
- `core/framework/ecosystem/ecosystem-domain/src/main/java/tech/kayys/numou/ecosystem/domain/party/PartyRelationship.java`
- `core/framework/ecosystem/ecosystem-domain/src/main/java/tech/kayys/numou/ecosystem/domain/party/PartyRelationshipType.java`

Confirmed:

- Party remains canonical cross-domain identity for PERSON or ORGANIZATION and owns only kind/lifecycle. The source comment explicitly assigns names, contact details, organization structure and person-specific attributes to their respective profile capabilities.
- PartyId is a strongly typed UUID identifier; its UUID representation does not make it interchangeable with any other UUID identifier.
- PartyRelationship is a directed source Party -> target Party relationship with effective dates and ACTIVE/PROPOSED/TERMINATED lifecycle behavior. It rejects self-relations and invalid date ranges.
- PartyRelationshipType remains the commercial/organizational vocabulary: CUSTOMER_OF, SUPPLIER_OF, PARTNER_OF, PROVIDER_OF, MEMBER_OF, SUBSIDIARY_OF.

Decision unchanged: do not add connection/follow/block semantics to PartyRelationship or its enum. Social Graph must own a distinct social relationship lifecycle and reference PartyId only after identity resolution.

### 7.2 Authenticated identity association

Inspected current-main source:

- `core/framework/ecosystem/ecosystem-domain/src/main/java/tech/kayys/numou/ecosystem/domain/party/IdentitySubjectRef.java`
- `core/framework/ecosystem/ecosystem-domain/src/main/java/tech/kayys/numou/ecosystem/domain/party/PartyIdentityAssociation.java`
- `core/framework/ecosystem/ecosystem-application/src/main/java/tech/kayys/numou/ecosystem/application/port/PartyIdentityAssociationRepository.java`

Confirmed:

- IdentitySubjectRef is the issuer + subject pair. Its source documentation explicitly says it is never interpreted as UserId or PartyId, even if its text resembles a UUID.
- PartyIdentityAssociation is tenant-scoped and explicitly links that external authenticated subject to a Party.
- The association documentation says it establishes identity linkage only; it does not grant roles, tenant membership, or authorization.
- The public repository port exposes tenant+identity active lookup and tenant+Party lookup.

M1 actor resolution must use trusted authenticated context and this explicit association. It must perform authorization separately; successful identity linkage alone is not access permission.

### 7.3 Workforce identity remains unresolved

Inspected current-main source:

- `workforce/workforce-domain/src/main/java/tech/kayys/numou/workforce/domain/worker/Worker.java`

Confirmed:

- Worker is tenant-owned and stores a `PersonRef`, WorkerType, WorkerStatus and audit metadata.
- No PartyId is stored in the Worker aggregate contract inspected here.
- The Worker factory raises WorkerCreated with PersonRef; no mapping to Party is implied by the event.

M1 must not cast PersonRef UUID to PartyId. A validated mapping/resolver contract remains required before Workforce profiles or workers can be linked into the graph. The current pass did not complete a repository-wide persisted-column/event/API scan of PersonRef.

### 7.4 CRM owns customer and referral semantics

Inspected current-main source:

- `crm/numou-crm-domain/src/main/java/tech/kayys/numou/crm/domain/model/Customer.java`
- `crm/numou-crm-domain/src/main/java/tech/kayys/numou/crm/domain/referral/Referral.java`
- `crm/numou-crm-domain/src/main/java/tech/kayys/numou/crm/domain/relationship/AccountRelationship.java`

Confirmed:

- CRM Customer currently contains its own company/person contact fields, addresses, contact list, commercial terms and active flag. This is a domain-specific customer model, not proof of canonical Party linkage.
- CRM AccountRelationship connects AccountIds and has its own type and termination semantics. It is not a social relationship.
- CRM Referral represents a business introduction from a referrer Account to a Lead or Opportunity and has pending/accepted/rejected/converted lifecycle semantics.

Decision: Social Graph may later provide social connection context or referrals as a consumer integration, but must not take over CRM customer lifecycle, account relationships, lead/opportunity referral conversion, or copy their state into generic social edges. Any CRM-to-Party mapping must be verified before cross-linking.

### 7.5 Mobility Driver remains an operational domain subject

Inspected current-main source:

- `mobility/driver/mobility-driver-domain/src/main/java/tech/kayys/numou/mobility/driver/domain/Driver.java`
- `mobility/driver/mobility-driver-domain/src/main/java/tech/kayys/numou/mobility/driver/domain/Vehicle.java`

Confirmed:

- Driver owns onboarding verification, online/offline/on-trip state, active ride claim, rating counters and cancellation/suspension behavior.
- Driver stores operational name/phone/vehicle information in its own aggregate.
- Vehicle is a driver-domain value object with vehicle type, plate, model and color.
- The inspected Driver aggregate does not expose PartyId or PersonRef as its identity contract.

Decision: Social Graph must not become the owner of driver identity, verification, availability, ride assignment, vehicle details or operational reputation. A DriverId-to-Party mapping must be explicitly discovered or introduced by the Mobility owner before a person profile can be linked. DriverId must not be treated as PartyId.

### 7.6 Updated ownership and integration matrix

| Domain concept | Current owner / identifier | Social Graph integration rule |
|---|---|---|
| Canonical person/organization | Ecosystem Party / PartyId | Reference after validated resolution |
| Authenticated principal | Identity subject (issuer + subject) | Resolve tenant-scoped Party association; separately authorize |
| Workforce worker | Workforce / WorkerId + PersonRef | Require explicit PersonRef-to-Party resolver |
| CRM customer | CRM / CustomerId | Do not infer Party mapping from customer fields |
| CRM account relationship | CRM / AccountId pair | Keep separate from social connection |
| CRM referral | CRM / ReferralId, AccountId, Lead/Opportunity | Integrate by contract; preserve referral lifecycle |
| Mobility driver | Mobility / DriverId | Require explicit DriverId-to-Party mapping |
| Mobility vehicle | Mobility / vehicle value object | Reference only through Mobility-owned authorized views |
| Ecosystem Party relationship | Ecosystem / PartyId pair | Commercial/organizational relationship only |

### 7.7 Remaining M0 inventory gaps

The following are intentionally still open:

- Current-main OrganizationPartyAssociation and OrganizationMembership source revalidation (including exact API and persistence behavior).
- Current-main Customer/CRM account-to-Party mapping inventory and any migration/backfill contracts.
- Jobs/recruitment and marketplace subject contracts, plus Fleet/Carrier/Logistics mappings.
- Complete repository-wide PersonRef, PartyId, UserId, WorkerId, DriverId and OrganizationId usage scan, including persisted columns, events, APIs and serialized documents.
- Current-main consent/privacy/deletion/export owner and policy integration contract.
- Current-main Security authorization contract and the exact application composition point for actor Party resolution.
- Current-main persistence/outbox transaction implementation and executable PostgreSQL proof.

These are not presumed absent based on GitHub code-search results. A complete checked-out scan and affected-reactor CI evidence are still needed before M0 can be declared complete.

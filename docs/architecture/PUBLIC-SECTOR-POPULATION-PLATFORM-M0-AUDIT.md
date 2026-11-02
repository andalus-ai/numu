# Public Sector & Population Data Platform — M0 Repository Audit

- **Status:** Initial source-informed repository audit; implementation boundary and consumer tracing remain open.
- **Repository:** `andalus-ai/numu`
- **Baseline:** `main` as inspected for this milestone.
- **Guidance:** `AGENTS.md`, ADR-003, `DOMAIN-CAPABILITY-MATRIX.md`, `CAPABILITY-PACKAGING-AND-COMPOSITION.md`, and `ECOSYSTEM_VISION_AND_ENGINEERING_GUARDRAILS.md`.
- **Scope:** Government/public administration, population and citizen data, social protection, land/property, agriculture/livestock, public safety, official statistics, and cross-agency data exchange.

## 1. Executive finding

Numou already has several reusable platform capabilities that should form the foundation of a public-sector solution: foundation, identity, tenancy, organization, Geo, document storage, workflow-related capabilities, workforce, education, survey, commerce/marketplace, support, and analytics-related code.

The repository root inventory does **not** show a dedicated public-sector/government capability or a dedicated population/civil-registration, land-cadastre, social-protection registry, agriculture-production registry, or public-statistics capability. This is a repository inventory finding, not proof that no related domain concepts exist inside other modules. Those must be traced before implementation.

**Recommendation:** Add a public-sector domain family by composing existing capabilities and introducing narrowly owned registries where no suitable owner exists. Do not build a single universal government database, duplicate platform identity, or turn Numou into a BI/lakehouse engine. Keep operational authoritative records separate from derived statistical/analytical projections.

No code or schema changes are included in M0.

## 2. Repository evidence inspected

### 2.1 Repository-level inventory

The default-branch root contains these relevant capability directories:

| Area | Observed repository path | M0 interpretation |
|---|---|---|
| Platform foundation | `core/framework/foundation` | Candidate shared primitives; inspect exact Party/Person/Address contracts and consumers before reuse |
| Identity | `identity` | Existing identity capability; do not create a second login/credential/person identity system |
| Tenancy | `tenancy` | Existing tenant boundary; government jurisdiction and agency scope must not be conflated with tenant |
| Organization | `organization` | Existing organization capability; government bodies should be represented here where semantics fit |
| Geo | `geo` | Existing provider-neutral geographic primitives; no administrative-boundary dataset or spatial query adapter is claimed |
| Corporate compliance | `corporate/numou-compliance` | Potentially adjacent compliance support; not evidence of public justice/criminal-record ownership |
| Document | `document`, `documents` | Existing document lifecycle and storage areas; audit their canonical boundary before attaching evidence |
| Education | `education` | Existing education vertical; likely source for education operational records, not a population master |
| Workforce | `workforce` | Existing workforce/HRIS capability; do not duplicate workforce employment records |
| Survey | `survey` | Existing survey domain and adapters; potential data-collection instrument, not a canonical registry |
| Support | `support` | Existing support capability; may support citizen service cases only through a verified public contract |
| Commerce marketplace | `commerce/marketplace` | Existing commercial marketplace; not a government registry or agricultural production system |
| Analytics | `common/analytics` | Existing analytics domain subtree; scope and readiness are not yet established as an official-statistics capability |
| Finance | `finance` | Existing financial capabilities; grants/benefits/payment execution should integrate rather than duplicate ledgers |
| Workflow / scheduling / queue | `core/framework`, `scheduling`, `queue-management` | Candidate reusable process and service-delivery capabilities; inspect exact public contracts before composition |

Repository inventory also confirms multiple Maven reactor roots and no root-level `pom.xml`. Any later build verification must use the affected reactor(s), not assume a root build.

### 2.2 Geo capability — confirmed limits

`geo/README.md` documents a provider-neutral Geo domain with validated WGS84 points, spherical distance, geographic bounds, radius predicates, angular grid bucketing, and encoded polyline support.

The same README explicitly says Geo does **not currently provide**:
- stored-location query APIs;
- topology-aware polygon operations;
- administrative-boundary datasets;
- PostGIS adapters;
- address validation/geocoding or road routing.

**Decision:** Reuse Geo's validated primitives where appropriate, but add an independently versioned administrative geography dataset and a proper polygon/spatial-query capability only after the spatial contract, storage, completeness, and jurisdiction requirements are specified. Do not use angular grid cells as authoritative administrative boundaries or as a complete polygon-query solution.

### 2.3 Organization and identity

The repository contains separate `organization` and `identity` reactors. This supports the intended composition boundary:
- agency/ministry/municipality/village office is an Organization subtype/classification, not a new identity provider;
- a citizen/person identity reference must reuse the canonical foundation/person contract after source tracing;
- a user account or authentication subject is not automatically a citizen record;
- government hierarchy, legal jurisdiction, agency delegation, and tenant isolation are distinct relationships.

The exact Party/Person/Address types, extension mechanisms, relationship lifecycle, and public application contracts still need a targeted source-and-consumer trace in M0 follow-up. Do not invent package names or schema contracts before that trace.

### 2.4 Existing survey and analytics code

The repository contains `survey/survey-domain` and `common/analytics/domain`. These are promising reusable areas but do not, by directory existence alone, prove:
- survey sampling/weighting and statistical methodology;
- data lineage and official-indicator governance;
- privacy-preserving aggregation;
- historical, reproducible statistical releases;
- a cross-agency data exchange or data-quality reconciliation framework.

These are explicit audit questions for the next source-tracing pass.

## 3. Capability ownership proposal

The following is the proposed ownership model, subject to consumer and schema verification.

| Proposed capability | Owns | Must not own |
|---|---|---|
| Public Administration | Public agency profiles, administrative mandates, jurisdiction assignments, service catalog and inter-agency delegation references | Authentication, general Organization identity, tenant implementation, every domain registry |
| Administrative Geography | Versioned administrative units, parent-child hierarchy, boundary revisions, validity periods, official codes, geometry dataset metadata | Citizen identity, land title, generic address book, routing |
| Population & Civil Registration | Person/citizen registration references, household membership, birth/death/marriage/divorce and vital-event registration, identity corrections and source evidence | Login credentials, workforce identity, social-benefit eligibility rules |
| Household & Social Protection | Household assessments, socioeconomic observations, program enrollment, eligibility decisions, benefit cases and appeal history | General person master, payment ledger, poverty as a permanent person attribute |
| Land & Property Registry | Parcel/land-unit identifiers, cadastral geometry references, tenure/rights/interests, ownership/claim history, encumbrances, valuation references and disputes | General Geo primitives, all property listings, citizen master |
| Agriculture & Livestock Registry | Farm/holding profiles, plots cultivated, crops, livestock populations, breeding records, production cycles, harvest/output and disease/inspection observations | Generic product catalog, generic stock ledger, payment or shipment lifecycle |
| Public Safety & Justice Records | Restricted case/incident references, legally governed justice workflow references, dispositions and retention rules where legally authorized | Public citizen profiles, open-ended criminal labels, universal surveillance |
| Official Statistics & Indicators | Statistical definitions, classifications, methodology/version, source lineage, aggregate measures, release snapshots, revisions and suppression rules | Operational source-of-truth registries, a replacement lakehouse or general BI engine |
| Government Data Exchange | Source/target contracts, dataset registration, schema mapping, controlled ingestion, reconciliation, delivery status and provenance | Direct writes to another capability's private tables |

Capabilities may be grouped into a smaller number of Maven reactors where boundaries remain clear. Do not create a separate microservice or database for every row by default.

## 4. Reuse and integration boundaries

| Existing capability | Reuse direction | Verification still required |
|---|---|---|
| Foundation Party/Person/Address | Stable references for people, parties, addresses and relationships | Exact public types, custom attributes, effective dating, identity resolution and duplicate detection |
| Organization | Agencies, ministries, local governments, public institutions and delegated offices | Classification model, organizational hierarchy, jurisdiction model, external identifiers |
| Identity + Tenancy + Security | Authenticated users, tenant context, policy enforcement and access boundaries | Agency-to-tenant mapping, multi-agency users, delegated access, field-level policy and audit integration |
| Geo | Coordinates, bounds and geometry primitives | Polygon/topology semantics, spatial persistence, administrative dataset import and versioning |
| Document(s) | Certificates, land evidence, survey evidence, decisions and attachments | Canonical storage SPI, access policy, versioning, retention, legal hold and integrity checks |
| Workflow / Scheduling / Queue Management | Applications, review, inspections, appeals, field visits and citizen-service queues | Public contracts, authorization, durable persistence, deadlines and escalation semantics |
| Survey | Census/survey collection and enumerator workflows | Sampling design, response privacy, offline collection, verification, weighting and source lineage |
| Education / Workforce | Their respective authoritative education and employment records | Cross-capability identifiers, consent/legal basis, historical records and reporting views |
| Finance / Payment | Benefit disbursement, fees, grants and public payment flows where supported | Program funding, payment reconciliation, separation of eligibility from payment execution |
| Analytics | Candidate shared statistical/read-model primitives | Metric governance, aggregation, privacy controls, reproducibility and supported deployment modes |

No capability should access another capability's private tables or persistence entities. Integrations use public application ports in-process and versioned API/event/data contracts across deployment boundaries.

## 5. Critical data-model distinctions

Do not reduce the platform to one mutable citizen row with columns for poverty, land, criminal history, fertility, employment and farming.

The domain must distinguish:

1. **Identity/master records:** person, party, household, organization, parcel and administrative unit.
2. **Legal registrations:** birth, death, marriage, divorce, land title/rights and other official acts.
3. **Relationships:** household membership, guardianship, parcel interests, agency jurisdiction and program enrollment.
4. **Time-bounded observations:** income, housing condition, crop output, livestock count, health/demographic survey observations and employment status.
5. **Assessments and decisions:** poverty/social eligibility, case outcomes, inspection decisions and appeals.
6. **Operational events:** birth registration, harvest, livestock movement, benefit payment, title transfer and service application.
7. **Derived indicators:** poverty rate, fertility rate, birth rate, land ownership concentration, crop yield and livestock production statistics.

Every observation/decision/event must preserve applicable source authority, source record reference, geographic scope, event/effective time, recorded time, methodology or schema version, verification status, legal basis/purpose, correction/supersession history and provenance. Not every field belongs on every record; define typed contracts per domain.

A statistic is not an authoritative fact about an individual. For example, a poverty rate is a population/geography/time-period indicator, while a household assessment is a restricted, revisable case record.

## 6. Security, rights and governance requirements

This platform will handle highly sensitive personal, property, socioeconomic, biometric-adjacent, health/demographic and justice-related data. Security and lawful purpose are foundational acceptance criteria, not a later hardening milestone.

Required design controls:
- purpose-bound access and least privilege, including field/record-level policy where necessary;
- agency and jurisdiction scoping independent of SaaS tenant boundaries;
- explicit delegation and separation of duties;
- legal basis and permitted-purpose metadata for each collection/use/share;
- data minimization, retention schedules, archival, legal hold and verified deletion where legally permitted;
- immutable or tamper-evident access and administrative audit trails;
- data-subject correction, dispute, appeal and rectification workflows;
- source verification, duplicate resolution and provenance;
- encryption in transit and at rest, key separation and controlled export;
- privacy-preserving aggregation, minimum-cell suppression and re-identification risk review for statistics;
- controlled cross-agency sharing with contract version, approval, recipient, purpose, fields, time window and delivery audit;
- no sensitive personal data in logs, metrics, traces, URLs or broad search indexes by default.

**Criminal/justice data:** never model a person as simply having a public `criminal=true` flag. Any justice record must be restricted, purpose-limited, legally classified, status-aware (allegation, investigation, charge, adjudication, appeal, expungement/rehabilitation as applicable), and protected against stale or misleading interpretation. M0 does not assert which specific legal record categories Numou may lawfully process.

**Birth/fertility/demographic data:** distinguish vital registration from survey-derived fertility estimates and health records. Do not expose sensitive individual records through statistical dashboards.

## 7. Indonesia-first localization boundary

Indonesia is the first implementation profile, while the core remains jurisdiction-neutral.

Candidate administrative hierarchy:
- national;
- province;
- regency/city;
- district (*kecamatan*);
- village/urban village (*desa/kelurahan*);
- optional lower local units where supported by the relevant official dataset.

Do not hardcode a fixed hierarchy depth or assume administrative boundaries never change. Store official codes, source authority, dataset release/version, validity interval, parent relationship history and boundary geometry reference. Preserve historical jurisdiction assignment for events and records.

Candidate integrations to assess (not yet verified or authorized):
- Dukcapil / civil registration;
- BPS / official statistics;
- ATR/BPN / land administration;
- relevant ministries and provincial/regency/city systems;
- agriculture, livestock, fisheries, food-security and social-protection agencies.

These are integration candidates only. Do not claim access, API compatibility, permission, data-sharing authority or a government partnership until official documentation, legal authority, interface contracts and credentials are verified.

Indonesian classifications, official codes, terminology, language, address conventions, calendar/time-zone handling and legal rules belong in versioned jurisdiction packs/adapters where appropriate, not as irreversible assumptions in neutral domain models.

## 8. Operational registry versus statistical intelligence

Maintain two deliberately separate planes:

### A. Operational authoritative registries
Each registry owns validated commands, legal/operational lifecycle, authorization, correction, source evidence, revision history and transaction boundaries. It is the authoritative source only for the records it is legally/operationally designated to own.

### B. Government data intelligence
A separate read/analytics plane consumes approved events and datasets to produce historical snapshots, reconciled datasets, indicators, geographic aggregation, quality measures, forecasts and governed statistical releases.

The intelligence plane must be rebuildable from approved sources where possible, record source watermarks and methodology versions, and never silently become a write path into operational registries. External warehouses/lakehouses may be connected through stable data contracts; Numou should not duplicate or mandate another ecosystem product's warehouse engine.

## 9. M0 gaps and next source-tracing work

The following checks remain open before selecting the first implementation reactor:

1. Trace foundation Party/Person/Address and custom-attribute contracts, source packages, tests, migrations and all consumers.
2. Trace Organization domain/application/SPIs, classification and hierarchy support, public-sector suitability, persistence and tenant semantics.
3. Trace Identity, Tenancy and Security policy enforcement, including agency/jurisdiction authorization and audit extension points.
4. Inspect Geo spatial contract M7, identify whether it has advanced beyond the README's stated limitations, and trace candidate PostGIS/storage work and import pipelines.
5. Inspect Document/Storage canonical provider boundary, retention/versioning and evidence authorization.
6. Inspect Survey source and adapters for census-scale collection, offline capture, sampling and privacy support.
7. Inspect `common/analytics` source, build dependencies and consumers; distinguish shared analytics primitives from official statistics and lakehouse concerns.
8. Trace Education, Workforce, Support, Workflow, Queue and Finance contracts that may be consumed by public-service workflows.
9. Search the full repository for existing land, cadastre, property, social protection, population, civil registration, agriculture, livestock, fisheries, crime/justice, census, statistical-indicator and administrative-boundary models, including embedded modules and migrations.
10. Inspect Maven reactor POMs, architecture tests, CI workflows and database migration conventions for the candidate owners.
11. Define data classification, lawful-purpose matrix, agency/jurisdiction model, retention policy and threat model with domain/legal stakeholders before sensitive schemas or integrations are implemented.

## 10. M0 decision and M1 entry criteria

M0 recommends the following first implementation order, conditional on the source trace:

1. **M1 — Public administration and jurisdiction foundation:** public-sector Organization classification/relationships, jurisdiction/delegation contracts and administrative geography dataset/versioning boundaries. Reuse Organization, Tenancy, Security and Geo rather than duplicating them.
2. **M2 — Population and civil registration:** only after Party/Person contract and identity-resolution ownership are confirmed.
3. **M3 — Household and social protection:** household relationships, assessments, eligibility, program enrollment and appeal lifecycle, separate from payments.
4. **M4 — Land and property registry:** parcel identity, rights/interests, provenance, spatial references, title/claim revisions and dispute lifecycle.
5. **M5 — Agriculture and livestock registry:** farm/holding, crop/livestock, breeding, production and traceability, integrated with existing Product/Inventory/Marketplace only where semantics match.
6. **M6 — Data exchange and official statistics:** versioned ingestion, lineage, data-quality reconciliation, indicator definitions, historical releases and privacy-preserving aggregation.
7. **Later domain packs:** education, employment, health/demographic statistics and restricted public-safety/justice workflows, each with its own lawful-purpose and access review.

M1 must not start until:
- existing Organization, Party/Person, Identity, Tenancy, Security and Geo contracts have been traced to source and consumers;
- no existing capability already owns the proposed invariant;
- agency hierarchy, legal jurisdiction and tenant isolation are explicitly distinguished;
- administrative-boundary source, license, versioning and historical semantics are agreed;
- authorization, audit, retention and data-sharing requirements are reviewed;
- the selected Maven reactor and compatibility/test plan are identified.

## 11. Verification record

- Repository root and selected capability directories were inventoried through GitHub repository contents.
- `AGENTS.md`, the ecosystem guardrails, ADR-003, capability packaging contract, domain capability matrix, Geo README, capability architecture audit and Agriculture Marketplace M0 audit were inspected.
- No source-code changes, migrations, builds or tests were performed in this M0 pass.
- The code-search connector returned no matches for targeted terms; this is not treated as proof that the concepts do not exist. Directory/source tracing remains required.
- No external government integration was called or validated.
- This document is an initial architecture audit, not a legal opinion, production-readiness certification, or proof of complete repository coverage.

## References

- `AGENTS.md`
- `docs/ECOSYSTEM_VISION_AND_ENGINEERING_GUARDRAILS.md`
- `docs/architecture/ADR-003-reusable-capability-modules.md`
- `docs/architecture/DOMAIN-CAPABILITY-MATRIX.md`
- `docs/architecture/CAPABILITY-PACKAGING-AND-COMPOSITION.md`
- `docs/architecture/CAPABILITY-MODULE-ARCHITECTURE-AUDIT.md`
- `geo/README.md`
- `docs/architecture/AGRICULTURE-MARKETPLACE-M0-AUDIT.md`

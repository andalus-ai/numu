# Indonesian Construction Consultancy — M0 Repository Audit

- **Status:** Source-backed baseline audit; findings are limited to inspected files and require broader consumer/runtime verification.
- **Repository:** `andalus-ai/numu`
- **Related roadmap:** [Indonesian Construction Consultancy and Sharia Istisna' Roadmap](INDONESIA-CONSTRUCTION-CONSULTANCY-SHARIA-ROADMAP.md)
- **Architecture contracts:** [AGENTS.md](../../AGENTS.md), [ADR-003](ADR-003-reusable-capability-modules.md), [Domain Capability Matrix](DOMAIN-CAPABILITY-MATRIX.md)

## Purpose

Establish a truthful implementation baseline before adding Indonesian consultancy, regulatory-compliance, or Sharia istisna' behavior. The roadmap is target direction, not proof of implemented features. This audit inspected the construction reactor POMs, selected Construction domain/application/SPI classes, the capability matrix, and the existing architecture audit. It is not a full module inventory, legal opinion, runtime-composition review, or database migration audit.

## Confirmed baseline

### 1. Construction already has a bounded-context reactor

The tracked `construction/pom.xml` defines four modules: `construction-domain`, `construction-spi`, `construction-application`, and `construction-adapter`. The domain POM depends on neutral Foundation, Quantity, and Project contracts. The application layer consumes Construction domain/SPI and neutral Project/Scope contracts. This is consistent with the intended extension direction; do not move Indonesian rules into neutral Project, Scope, or Quantity.

The Construction reactor description lists many intended subdomains (including BOQ, progress, variations, contracts, payment certificates, HSE, quality, design, and closeout). A module description is not evidence that each lifecycle is fully implemented or production-ready.

### 2. Existing contract aggregate is a minimal lifecycle, not an istisna' contract model

Inspected source: `construction/construction-domain/src/main/java/tech/kayys/numou/construction/domain/contract/ConstructionContract.java`, `ContractParty.java`, and `ContractValue.java`.

Observed behavior:
- Contract records project, number, title, client, contractor, original value/currency, and status.
- Activation is allowed from DRAFT or SUSPENDED.
- Closing changes status to CLOSED.
- The inspected aggregate has no explicit signed contract-version/specification snapshot, delivery schedule, installment schedule, variation authorization history, acceptance/defects workflow, Sharia review evidence, or parallel-istisna' relationship.

These are gaps in the inspected aggregate, not proof that no related behavior exists elsewhere. Before extending it, trace all contract consumers, persistence adapters, events, and migrations. Prefer a new explicit contract-profile model or compatible extension over changing the meaning of existing contracts for every project.

### 3. Progress measurement domain has a lifecycle, but the inspected integration contract is insufficient for downstream approved evidence

Inspected source:
- `construction/construction-domain/src/main/java/tech/kayys/numou/construction/domain/progress/ProgressMeasurement.java`
- `construction/construction-domain/src/main/java/tech/kayys/numou/construction/domain/progress/event/ProgressMeasurementApproved.java`
- `construction/construction-application/src/main/java/tech/kayys/numou/construction/application/progress/handler/RecordProgressMeasurementHandler.java`
- `construction/construction-application/src/main/java/tech/kayys/numou/construction/application/progress/command/RecordProgressMeasurementCommand.java`
- `construction/construction-spi/src/main/java/tech/kayys/numou/construction/spi/progress/ProgressMeasurementRepository.java`

Observed behavior and gaps:
- The aggregate supports DRAFT → SUBMITTED → APPROVED, with rejection from SUBMITTED.
- A measurement includes project, BOQ, BOQ item, measurement date/type, quantity/unit and remarks.
- The approval event carries measurement ID, project ID, and BOQ item ID, but not the quantity/unit, measurement date/type, tenant, actor, approval decision metadata, or correction/supersession reference.
- The record command does not carry an explicit idempotency key or trusted tenant/actor context.
- The inspected repository SPI offers project/item lookups but does not express tenant-scoped access.
- The inspected record handler saves the aggregate and publishes its pulled events as separate application operations. Atomic persistence + event publication/reliable outbox composition must be verified at adapter/runtime level.
- The earlier architecture audit (AUD-022/AUD-023 and progress-evidence findings) also records a missing approval command path and missing concrete repository/transaction composition in the inspected tree at the time of that audit. Re-verify those findings against current main before implementing; this document does not assume those files remain absent.

Consequences:
- Do not create a Planning progress consumer from the current approval event by guessing quantities, percentages, actor, tenant, or measurement date.
- A future versioned approved-evidence contract must carry immutable source evidence and trusted context, define correction semantics, and have an idempotency scope.
- Keep the approved measurement, Planning progress observation, payment certificate, and cash payment as distinct facts and lifecycles.

### 4. BOQ approval and contract values are not enough to establish Indonesian regulatory compliance

The inspected BOQ aggregate has draft/review/approved status and a revision counter. The inspected contract value uses `BigDecimal` and `Currency`, rejecting negative values. These are useful foundations but do not by themselves provide:
- a versioned regulatory obligation register;
- applicability rules and effective dates;
- SBU/SKK validity and renewal evidence;
- permit/building approval evidence (where applicable);
- construction safety, quality, environmental, labor, procurement, tax, privacy, or records-retention checks;
- consultant independence and reviewer separation;
- immutable links between a compliance decision and the exact submitted document/revision.

The Indonesia profile should be data-driven and source-versioned. A checklist item must be marked applicable/not applicable with rationale and reviewer; it must not imply that Numou grants a permit or certifies a regulated professional.

### 5. Consultancy practice needs to remain distinct from contractor execution

The inspected Construction reactor is broad and contains project delivery concepts. The files inspected in this pass do not establish a dedicated consultant-engagement lifecycle with proposal/ToR/KAK, deliverable schedules, fee basis, discipline review gates, consultant submissions, conflict declarations, and client acceptance. Before creating one, inventory existing CRM, contract, document, workflow, timesheet, billing, and project capabilities and compose their public contracts instead of duplicating them.

A consultant's review/approval must not be automatically treated as contractor self-acceptance. The workflow must represent organization/role separation and record the evidence supporting each decision.

## Regulatory source baseline

This is an initial source list, not a complete or current legal determination:

- [Law No. 2 of 2017 on Construction Services — BPK legal database](https://peraturan.bpk.go.id/Details/37637/uu-no-2-tahun-2017). The BPK page records amendments including the Job Creation legislation; implementation must verify the currently consolidated law.
- [Government Regulation No. 22 of 2020 — BPK legal database](https://peraturan.bpk.go.id/Details/137561/pp-no22-tahun-2020), concerning implementation of the Construction Services Law.
- [Government Regulation No. 14 of 2021 — BPK legal database](https://peraturan.bpk.go.id/Details/161844/pp-no-14-tahun-2021), amending PP 22/2020. The BPK page marks the regulation as in force, but a complete legal update check is still required.
- Current OSS, Ministry of Public Works/JDIH, LPJK, procurement, building approval, safety, environment, tax, labor, privacy, and record-retention requirements must be separately verified by service type, project type, funding source, location, and effective date.

For Sharia governance, validate the applicable DSN-MUI istisna' and parallel-istisna' fatwas and current interpretations with a qualified Sharia adviser before treating a template or workflow as compliant. The software should record which reviewed policy/template version was used, reviewer identity/authority, decision date, exceptions, and evidence.

## M0 acceptance and implementation gates

Before M1–M3 runtime work begins:

1. Inventory the complete Construction domain/application/SPI/adapter package tree, migrations, tests, public events, and actual runtime composition.
2. Trace current contract, BOQ revision, variation, payment-certificate, progress, document, approval, finance/accounting, and audit consumers.
3. Verify the status of the previously documented progress approval and persistence/outbox gaps against current main and CI.
4. Identify existing reusable CRM/organization, workflow/approval, document, identity/authorization, finance, procurement, and audit contracts.
5. Build a regulatory obligation source register with jurisdiction, source URL, provision, publication/effective dates, supersession status, applicability dimensions, reviewer, and review date.
6. Have Indonesian construction/legal professionals review regulatory mappings and a qualified Sharia adviser review the istisna' model before any “compliant” product claim.
7. Record the exact Maven reactor commands and CI evidence for the affected modules; do not report tests as passing until the output confirms it.

## Proposed next implementation milestones

- **M1 — Consultancy engagement and deliverable lifecycle:** introduce only the missing domain/application contracts after reuse inventory; include tenant/actor authorization, versioned scope, deliverable evidence, reviewer separation, idempotent commands, and tests.
- **M2 — Indonesia compliance profile:** versioned obligation and evidence register, applicability decisions, expiry/reminder scheduling through shared workflow/notification contracts, audit history, and exports.
- **M3 — Sharia istisna' profile:** explicit reviewed contract profile, signed specification/price/delivery/payment snapshots, variation and acceptance controls, defect handling, and separate contracts for parallel istisna'.
- **M4 — Commercial and finance integration:** connect approved progress/acceptance evidence to invoicing/payment eligibility without conflating earned, certified, invoiced, and paid values.
- **M5 — Field operations and closeout:** connect site diary, RFI/submittal, QA/QC, HSE, handover and warranty evidence through existing document/workflow capabilities.
- **M6 — Production readiness:** tenant-isolation/authorization tests, concurrency and idempotency tests, migrations/rollback, observability, access-controlled audit export, backup/recovery, and reviewed legal/Sharia policy versions.

Each milestone should be a coherent PR, not a PR for every small edit. Runtime changes must include domain tests, application authorization tests, persistence/outbox integration tests where applicable, and architecture checks for dependency direction.

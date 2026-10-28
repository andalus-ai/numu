# Indonesian Construction Consultancy and Sharia Istisna' Roadmap

- **Status:** Proposed implementation roadmap; not a statement that the capabilities below are already implemented.
- **Repository:** `andalus-ai/numu`
- **Architecture alignment:** [AGENTS.md](../../AGENTS.md), [ADR-003 — Reusable capability modules](ADR-003-reusable-capability-modules.md), [Domain capability ownership matrix](DOMAIN-CAPABILITY-MATRIX.md)
- **Principle:** reusable construction/consultancy core + versioned Indonesia compliance profile + optional Sharia contract profile.

## Outcome

Support Indonesian construction consultants and project owners across feasibility, planning/design, tender/procurement support, construction supervision, project management, testing/inspection, commissioning, and handover. Provide auditable compliance evidence and an optional Sharia contract workflow where a construction deliverable is procured under an akad istisna'.

This roadmap is a product/engineering plan, not legal, engineering, tax, or Sharia advice. Each regulated checklist and contract template must be reviewed by qualified Indonesian construction/legal professionals and a qualified Sharia adviser before being represented as compliant.

## Capability boundaries

| Capability | Owns | Must not own |
|---|---|---|
| Neutral project management | Project lifecycle, WBS, work packages, activities/dependencies, schedule baselines, assignments, progress observations, risks, issues, decisions | Indonesian permits, construction-only terminology, or Sharia contract rules |
| Construction delivery extension | Site/work packages, drawing and specification registers, BoQ, quantity take-off, cost estimates, RFIs, submittals, inspections, non-conformance, tests, variations, claims, progress certificates and handover | Generic identity/workflow/document storage already owned by shared capabilities |
| Consultancy practice | Client engagements, scope/ToR, proposal and fee schedules, deliverables, review gates, discipline/role assignments, timesheets/expenses where enabled, submissions and acceptance | Contractor execution assumptions; consultant supervision must remain distinct from contractor self-acceptance |
| Indonesia compliance profile | Versioned obligations, applicability decisions, evidence requirements, responsible parties, due dates, review/approval, source and effective dates | Hard-coded global assumptions or a claim that software itself grants approval/certification |
| Contract and commercial terms | Contract versions, scope/specification snapshots, price/quantity basis, milestones, change control, notices, acceptance and dispute records | Legal advice or implicit alteration of signed terms |
| Sharia istisna' extension | Contract-specific specification and manufacturing/construction obligations, agreed price, delivery/acceptance, payment schedule, change/defect handling and Sharia review evidence | Assuming every construction project uses istisna', or silently converting an ordinary works contract into istisna' |
| Shared finance/procurement/documents/audit | Reuse public capability contracts for accounting, supplier processes, immutable document revisions, approvals, and audit trails | Duplicated ledgers or direct access to another capability's private persistence model |

A capability may run in-process in a modular monolith or behind a versioned API in a distributed deployment. Do not split it into a service merely because it has a separate name.

## Indonesian consultant workflows

1. **Firm and personnel readiness:** record NIB/OSS business identity where applicable, relevant business classifications and SBU, consultant discipline/subclassification, SKK and personnel competency evidence, validity periods, responsible roles, and renewal reminders. Model requirements by legal entity, service type, qualification, and applicable regulation version.
2. **Opportunity and engagement:** qualification check, client/owner details, tender/RFP and Kerangka Acuan Kerja (KAK)/Terms of Reference, conflict-of-interest declaration, proposal, scope exclusions, fee basis, deliverables, milestones, and approval.
3. **Planning and design:** survey inputs, assumptions, design-basis register, discipline coordination, drawing/specification revisions, technical review, estimate/BoQ revisions, risk register, and client approvals.
4. **Procurement and construction supervision:** tender clarification, submittals, RFIs, site instructions, inspection/test plans, site diary, progress observations, non-conformance/corrective action, safety/environment evidence, and independent review/approval gates.
5. **Change and commercial control:** proposed variation, cause and impact analysis, updated scope/specification/BoQ/schedule/cost, contract authority, approval before baseline replacement, and immutable history of previous baselines.
6. **Completion and handover:** test and commissioning evidence, punch list, as-built drawings, manuals, warranties, training, acceptance record, defects-liability obligations, final account, and handover dossier.
7. **Close-out and experience:** lessons learned, project references/experience evidence, consultant deliverable archive, retention schedule, and exportable audit package.

Support Bahasa Indonesia and English labels, Asia/Jakarta timezone defaults with explicit project timezone, Indonesian Rupiah (IDR) monetary precision, Indonesian date/number formatting, metric units, and configurable tax/accounting integrations. Do not bake a tax rate or legal retention period into domain code.

## Compliance register and rule governance

Represent each obligation as data with at least:
- stable obligation ID and topic;
- jurisdiction, authority, source URL/reference, instrument number, article/section where known;
- publication/effective/expiry dates and superseding/superseded relationship;
- applicability predicate and required evidence type;
- responsible role, due/event trigger, status, reviewer, decision and audit history;
- confidence/review state, last legal review date, and next review date.

Compliance checks should return **applicable / not applicable / needs review / evidence missing / evidence expired**, with explanations. Do not label a project “legally compliant” merely because a checklist is green. Changes to rule versions must not rewrite historical decisions; preserve the version used when a decision was made.

Initial source register to validate before implementation and at every legal-content release:
- Law No. 2 of 2017 on Construction Services, as amended and implemented by current applicable legislation.
- Government Regulation No. 22 of 2020, as amended by Government Regulation No. 14 of 2021, on implementation of the Construction Services Law.
- Government Regulation No. 5 of 2021 on risk-based business licensing, with current amendments and OSS implementing rules verified before use.
- Government Regulation No. 16 of 2021 on implementation of the Buildings Law, including PBG and SLF workflows where relevant.
- Current Ministry of Public Works/LPJK rules and official systems relevant to SBU, SKK Konstruksi, business classifications/subclassifications, and construction-service experience records.
- Procurement-specific rules only when the project/customer/procurement route makes them applicable; public procurement and private procurement must not share an assumed identical rule set.
- Other applicable requirements: spatial planning, environmental approvals, occupational safety and health (K3), building/technical standards, labor, tax, personal-data protection, and local-government requirements, determined by project type and location.

Use official JDIH/BPK/Ministry sources as the canonical evidence source where available. Legal status and applicability must be checked by a qualified reviewer; a source list is not itself a complete compliance opinion.

## Sharia extension: akad istisna'

Treat istisna' as a contract policy and lifecycle extension, not a new construction project model. The ordinary construction scope, BoQ, schedule, quality evidence and handover remain reusable.

The contract record should snapshot and version:
- contracting parties and authorized signatories;
- the object to be manufactured/built, measurable specifications, drawings, quantity/capacity, quality, and agreed delivery/acceptance criteria;
- agreed price and currency, payment terms and milestones, and which obligations become due on which verified events;
- delivery date/place or a sufficiently defined schedule, inspection, rejection/cure and defect obligations;
- variation rules that require explicit approval and record impacts on scope, price and time;
- delay, force-majeure, termination, dispute resolution, and other remedies as reviewed contract clauses;
- whether parallel istisna' is used, keeping the end-customer contract and the separate supplier/manufacturer contract distinct;
- Sharia review status, reviewer, findings, approved template/version, exceptions and supporting evidence.

Suggested lifecycle: draft → specification/price review → legal and Sharia review → authorized signing → controlled execution → inspection/acceptance → settlement/close-out, with explicit exception and dispute paths. Do not automatically infer contract validity from lifecycle state.

**Financial and accounting invariants**
- Preserve the signed agreed price and approved changes as immutable contract revisions; never silently reprice a signed contract when estimates or market rates change.
- Separate contract price, earned/progress amounts, accepted deliverables, invoices, cash receipts/payments, retention, advances, taxes, and accounting recognition. They are related but not interchangeable.
- Link payment eligibility to the agreed contract terms and verified evidence; support configurable advance/progress/milestone/acceptance arrangements rather than forcing one universal schedule.
- Use the shared Finance capability through its public contract. Do not implement a second ledger inside construction or the istisna' extension.
- Idempotently process approvals, payment instructions and events. Maintain actor, tenant, correlation/idempotency key, timestamps, before/after revisions and reason codes.
- Route late delivery, defects, disputed quantities, rejected work, unapproved variations and payment disputes to explicit review/claim workflows; do not auto-apply interest, penalties or compensation without contract-specific legal and Sharia review.

Reference starting points for Sharia review include DSN-MUI Fatwa No. 06/DSN-MUI/IV/2000 on sale by istishna' and Fatwa No. 22/DSN-MUI/III/2002 on parallel istishna'. Confirm authoritative text, current status, scope, and applicability with qualified advisers before encoding controls or generating contract templates.

## Milestones (PRs by milestone, not every small edit)

### M0 — Repository audit and baseline
- Inspect relevant architecture ADRs, construction/project/WBS/BoQ capabilities, finance, procurement, workflow, documents, audit and existing tests.
- Map current implementation against this roadmap; list existing contracts and gaps rather than duplicating modules.
- Record the actual Maven reactor roots and supported test commands; establish a reproducible baseline.

**Acceptance:** capability ownership map, code/test inventory, explicit gaps, and reproducible verification commands. No claims of passing tests without recorded CI/build evidence.

### M1 — Construction consultancy domain and delivery evidence
- Add or complete engagement, ToR/scope, deliverable register, consultant assignments/reviews, drawing/specification revisions, site diary, RFI/submittal, inspection, non-conformance, variation and handover contracts where absent.
- Reuse neutral project/WBS/scheduling, documents, workflow and audit contracts.
- Add tenant isolation, authorization, immutable revisions and idempotent commands/events where relevant.

**Acceptance:** domain and application tests for lifecycle transitions, authorization, revision history, duplicate requests, and tenant isolation.

### M2 — Indonesia compliance profile
- Add versioned obligation/evidence models and rule-source metadata.
- Add organization/person credential records, expiration alerts, project applicability assessment, PBG/SLF evidence workflow where applicable, K3/environment/quality registers, and auditable review/exception handling.
- Keep external government-system integration behind adapters; never claim direct integration without a verified API and authorization.

**Acceptance:** tests for applicability, missing/expired evidence, rule supersession, historical decision stability, tenant isolation, and export of an evidence dossier.

### M3 — Istisna' contract and Sharia governance
- Add opt-in contract type/policy, signed specification and price snapshots, approval/signature workflow, delivery and acceptance obligations, approved variation control, claims/disputes, and Sharia review evidence.
- Integrate with shared Finance through a public contract and test event/payment idempotency.
- Keep legal clause templates versioned and subject to professional review.

**Acceptance:** tests prevent silent price/scope changes, premature payment eligibility, duplicate settlement side effects, unauthorized variation, and conflation of parallel istisna' contracts.

### M4 — Consultant commercial control and reporting
- Contract/fee budget, resource loading, timesheets/expenses if in scope, fee milestones, invoice readiness, progress and earned-value views, project margin, cash-flow forecast, and client reporting.
- Explicitly separate consultancy fee contracts from construction works contracts and istisna' contracts.

**Acceptance:** reconciled totals, permission-aware reporting, currency/rounding tests, and exportable client reports.

### M5 — Interoperability and field operations
- Offline-capable field capture with conflict resolution, geotagging only when justified/consented, photos/documents with provenance, digital signatures through adapters, and import/export for common schedules, BoQs and document registers.
- Add external provider integrations only behind documented adapters with retry, timeout and failure visibility.

**Acceptance:** duplicate/offline replay tests, evidence provenance, migration/contract compatibility tests and observable integration failures.

### M6 — Production readiness
- Security/privacy review, threat model, retention/deletion policy, backup/restore, observability, accessibility, localization, load tests, migration/rollback plan, incident runbooks, and deployment profiles.
- Verify dependency and architecture boundaries and test the supported Maven reactor(s) in CI.

**Acceptance:** CI evidence, security/tenant-isolation tests, operational runbooks, restore verification, and release checklist. Regulatory or Sharia approval remains an external professional decision.

## Cross-cutting non-negotiables

- Tenant-scoped data access and authorization at every application boundary.
- No direct cross-capability persistence access; use documented public contracts.
- Audit consequential changes with actor, reason, source revision and correlation/idempotency context.
- Version contract terms, compliance rules, specifications, drawings, BoQ, schedule and reports; preserve signed/approved snapshots.
- Use exact decimal/currency handling for money and explicit units for quantities.
- Avoid universal enums for consultant, contractor, owner, regulator and Sharia reviewer workflows; model role and policy boundaries intentionally.
- Keep sensitive personal, financial, project-site and client information access-controlled.
- No claim of legal, technical, tax, or Sharia compliance based solely on software validation.

## External references

- [BPK legal database — PP No. 14 Tahun 2021](https://peraturan.bpk.go.id/Details/161844/pp-no-14-tahun-2021)
- [Ministry of Public Works — Construction services risk-based licensing guidance](https://sahabat.pu.go.id/post/id/layanan-kami/konsultasi-perizinan/perizinan-berusaha-berbasis-risiko-subsektor-jasa-konstruksi)
- [Ministry of Public Works JDIH — PP No. 14 Tahun 2021](https://jdih.pu.go.id/detail-dokumen/PP-nomor-14-Tahun-2021-tahun-2021-Perubahan-Atas-Peraturan-Pemerintah-Nomor-22-Tahun-2020-Tentang-Peraturan-Pelaksanaan-Undang-Undang-Nomor-2-Tahun-2017-Tentang-Jasa-Konstruksi)

These references are initial discovery sources, not a substitute for checking current consolidated law and all relevant implementing instruments.

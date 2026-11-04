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

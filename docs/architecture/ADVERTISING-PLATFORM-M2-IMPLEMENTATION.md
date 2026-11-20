# Advertising Platform — M2 Campaign and Creative Management

- **Milestone:** M2
- **Branch:** `feat/advertising-m2-campaign-creative`
- **Status:** Implementation committed on a feature branch; build and integration-test execution remain unverified.
- **Depends on:** M0 architecture audit and M1 advertiser/publisher/inventory foundation.

## Scope delivered

### Campaign management
- Tenant-scoped campaign identity and advertiser ownership.
- Campaign objectives: awareness, traffic, leads, sales and app installs.
- Campaign start/end window validation.
- Lifecycle: draft → scheduled → active ↔ paused → completed; archive is available only before completion/while not active.
- Application authorization for create/manage actions.
- Campaign activation and scheduling re-check that the advertiser is active and at least one assigned creative remains approved.

### Creative management
- Creative metadata for text, image, video and native formats.
- Binary assets remain owned by the Documents capability; Advertising stores an opaque document reference, not binary content or storage-provider keys.
- Absolute HTTP(S) destination URI validation.
- Draft → submitted → approved/rejected review lifecycle, with rejected creatives eligible for resubmission.
- Review note and rejection reason limits.
- Authorization separation between creative creation/submission and creative review.

### Campaign-to-creative assignment
- A creative must be approved before assignment.
- Campaign and creative must belong to the same advertiser.
- Assignment is permitted only while a campaign is draft or scheduled.
- PostgreSQL composite foreign keys include tenant and advertiser ownership on both sides, preventing cross-tenant and cross-advertiser assignment even if the adapter is called outside the application service.
- Duplicate assignment is rejected by a tenant-scoped composite primary key.

## Persistence

Migration: `V2__advertising_campaigns_and_creatives.sql`

Tables:
- `advertising_campaign`
- `advertising_creative`
- `advertising_campaign_creative`

All primary keys, foreign keys, lookup predicates and assignment constraints are tenant-scoped. Campaign and creative lifecycle updates use optimistic compare-and-set versions. The assignment query is deterministically ordered by assignment time and creative ID.

The PostgreSQL adapter uses blocking JDBC. Any runtime composition must dispatch it on a blocking worker; returning a `CompletionStage` does not make JDBC non-blocking.

## Tests added

- Domain tests for campaign windows, campaign transitions, creative asset requirements, creative submission and review transitions.
- Application tests for active-advertiser requirements, authorization-before-mutation, creative approval before assignment, advertiser ownership and campaign scheduling/activation.
- PostgreSQL Testcontainers tests for tenant-isolated campaign and creative reads, assignment persistence, cross-tenant assignment rejection and optimistic stale-write rejection.

**Verification caveat:** Tests are committed but have not been run in this environment. Do not treat them as passing until the Advertising Maven reactor and PostgreSQL Testcontainers suite execute successfully in CI.

## Deliberate non-goals

M2 does not implement:
- campaign budgets, CPM/CPC pricing, pacing or spend reservation;
- audience/targeting evaluation;
- ad selection, ranking, frequency capping or real-time serving;
- impression/click ingestion, attribution or conversion measurement;
- invoices, usage billing, publisher earnings or payouts;
- public REST APIs, runtime composition, Identity/Party adapters, or document upload.

These remain separate milestones. Finance owns invoices, payment execution and accounting; Documents owns binary content; Advertising owns campaign configuration, creative policy metadata, and delivery eligibility.

## Next milestone

M3 should introduce a low-latency ad decision/serving contract that consumes enabled placements, active campaigns and approved assigned creatives. It must define deterministic eligibility filters, tenant/property boundaries, creative/placement format compatibility, decision IDs, cache invalidation, timeout/fallback behavior and an auditable decision result. M3 must not yet count an impression as billable evidence; measurement and billable-event qualification belong to M4.

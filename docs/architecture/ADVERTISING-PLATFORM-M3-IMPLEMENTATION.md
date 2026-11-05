# Advertising Platform — M3 Ad Serving and Decisioning

- **Milestone:** M3 foundation (eligibility, placement targeting, decision audit)
- **Branch:** `feat/advertising-m3-ad-serving`
- **Base:** current `main` after M0, M1, M2 and M1 host-integration merges
- **Status:** Implementation and tests committed to a feature branch; Maven and Testcontainers execution pending.
- **Owner:** Advertising capability

## 1. Delivered behavior

M3 introduces a first correct serving decision path, not yet a complete programmatic ad exchange.

A serving request contains:
- a tenant scope resolved by a trusted host integration (never trusted directly from a public request body);
- an opaque placement ID;
- the page URI;
- a requested creative format.

The serving service:
1. Resolves the placement, canonical publisher property and publisher account within the tenant.
2. Requires the placement to be enabled, the property verified and the publisher account active.
3. Requires the page URI to match the publisher property's origin (scheme, host and effective port).
4. Requires requested format to match the configured placement format.
5. Loads candidates that have an explicit campaign-to-placement target, an active campaign within its time window, an active advertiser, an approved assigned creative and a matching format.
6. Rechecks the key lifecycle and tenant invariants in the application layer.
7. Reads at most 100 candidates per decision and selects deterministically by campaign creation time, campaign ID and creative ID. This is a bounded transparent tie-breaker, not a bid ranking or auction.
8. Persists a decision record for both fill and no-fill outcomes before returning the result.

A no-fill is a normal, auditable response rather than an exception. Reasons include unknown placement, ineligible inventory, property-origin mismatch, format mismatch and no eligible campaign/creative.

## 2. Explicit inventory targeting

Campaigns are not eligible for every publisher slot in a tenant.

Advertisers must explicitly target an enabled placement while the campaign is draft or scheduled. The application service verifies:
- campaign and advertiser belong to the tenant;
- advertiser is active;
- placement is enabled;
- publisher property is verified;
- publisher account is active.

The PostgreSQL relation `advertising_campaign_placement` uses tenant-scoped foreign keys to both the campaign/advertiser owner tuple and placement. The serving query joins this relation, so an active campaign without an explicit placement target is not served.

## 3. Domain and contracts

New domain contracts:
- `AdServingRequest`
- `AdServingInventory`
- `AdServingCandidate`
- `CampaignPlacementTarget`
- `AdDecision`
- `AdDecisionStatus`, `AdDecisionReason`
- `AdServingResult`

New application contract:
- `AdvertisingAdServingService`

New persistence SPI:
- `AdvertisingAdServingStore`

The campaign persistence SPI now also supports `assignPlacement`.

## 4. PostgreSQL

Migration: `V3__advertising_serving_and_decisions.sql`

Tables:
- `advertising_campaign_placement`: explicit tenant/advertiser/campaign/placement targeting, uniqueness and foreign keys.
- `advertising_ad_decision`: append-only serving outcome audit for fills and no-fills.

Indexes support placement-target candidate lookup, active campaign/approved creative delivery, and tenant/placement/time decision retrieval. The candidate read is capped at 100 rows per decision. Unknown placement identifiers are retained in no-fill decisions without a placement foreign key because the request may intentionally reference an unknown ID. Filled decisions carry the advertiser reference and use composite foreign keys to prove that campaign, creative and placement-target ownership all match; resolved property references remain tenant-scoped and foreign-key constrained.

The serving adapter uses a single joined candidate query with database-side status/time/format predicates and deterministic ordering. It rechecks placement, property and publisher eligibility in the query to reduce races between inventory lookup and candidate retrieval.

**JDBC is blocking.** Any HTTP/runtime composition must dispatch this adapter on a blocking worker or replace it with a genuinely reactive implementation behind the same SPI. A `CompletionStage` wrapper does not make JDBC non-blocking.

## 5. Tests added

Application tests cover:
- deterministic candidate selection;
- campaign schedule-window and creative approval eligibility;
- placement/property/publisher eligibility;
- property-origin, format mismatch and credential-bearing URI rejection;
- unknown placement no-fill;
- durable-decision contract invocation;
- cross-tenant candidate and campaign-target rejection.

Campaign application tests cover explicit placement targeting and rejection of unverified publisher inventory.

PostgreSQL Testcontainers tests cover:
- canonical inventory resolution;
- candidates require explicit placement targeting;
- targeted campaign is returned while an untargeted active campaign is not;
- filled, no-eligible-candidate and unknown-placement no-fill audit persistence.

Tests are committed but **not yet executed** in this authoring session. Do not treat them as passing until the Advertising workflow runs.

## 6. Deliberate boundaries and limitations

M3 does not yet provide:
- a public publisher JavaScript tag or public REST serving endpoint;
- publisher serving credentials, signed request validation, key rotation or domain-bound token verification;
- audience targeting, geo/device/context targeting or consent-aware profile signals;
- bids, CPM/CPC pricing, auction ranking, campaign budget reservation or pacing;
- frequency caps, deduplication or impression counting;
- caching, precomputed candidate indexes, Redis, CDN or edge delivery;
- impression/click/conversion ingestion or billable-event evidence (M4);
- billing, publisher earnings, revenue share or payout (M5).

A public endpoint must not accept a caller-supplied tenant as authority. It needs a publisher credential contract that resolves the placement and tenant, validates the origin, supports rotation/revocation, and enforces rate limits. Until that exists, this milestone exposes a trusted in-process application contract only.

The current deterministic ordering is intentionally a simple baseline. It is not a fairness guarantee and must not be represented as an auction. Budget-aware pacing and ranking require campaign budget/line semantics and a concurrency-safe reservation contract before they are introduced.

## 7. Next implementation slice

M3.2 should add publisher serving credentials and a versioned serving API:
- opaque, hashed, rotatable credentials bound to publisher/property/placement scope;
- origin allowlist and strict host validation;
- tenant resolution from credential, never from request payload;
- rate limiting and abuse controls;
- response DTOs that expose only approved creative fields and opaque decision ID;
- bounded timeouts and safe no-fill fallback;
- integration tests proving tenant spoofing, credential revocation and cross-origin attempts are denied.

Then M3.3 can add a cache/precomputed candidate strategy only after measuring the PostgreSQL baseline and defining invalidation semantics for campaign, creative, publisher and placement lifecycle changes.

# Numou Omnichannel — Foundation and Indonesia-First Roadmap

**Status:** Architecture baseline for implementation  
**Scope:** Reuse existing Numou business capabilities; define deployment-neutral boundaries and Indonesia-first connector priorities.  
**Non-goal:** This milestone does not claim production connector integrations or executable order orchestration.

## Decision

Omnichannel is a composition/orchestration capability, not a replacement for commerce, inventory, warehouse, supply chain, courier, payment, finance, customer, or workspace domains. Each existing domain remains independently usable. Installing omnichannel adds channel coordination; it must not become a mandatory dependency of those domains.

Core rule: **one business state, many channel experiences**. Channel adapters translate external protocol and provider-specific concepts into canonical commands/events. Existing domain modules remain authoritative for their own state.

## Module placement and reuse

Prefer existing reusable contracts first. Avoid creating parallel product, price, inventory, order, shipment, payment, customer, or accounting models.

Proposed logical boundaries (physical module split is a deployment choice):

- **Channel core:** channel identity, channel/tenant/branch context, correlation and idempotency metadata, capability descriptors, provider-neutral adapter contracts.
- **Commerce orchestration:** maps channel requests to existing catalog/pricing/promotion/order contracts; coordinates workflows but does not own authoritative order or stock state.
- **Connector adapters:** provider-specific authentication, payload mapping, rate limits, webhook verification, retries and provider error translation.
- **Operational adapters:** inventory/availability, warehouse, courier/fulfillment, payment and finance integrations through existing ports.
- **Read models/analytics:** optional projections for channel performance and customer journey; never a replacement for transactional domain records.

Do not force a single physical deployment topology. Keep domain/application APIs framework-neutral. Adapters may be packaged together in a modular monolith or deployed independently behind stable APIs/events. Shared contracts should be versioned; synchronous calls are for immediate decisions, and durable events/outbox patterns are for cross-service propagation.

## Deployment profiles

| Profile | Expected shape | Required safeguards |
|---|---|---|
| Standalone | Core and selected adapters embedded in one application | Explicit configuration, local persistence adapter, no hidden service discovery |
| Modular monolith | Modules share a process and transaction manager where appropriate | Enforced module boundaries; no cross-module table writes |
| Microservices | Independent deployable services using API/event contracts | Timeouts, retries, idempotency, schema compatibility, outbox/inbox and observability |
| Cloud | Managed runtime and provider adapters | Secret management, tenant isolation, rate limits, autoscaling, regional/data controls |
| Enterprise/on-premises | Private deployment and optional external connectivity | Replaceable infrastructure adapters, offline/replay operations, audit exports, policy controls |

These are deployment profiles over the same domain contracts, not separate product implementations.

## Indonesia-first priorities

Start with provider-neutral capabilities and connector seams, then implement and verify providers one by one. Initial priorities:

1. **Local checkout and branch operations:** POS/kiosk integration should reuse existing POS application and commerce primitives rather than duplicate them.
2. **Messaging and assisted commerce:** WhatsApp Business Platform and other messaging adapters, with explicit consent, template/policy handling and webhook signature validation.
3. **Marketplaces:** adapter pattern for Indonesian marketplace APIs (for example, Shopee and Tokopedia), with connector capabilities and API terms verified before implementation.
4. **Payments:** provider-neutral payment intent/status/refund ports; Indonesian payment methods and payment providers are connector-specific. Payment status must be reconciled against provider evidence.
5. **Delivery:** reuse Numou courier/shipment and supply-chain capabilities. Do not create an alternate shipment lifecycle inside omnichannel.
6. **Tax, currency and addresses:** explicit IDR minor-unit handling, Asia/Jakarta-aware timestamps, configurable Indonesian address/contact normalization, and tax/accounting rules delegated to existing finance/tax capabilities where available.

Provider names above are roadmap candidates, not a claim that integrations are implemented or API access has been approved.

## Canonical flow

1. A connector authenticates the provider request and resolves the configured tenant, branch, channel and external account.
2. It verifies signatures, validates payload shape, records correlation/idempotency metadata and maps the request to a canonical command.
3. The orchestration application calls existing domain use cases through ports; it does not write their tables directly.
4. A successful state transition and its outgoing event are durably recorded using the owning module's transaction/outbox boundary.
5. Connector delivery is retried with bounded backoff; poison messages are quarantined/dead-lettered and can be replayed safely.
6. Reconciliation compares provider and Numou state, emits actionable discrepancies, and never silently rewrites accounting or order history.

## Cross-cutting requirements

- Every command/read affecting tenant-owned data must be tenant-scoped; branch/store scope is explicit where applicable.
- External identifiers are namespaced by provider account and tenant; never assume a provider's ID is globally unique.
- Idempotency keys have documented scope, retention and conflict semantics.
- Webhooks are authenticated, deduplicated, durably accepted, and processed asynchronously where possible.
- Retries are bounded, jittered and observable. Replay is safe and auditable.
- Inventory availability is a decision/read model; reservation and stock movement remain owned by inventory/warehouse.
- Payments and accounting use explicit state machines and reconciliation. A channel callback alone is not proof of settlement.
- Customer identity linking is policy-driven and auditable; do not merge profiles solely on matching names or phone strings.
- Secrets are tenant/provider-account scoped and never logged. PII is minimized and retained under configurable policy.
- Connector contract tests use recorded/synthetic fixtures; production credentials are not required for the core test suite.

## Milestones

- **M1 — Foundation and architecture baseline:** boundaries, reuse map, deployment profiles, Indonesia-first sequence and acceptance rules (this milestone).
- **M2 — Canonical channel contracts:** tenant/branch/channel context, provider-account identity, capability model, idempotency/correlation and contract tests. No provider-specific business logic in the core.
- **M3 — Commerce orchestration integration:** adapt existing catalog, price, promotion and order use cases; enforce authoritative ownership and replay safety.
- **M4 — Inventory and fulfillment coordination:** availability/hold semantics, warehouse handoff, shipment creation and tracking via existing logistics modules.
- **M5 — Indonesia connector wave 1:** select one validated marketplace/messaging/payment connector at a time, with webhook security and reconciliation.
- **M6 — Operational hardening:** outbox/inbox, retry/dead-letter/replay, reconciliation dashboards, audit and tenant/branch isolation tests.
- **M7 — Additional channels and enterprise deployment profiles:** connector expansion, policy controls, deployment packaging and operational documentation.
- **M8 — Analytics and optimization:** cross-channel projections, attribution and operational recommendations, explicitly separated from transaction truth.

## Acceptance gate before calling a connector production-ready

- Provider API/version and terms checked; credentials and secrets handled outside source control.
- Tenant/account/branch mapping and authorization tests.
- Duplicate, out-of-order, timeout, retry, replay, malformed payload and provider outage tests.
- Contract tests plus integration tests against a sandbox when available.
- Reconciliation path and operator runbook.
- Metrics, structured logs with PII redaction, traces/correlation IDs, alerts and documented SLOs.
- CI and relevant persistence migration tests pass. If tests have not been run, status must say so explicitly.

## Next implementation step

M2 should implement the provider-neutral channel context and adapter contracts in the existing framework/module conventions, then connect them to existing commerce contracts. Before adding a new domain model, inspect the current catalog/order/POS/inventory ports and extend those contracts when they already represent the needed capability.

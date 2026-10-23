# Numou Omnichannel — M3: Commerce Integration Boundary

## Purpose

Define the safe integration boundary between channel adapters and Numou's existing commerce capabilities before wiring executable orchestration. The core channel contracts in `commerce/omnichannel/numou-omnichannel-core` describe channel context and adapter capabilities; they do not own products, prices, promotions, orders, inventory, payments or fulfillment state.

## Integration rule

**Adapt existing use cases; do not create parallel commerce aggregates.**

The omnichannel application layer may coordinate calls and map channel-specific input to existing application commands. It must not:
- create a second product/catalog, price, promotion, order, inventory, payment or shipment model;
- write another module's persistence tables;
- treat an external marketplace order ID as Numou's internal order ID;
- mark an order paid or shipped solely because a channel payload says so;
- infer authorization from `ChannelContext` alone.

## Required integration sequence

1. **Catalog / offering:** locate the existing offering/catalog query ports and map their output to a channel projection. Channel-specific listing IDs and listing state belong to a connector mapping, not the canonical product aggregate.
2. **Pricing:** call the existing pricing use case with its required configuration and context. Do not calculate a competing price in the channel layer. Currency and amount representation must follow the pricing contract.
3. **Promotion:** use existing promotion evaluation/application contracts. Do not trust a promotion total supplied by a remote channel without validation against the appropriate domain policy.
4. **Order intake:** call the existing order-creation/application port. If no suitable port exists, introduce a narrow port in the owning order application module first; do not create a second order aggregate inside omnichannel.
5. **Inventory:** request availability or reservation through the existing inventory/warehouse boundary. A cached channel projection is not a stock reservation.
6. **Payment and fulfillment:** use existing payment and courier/shipment application ports. External callbacks are inputs to verification/reconciliation, not proof of settlement or dispatch.

## Canonical boundary metadata

Every incoming command should carry:
- resolved `ChannelContext` (tenant, optional branch, channel type, provider account and correlation ID);
- an idempotency key with scope defined by tenant + provider account + operation;
- a provider-scoped external reference;
- authenticated actor/system identity and authorization result at the application boundary;
- schema/version metadata and a payload fingerprint where replay conflict detection is required.

Do not place raw payment credentials or unnecessary personal data in canonical metadata. Store provider payloads only where required, with access controls, retention limits and redaction.

## Idempotency and retry semantics

An idempotency key must be persisted atomically with the accepted result at the owning application boundary. Same key + same normalized request returns the prior result; same key + different request fingerprint returns a conflict. Concurrent duplicate requests must not create duplicate orders. A key object alone does not implement this guarantee.

External event delivery should use durable inbox/outbox patterns where supported. A connector must distinguish retryable transport failures from terminal validation/business failures. Retries must be bounded; poison messages need an operator-visible quarantine and safe replay process.

## Indonesia-first connector implications

The integration boundary is provider-neutral. Indonesia-specific adapters can later supply:
- marketplace listing/order identifiers and webhook verification;
- messaging-originated assisted orders with explicit consent and agent attribution;
- local payment methods and provider status reconciliation;
- IDR amounts and Asia/Jakarta timestamps;
- address/contact normalization that preserves source values and records normalized values separately.

Shopee, Tokopedia, WhatsApp Business Platform, and payment providers remain candidates, not implemented or approved integrations. Verify current API contracts, access requirements and provider terms before building each connector.

## Implementation gate before M3 runtime wiring

Repository integration must first identify and document the actual source paths and public contracts for:
- offering/catalog query;
- pricing resolution;
- promotion evaluation;
- order creation and order status;
- inventory availability/reservation;
- payment intent/status and shipment/fulfillment.

For each integration, record the owner module, application port, identifier type, transaction boundary, emitted events, tenant/branch authorization rules and available contract tests. Only then should an adapter or orchestration service be added. This avoids guessing class names or creating an incompatible duplicate API.

## Acceptance criteria

- Omnichannel remains optional; existing commerce modules do not depend on it.
- All calls go through owning application ports; no cross-module table writes.
- Tenant/branch authorization is enforced by trusted application services.
- Duplicate and concurrent order submissions are safe.
- Provider references are namespaced by tenant and provider account.
- Price, promotion, inventory, payment and shipment truth stays with the owning module.
- Contract tests cover successful mapping, invalid context, duplicate request, fingerprint conflict, timeout/retry, provider callback replay and downstream rejection.
- CI/full Maven reactor passes before the runtime integration is marked complete.

## Status and next step

This document is an integration guardrail, not a claim that runtime order orchestration is complete. The current repository search path did not return source matches for order/catalog application ports, so M3 runtime wiring must wait until those contracts are located and inspected rather than inventing dependencies. Next work should map the concrete existing contracts and then implement one vertical slice end to end.

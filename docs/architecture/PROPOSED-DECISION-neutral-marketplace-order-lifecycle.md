# Proposed Decision: Neutral Marketplace Order Lifecycle

- **Status:** Proposed, not an accepted ADR
- **Date:** 2026-10-28
- **Context:** Agriculture/breeder marketplace extension for `andalus-ai/numu`
- **Related:** ADR-003 reusable capability modules; `AGRICULTURE-MARKETPLACE-M0-AUDIT.md`

## Context

The repository contains Product and Commerce Offering, Supply Inventory, Payment, Purchasing, Fulfillment, Courier/Shipment, and Fleet capabilities. The M0 audit did not find a generic marketplace order aggregate. Existing restaurant ordering is domain-specific and should not become a universal order model.

Payment already has a separate obligation lifecycle and PostgreSQL persistence adapter. Fulfillment owns demand allocation and shipment handoff. Inventory owns stock and reservation invariants. Shipment owns parcel custody and shipment lifecycle.

## Decision proposed

Introduce a reusable **Marketplace Order** capability under Commerce (exact artifact placement to be confirmed against the Commerce reactor and its existing conventions before code is added).

The marketplace order is the commercial agreement between buyer and seller for an accepted purchase. It owns:
- order identity and lifecycle;
- buyer and seller references and required commercial party snapshots;
- line-level product/offer references and accepted commercial snapshots;
- order-level delivery and fulfillment intent;
- cancellation/amendment decision history and correlation references;
- idempotent command semantics and durable orchestration state.

It does **not** own:
- catalog/product identity or current offer publication;
- available/on-hand/reserved stock or stock movements;
- payment provider execution, verified payment status, or refunds;
- purchasing supplier master data or procurement policy;
- fulfillment allocation/picking/packing;
- shipment custody, carrier booking, fleet dispatch, or route/ETA calculation;
- agriculture production/lot/quality records.

## Order and payment are distinct

Creating an order may request a payment obligation through the published Payment application port. A payment ID/status is a reference to the Finance Payment owner, not a copied mutable payment aggregate. Payment success must be based on verified Payment state, never a browser redirect or unverified callback. Refunds remain Finance-owned.

## Order acceptance and inventory safety

Order creation and order acceptance are separate transitions if inventory/payment operations are asynchronous. The system must not mark an order accepted/confirmed until its configured acceptance policy is satisfied. Any inventory adapter selected for an acceptance path must implement durable atomic reservations, tenant isolation, and idempotent conflict handling. The current in-memory inventory adapter is not sufficient for multi-process production acceptance.

The system must not pretend a database transaction spans independent capabilities. Use durable orchestration state, idempotent commands, outbox/inbox delivery, timeouts, reconciliation, and explicit compensation/rejection states.

## Seller, channel, and geography

A seller identifier on an offer is a reference, not proof of seller eligibility or authorization. Seller participation and actor permissions must be verified by the application boundary. Channels are optional contexts and do not own the order lifecycle. Geo may assist service-area eligibility, but straight-line distance must not be presented as road distance or ETA.

## Consequences

Positive:
- Direct-to-consumer and B2B commerce can share the same order invariants.
- Agriculture adds lots, grading, traceability, and livestock-specific evidence as extensions.
- Transport providers, cooperatives, and crowd commerce remain optional integrations.
- Embedded, modular-monolith, and distributed compositions can use the same business contract.

Costs and risks:
- The new owner must be implemented without bypassing existing POM/reactor, authorization, audit, event, and persistence conventions.
- Durable inventory reservation and orchestration/recovery are prerequisites for a production acceptance flow.
- Tax, refund, settlement, and regulatory requirements vary by jurisdiction and product category.

## Alternatives rejected

1. Reuse restaurant order domain: wrong lifecycle and domain coupling.
2. Put marketplace order lifecycle in Payment: payment obligation is not the buyer-seller commercial agreement.
3. Put order lifecycle in Fulfillment: fulfillment does not own commercial terms and does not post stock movements.
4. Put agricultural fields on a universal order model: violates neutral-core boundary.
5. Create a separate marketplace inventory/payment/shipment ledger: duplicates authoritative owners.

## Verification required before implementation

- Inspect Commerce module conventions and relevant ADRs/AGENTS.md.
- Identify public offer/pricing contracts and actor authorization source.
- Add domain tests for legal transitions, immutable snapshots, duplicate idempotency keys, and invalid payload conflicts.
- Verify affected Maven reactor and tests; do not claim passing until executed.

# Neutral supply-chain and logistics boundaries

## Contexts and ownership

The supply chain should be reusable by retail, manufacturing, construction, field service, and software/hardware delivery. Construction-specific BoQ rules and project cost codes belong in construction extensions; the supply foundation should model neutral goods, quantities, locations, inventory, procurement, and fulfillment.

| Context | Owns | Must not own |
|---|---|---|
| Catalog | Item identity, SKU/attributes, UOM references, tracking policy | Stock balances |
| Inventory | Immutable stock movements, valuation, availability, reservations | Sales-order or construction-project lifecycle |
| Purchasing | Supplier commitments, purchase orders, receipt expectations | Final inventory balance calculation |
| Warehouse execution | Receiving inspection, put-away, pick tasks, packing, staging | Commercial order pricing |
| Fulfillment | Demand allocation, fulfillment order state, shipment handoff and completion | Authoritative inventory ledger |
| Transportation | Carrier assignment, dispatch, tracking events, proof of delivery | Inventory reservation rules |
| Planning | Forecasts, replenishment proposals, capacity and supply recommendations | Directly posting stock without inventory commands |

Each business invariant has one owner. Other contexts use application ports/events and do not mutate another context's state directly.

## Deployment profiles

- **Standalone:** one process and one deployment, with the same application ports and explicit tenant context.
- **Modular monolith:** contexts may share a database, but keep separate repositories, transaction boundaries, and ownership rules.
- **Distributed:** use versioned APIs/events, outbox/inbox delivery, idempotent consumers, and explicit saga/compensation for cross-context workflows.

A deployment profile must not change the meaning of a domain command. Process-local locks are not a substitute for database constraints or distributed coordination.

## Fulfillment lifecycle contract

The intended generic lifecycle is:

1. **Requested** — demand is accepted and lines are validated.
2. **Allocated** — inventory reservations cover the allocated quantities, or the order explicitly records partial allocation/backorder.
3. **Picking** — warehouse tasks identify source locations and picked quantities.
4. **Packed** — picked quantities are reconciled against fulfillment lines and package records.
5. **Staged / handoff-ready** — package and shipment identifiers are recorded; inventory issue and reservation commitment must be coordinated under a defined transaction or durable workflow.
6. **Shipped** — carrier handoff is confirmed and shipment identity/tracking is recorded.
7. **Delivered / completed** — delivery evidence or a trusted carrier event closes fulfillment.

Rejected, cancelled, short-picked, damaged, and partially fulfilled paths must be explicit transitions, not arbitrary status updates. Releasing reservations and reversing movements require auditable compensating commands; the ledger is not edited or deleted to hide history.

## Cross-context reliability

- Inventory owns the authoritative available quantity and performs atomic reservations/issues/transfers.
- Purchasing receipts are idempotent commands; a repeated receipt must not create additional stock.
- Fulfillment requests and shipment events use stable command/event IDs and tenant-scoped deduplication.
- For database-backed deployments, movement rows, command receipts, reservation state, and outbox records must commit atomically where they represent one local transaction.
- For cross-service workflows, use an outbox and idempotent inbox; assume at-least-once delivery.
- Event consumers must validate tenant identity from trusted message metadata and reject missing or mismatched tenant scope.

## Production gates

The current reference inventory adapter remains in-memory. Production rollout requires a persistent adapter with tenant-scoped queries/constraints, durable command fingerprints, transactionally atomic issue/transfer/reservation operations, migration/retention policy, reconciliation tooling, authorization integration, observability, and integration tests under concurrent multi-tenant load.

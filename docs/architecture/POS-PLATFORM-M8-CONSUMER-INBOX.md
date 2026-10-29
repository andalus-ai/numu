# POS Platform M8 — Consumer Inbox and Idempotent Handling

## Goal

Provide a durable idempotency boundary for modules consuming POS integration events. Each logical consumer handles a given event at most once per tenant.

## Contract

- `PosEventConsumer` is the application entry point.
- `PosEventInbox` is the input port.
- The PostgreSQL adapter inserts a receipt keyed by `(tenant_id, consumer_name, event_id)` and runs the handler in the same transaction.
- A duplicate receipt returns `false` and does not invoke the handler.
- A handler exception propagates and must roll back the receipt together with the handler's database writes. The broker can then redeliver.
- Separate logical consumers use separate stable consumer names and can independently process the same event.

## Transaction requirement

The inbox receipt and all local database effects must participate in the same JTA transaction. Do not call remote services or publish another broker message inside the handler as though those effects were atomic. For cross-service effects, write a new local outbox event in the same transaction and dispatch it separately.

## Delivery semantics

This creates effectively-once local database effects when the transaction manager enlists the inbox adapter and consumer repositories in the same transaction. It does not provide exactly-once network delivery. A consumer must still tolerate broker redelivery and use a stable consumer name across restarts and deployments.

## Retention and operations

Inbox receipts are durable deduplication records. Do not delete them on a short arbitrary TTL: deleting a receipt allows an old replay to execute the business effect again. Retention/partitioning and GDPR or tenant data-retention handling need an explicit policy based on the maximum replay horizon. Monitor duplicate rate, handler failures, and oldest unprocessed broker message externally.

## Verification status

Application tests cover duplicate suppression, independent subscriptions, and handler failure propagation. PostgreSQL concurrency and rollback integration tests remain required to prove the adapter's transaction enlistment with each supported runtime and datasource.

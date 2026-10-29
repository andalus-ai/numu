# Queue Management PostgreSQL adapter

This module owns the PostgreSQL schema for queue configuration, queue entries, append-only audit,
durable outbox records, and idempotency records. It deliberately does not duplicate the lifecycle
of source-owned customer visits, support tickets, work orders, or shipments.

## Tenant boundary

Every primary/foreign key for queue-owned business data is tenant-qualified. PostgreSQL row-level
security is enabled and forced for all five tables. Before any tenant-scoped statement, the runtime
must set the authenticated tenant inside the same transaction:

```sql
SET LOCAL numou.tenant_id = 'authenticated-tenant-uuid';
```

The runtime database role must not own the tables and must not have SUPERUSER or BYPASSRLS. Never
derive this setting from a caller-controlled request field without authorization. RLS complements,
but does not replace, explicit tenant predicates in repository queries.

## Atomic claim contract

The repository implementation must claim with a single transaction and locking strategy (for
example, an ordered candidate query using `FOR UPDATE SKIP LOCKED`, followed by the lifecycle
update before commit). FIFO ordering uses `entered_at, entry_id`; priority ordering uses
`priority DESC, entered_at, entry_id`. A claim must always include tenant and queue predicates.
A read-then-save sequence outside a single transaction is not acceptable.

## Command transaction contract

A command transaction must commit queue state, audit, outbox, and the idempotency result together.
Outbox delivery occurs after commit and must be retryable. An idempotency key is scoped by tenant and
operation; reuse with a different canonical request hash must be rejected. In-flight duplicates must
not execute the mutation a second time.

## Migration boundary

Flyway owns schema evolution. This first migration establishes the durable schema and tenant RLS
policies. The adapter is not production-ready until the JDBC repository and transaction implementation
are added and integration tests prove claim concurrency, RLS under a non-owner role, rollback atomicity,
idempotency replay/hash conflict, and outbox retry behavior. The migration itself is not evidence that
those runtime guarantees are already implemented.

# M5e — API Catalog Transactional Outbox

## Purpose

API catalog aggregate writes and domain-event publication must share one PostgreSQL transaction. The repository previously opened and committed its own JDBC connection while the application handler separately invoked EventPublisher inside the abstract UnitOfWork; those boundaries did not guarantee atomicity.

## Implementation

- ApiCatalogJdbcTransactionManager implements the shared UnitOfWork port and owns one worker-confined JDBC transaction.
- PostgresApiProductRepository and PostgresApiCatalogEventPublisher use the same manager and therefore reuse the same connection when invoked inside the UnitOfWork.
- Tenant context is installed with transaction-local `set_config('numou.tenant_id', ..., true)`; switching provider tenant within one transaction is rejected.
- Event rows are inserted into the shared `platform_outbox` table using the platform wire-envelope v1 format. Event ID uniqueness makes replayed publication idempotent.
- The existing platform reliability relay remains responsible for leased delivery, retries, and dead-letter handling.

## Runtime requirement

The platform reliability migration set (V100+) must have created `platform_outbox` before the API catalog event publisher is enabled. The application database role needs INSERT permission on that table.

## Verification

A PostgreSQL Testcontainers integration test verifies that catalog state and its outbox event commit together and both roll back together on a forced failure. The test also checks that the publisher stores the trusted tenant, actor, correlation, and trace metadata.

## Transaction constraint

This JDBC UnitOfWork deliberately keeps the complete database-only use-case chain on one worker subscription. Do not introduce asynchronous network calls inside this transaction. Perform artifact validation, remote authorization, and other I/O before entering the UnitOfWork, then re-read and mutate the aggregate inside it.

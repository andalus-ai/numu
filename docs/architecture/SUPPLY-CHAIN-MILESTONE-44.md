# Supply-chain milestone 44: filesystem recovery after checkpoint loss

## Objective

Exercise the durable filesystem artifact adapter across a simulated process restart when artifact bytes and the chunk receipt have committed but the database checkpoint has not.

## Coverage

- A first store instance appends a chunk using the stable export/offset idempotency key.
- The test intentionally leaves the logical database checkpoint at its old offset and creates a new store instance over the same artifact root.
- Retrying the same chunk ID, offset, and bytes returns the same size and SHA-256 digest without duplicating content.
- Reusing that chunk ID with different bytes after restart is rejected, and the original artifact bytes remain unchanged.

## Operational boundary

This validates the filesystem adapter's durable receipt and idempotency behavior across adapter instances. It does not replace a full PostgreSQL-plus-worker integration test, and it does not establish multi-host durability. The filesystem adapter remains intended for single-host deployments and tests; a multi-host production deployment needs an object-store adapter with equivalent conditional-write/idempotency guarantees.

## Validation

Maven tests were not run while authoring this milestone. Run the warehouse artifact filesystem module test suite and the worker/JDBC recovery suites, including the opt-in PostgreSQL integration tests when a dedicated test database is available.
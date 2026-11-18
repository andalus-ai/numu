# M3 — Schema-Bound Content Authoring

## Delivered
- Content revisions retain the exact `content_type_id` and `schema_version` used to validate that revision.
- Content creation and revision require an explicit schema identity and decoded field-value map.
- Authoring validates against a published schema version before encoding or writing the entry/revision.
- Invalid content raises `ContentValidationException` with structured field violations and is not persisted.
- A `ContentPayloadCodec` SPI keeps JSON serialization outside the domain and application layers.
- PostgreSQL stores schema identity on each immutable revision and enforces a tenant-scoped foreign key to the schema registry.
- Rehydration verifies the persisted current-revision pointer against contiguous revision history.

## Historical compatibility
V4 adds nullable schema-reference columns because records created before M3 have no schema identity. Those rows remain readable as legacy untyped history. All new writes through `ContentApplicationService` are schema-bound; legacy untyped application commands have been removed. Existing legacy rows are not silently assigned a guessed content type or schema.

## Invariants
1. Every authoring command names an exact content type and schema version.
2. Only published schemas can authorize persisted content.
3. Validation occurs before payload encoding and repository mutation.
4. Every revision captures its own schema version; later schema changes do not rewrite historical revision meaning.
5. Schema references are tenant-scoped at the database boundary.
6. Optimistic revision checks and revision insert/pointer advancement remain one database transaction.
7. Serialization is an adapter responsibility behind `ContentPayloadCodec`.

## Verification boundary
The Content Platform workflow verifies Maven compilation and unit tests. The dedicated workflow starts PostgreSQL 16 and applies V1 (core), V2 (schema registry), V3 (editorial lifecycle), and V4 (schema binding) before running repository integration tests. This verifies migration ordering, tenant-scoped schema foreign keys, atomic revision append, and stale-revision rejection in CI. Production rollout still requires an operator-reviewed backup and migration plan.

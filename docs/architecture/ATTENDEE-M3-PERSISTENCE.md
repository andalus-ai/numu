# Attendee Capability — M3 PostgreSQL Persistence

## Delivered

M3 introduces the optional PostgreSQL/JDBC adapter as a separate module:

- `numou-attendee-domain`: profile invariants and lifecycle.
- `numou-attendee-application`: use cases and persistence/authorization ports.
- `numou-attendee-persistence-jdbc`: PostgreSQL schema and JDBC adapter.

The adapter uses the existing Mutiny worker-pool convention for blocking JDBC calls. It does not add persistence dependencies to the domain or application layers.

## Storage ownership

`attendee_profile` stores tenant-owned contextual profile fields only. It stores `party_id` as a canonical identifier reference, not a copied Party/person record. The adapter deliberately does not query or mutate Ecosystem tables. Party existence and tenant/actor permission are checked through the application Party-access contract.

## Guarantees

- All profile reads require `tenant_id` in the SQL predicate.
- `(tenant_id, party_id, profile_kind)` is unique in PostgreSQL.
- New profiles must begin at revision zero.
- Updates must advance exactly one revision and use a compare-and-set predicate on tenant, profile ID and prior revision.
- Tenant, Party reference, profile kind and creation timestamp are immutable after insertion.
- Stale updates fail with `AttendeeProfileOptimisticLockException`.
- Unique violations fail with `AttendeeProfileAlreadyExistsException`; the application handler maps the concurrent-create race to the same stable application error as the pre-check.
- JDBC transactions commit or roll back as one unit per repository write.

The application pre-check is for a friendly expected outcome only. It is not the concurrency guarantee; the database unique constraint is authoritative.

## Migration

The adapter migration is `db/migration/attendee/V1__attendee_profiles.sql`. The attendee-specific location avoids version collisions with other capability adapters that also publish a `V1` migration. Deployment composition must add `classpath:db/migration/attendee` to its Flyway locations (alongside the other selected capability locations) and configure one migration runner per database.

## Verification

PostgreSQL Testcontainers integration tests cover persistence/rehydration, tenant isolation, cross-tenant independence, database uniqueness and optimistic revision conflicts. Tests are configured to be skipped when Docker is unavailable. Maven build and CI status must still be checked before treating this milestone as verified.

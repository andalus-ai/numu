# Attendee Capability — M3 PostgreSQL Persistence

## Delivered

M3 introduces a dedicated PostgreSQL/JDBC adapter:

- `numou-attendee-postgres-adapter` is a separate infrastructure module.
- `V1__attendee_profiles.sql` creates tenant-owned attendee profiles.
- `JdbcAttendeeProfileRepository` implements the application repository port.
- Reads always include `tenant_id` in the SQL predicate.
- New profiles are inserted at revision zero.
- Updates require the persisted revision to equal candidate revision minus one.
- PostgreSQL enforces uniqueness of `(tenant_id, party_id, profile_kind)`.
- The canonical `party_id` foreign key references `ecosystem_party` and uses `ON DELETE RESTRICT`.
- The adapter translates the named uniqueness constraint to an application-level duplicate exception and stale writes to an application-level optimistic-lock exception.

## Persistence ownership and schema

Attendee owns only its contextual profile table. It does not copy Party data, create a second person table, or take ownership of canonical Party identity. Tenant ID is stored as a tenant boundary key; the migration intentionally does not assume a particular tenant registry table.

The migration expects the Ecosystem Party migration to have run first in the composed application database. The adapter does not duplicate or own the Ecosystem schema.

## Concurrency behavior

The application pre-check is a user-friendly fast path, not the concurrency boundary. Two requests can both pass the pre-check; the named PostgreSQL unique constraint guarantees that only one can commit. The adapter maps that exact constraint to `ATTENDEE_PROFILE_ALREADY_EXISTS`.

For updates, the SQL statement includes tenant, profile ID and expected previous revision. A zero-row update is treated as an optimistic-lock conflict. This also prevents a profile ID from being used to update another tenant's row.

## Operational notes

- Run the Ecosystem Party migrations before the Attendee migration.
- Include this adapter only in application compositions that use PostgreSQL.
- Keep tenant predicates in repository SQL even if database row-level security is later added.
- Do not expose raw SQL or adapter exception types through REST/event contracts.
- JDBC work is scheduled on Mutiny's worker pool; no blocking JDBC call runs on an event-loop thread.

## Tests added

The PostgreSQL Testcontainers suite covers:
- round-trip persistence and rehydration;
- cross-tenant read isolation;
- duplicate prevention within one tenant while allowing another tenant to reference the same Party;
- optimistic revision conflicts and valid lifecycle updates;
- rejection of references to a nonexistent canonical Party.

## Verification status

The source, migration, reactor module wiring and tests are committed on the M3 branch. Maven compilation and Testcontainers execution have not been run in this environment. The milestone must not be marked CI-verified until the framework reactor runs these tests with Docker available.

# PostgreSQL outbox relay-store integration tests

`PgOutboxRelayStoreIntegrationTest` exercises the actual Mutiny PostgreSQL adapter against the configured Quarkus test database. It covers:

- eligibility filtering for available, non-terminal events;
- persisted worker identity, attempt count, and lease token;
- reclaim of an expired lease and fencing of the previous token;
- retry/terminal updates, bounded error text, and lease clearing;
- rejection of invalid claim arguments before SQL execution.

## Run in the real Numou module

Run the module's normal test command with the PostgreSQL test service configured, for example `./mvnw test -Dtest=PgOutboxRelayStoreIntegrationTest` if that module uses Maven. This starter source dump does not include a build descriptor, so the command and dependency versions must be confirmed in the real repository.

The test uses the real `construction_outbox_event` table name. Its fallback DDL exists only to make the required columns explicit for starter evaluation; reconcile it with the canonical migration set before using it in the real module. The test deletes only rows with the dedicated `IntegrationTestEvent` event type before each method. Use an isolated test database, not production.

## Limits

Static contract checks do not compile Java or prove database behavior. These tests were added but have not been compiled or run against PostgreSQL in this environment. They do not test a real broker, operator replay API, or production service-role permissions.

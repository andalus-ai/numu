# Supply Chain M16 — Recovery lease integration hardening

## Scope

M16 strengthens the opt-in PostgreSQL integration test for the warehouse recovery journal. It removes the assumption that the database contains exactly one eligible recovery attempt, which made the test fail when run against a shared development database.

## Scenarios covered

- Persist a recovery payload and verify exact decimal-string round-tripping.
- Claim the inserted recovery attempt with worker A.
- Verify worker B cannot claim that same attempt while A's lease is live.
- Expire the test row's lease explicitly and verify worker B can claim it afterward.
- Delete the test's tenant/command row in a finally block.

## Running

Set NUMOU_TEST_POSTGRES_URL, and optionally NUMOU_TEST_POSTGRES_USER and NUMOU_TEST_POSTGRES_PASSWORD, to a disposable PostgreSQL database with warehouse migrations V1–V4 applied. Run the numou-warehouse-persistence-jdbc module tests from a Maven reactor that includes the repository's parent and supply/inventory modules.

## Boundaries

This test exercises PostgreSQL lease behavior, but it is not a substitute for a true multi-connection contention test with synchronized workers, migration automation, or downstream inventory adapter idempotency tests. The test is opt-in and is skipped when NUMOU_TEST_POSTGRES_URL is unset. No test result is claimed by this change.

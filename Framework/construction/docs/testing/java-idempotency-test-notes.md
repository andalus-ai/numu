# Java idempotency executor tests

`IdempotentCommandExecutorTest` adds unit coverage for the application-layer idempotency decision logic:

- a newly reserved key executes the action and stores the encoded result;
- a completed matching request returns the stored response without rerunning the action;
- a reused key with a different request is rejected before the action;
- an incomplete matching reservation fails closed;
- a failed business action propagates and does not complete the outcome;
- a failed completion result turns the command into a failure;
- invalid required inputs are rejected before the runner is invoked.

## Important scope limit

These are Java unit tests with an in-memory fake context and recording runner. They test `IdempotentCommandExecutor` logic, not PostgreSQL behavior and not the rollback guarantees of `PgTransactionalCommandRunner`. The fake runner does not implement transaction semantics.

Run this test from the real host build once the source bundle is integrated and its dependency versions are known. The starter bundle has no Maven/Gradle build descriptor, so this test has not been compiled or executed here.

Before production acceptance, add an integration test against PostgreSQL using the real `PgTransactionalCommandRunner`, `PgTransactionalCommandContext`, idempotency adapter, BOQ adapter, and outbox adapter. Force a failure after each write stage and verify all records roll back; also run concurrent requests with the same tenant/key and verify one business execution and one durable result.

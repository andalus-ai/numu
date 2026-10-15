# Trusted actor/tenant command-handler runtime tests

`TrustedCommandContextTest` exercises the Java command-handler boundary using a recording `BoqRevisionWorkflow` fake and fixed clock. It verifies:

- capture, submit-for-review, and approve propagate the server-resolved tenant and actor IDs;
- the handler uses the configured clock instant;
- missing or blank tenant context fails before invoking the workflow;
- missing actor context fails before invoking the workflow;
- client command records do not expose actor or tenant components.

Run this test through the host project's existing Java test build after copying the source into the matching module. This starter bundle does not include the project's Maven/Gradle build descriptor or dependency lock, so the test has not been compiled or executed here. These are handler-boundary tests only; they do not replace authorization-adapter, database transaction, idempotency, or outbox integration tests.

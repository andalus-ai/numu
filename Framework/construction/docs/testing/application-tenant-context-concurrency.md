# Application tenant-context concurrency acceptance test

## Purpose

`TrustedCommandContextTest.concurrentRequestsResolveTenantAndActorBeforeAsyncWorkflowBoundary` exercises the BOQ handler boundary with two concurrent request threads. Each thread has a distinct test tenant and actor context; both requests are released from a barrier together, and the test asserts that each workflow invocation receives its own identity.

The test also removes thread-local values after each invocation and bounds waits to avoid hanging the test process.

## Important scope limit

This is an application-boundary test harness, not proof that Syirkah's production authentication integration uses the same context implementation. It does not exercise HTTP authentication, CDI request scope, the production tenant-context bean, PostgreSQL row-level security, or the database connection pool. Those require integration in the complete Syirkah repository.

The handler currently resolves tenant and actor identity while constructing the workflow call, before crossing the asynchronous `CompletionStage` boundary. Do not change this to defer reading request-scoped or thread-local identity inside asynchronous callbacks unless context propagation is explicitly implemented and tested.

## Run

In the complete Syirkah project, run the relevant JUnit test through the project's normal Maven/Gradle test task. This source bundle does not include the full build descriptor/dependency graph, so static checks alone do not establish Java compilation or runtime success.

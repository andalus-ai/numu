# Numou Feedback — M0 Foundation

- **Status:** Implemented foundation slice; runtime and delivery verification remain pending
- **Branch:** `feedback/m0-persistence-outbox`
- **Parent architecture:** [ADR-003](ADR-003-reusable-capability-modules.md) and [Capability Packaging and Composition Contract](CAPABILITY-PACKAGING-AND-COMPOSITION.md)

## Ownership and canonical model

Feedback owns the original submission and its triage/resolution lifecycle. The canonical aggregate is `Feedback`, using the extensible `FeedbackKind`, opaque `FeedbackActor`, bounded `FeedbackContext`, shared `TenantId`, and shared `DomainEvent` contract.

Feedback does not own customer identity, support cases, survey definitions, product records, or diagnostic telemetry. It references actors and source records through opaque values. Host applications remain responsible for authentication, tenant authorization, and anonymous-submission policy. Support remains the owner of service tickets and operational case management; Survey remains the owner of survey authoring, distribution, respondent sessions and scoring.

## Implemented artifacts

- `numou-feedback-domain`: canonical `Feedback` aggregate, extensible kind, actor/context value objects, lifecycle invariants and versioned domain events.
- `numou-feedback-application`: submission command/handler and tenant-scoped `FeedbackRepository` port.
- `numou-feedback-postgres-adapter`: PostgreSQL implementation, aggregate rehydration, idempotency storage and transactional outbox writes.
- `feedback-postgres-adapter/src/main/resources/db/migration/V1__feedback_foundation.sql`: write schema and indexes.

The top-level `feedback/` reactor is included in `core/framework/pom.xml` and resolves its parent through `../core/framework/pom.xml`.

## Persistence guarantees

1. Every aggregate is tenant-owned and the authoritative lookup uses both tenant ID and feedback ID.
2. The feedback row, tenant-scoped idempotency mapping and pending domain events are committed in one PostgreSQL transaction.
3. Idempotency keys are normalized and scoped by tenant. Concurrent duplicate submissions use a conflict-safe insert; the losing transaction rolls back its new aggregate and returns the original feedback ID.
4. Outbox records preserve the domain event's versioned `eventType()`, event ID, aggregate ID, tenant ID, payload and occurrence time.
5. Rehydration uses the canonical `Feedback` aggregate and clears constructor-raised events so reads do not create duplicate `FeedbackSubmitted` events.
6. The adapter does not publish events directly. A later relay owns claiming, retry, backoff, dead-letter and publication state.

## Verification

The domain and application tests cover aggregate invariants, kind validation, lifecycle transitions, bounded context values, and submission orchestration. PostgreSQL Testcontainers tests cover atomic submission/outbox persistence, idempotent retries, and tenant-scoped reads.

Tests have been added but **have not been executed in this session**. No CI pass is claimed. The documented reactor command is:

```bash
mvn -f core/framework/pom.xml -pl ../../feedback -am test
```

Run it from the repository root in an environment with the required Maven dependencies. Docker is required for the PostgreSQL Testcontainers integration test; that test is configured to be skipped when Docker is unavailable.

## Remaining M0 work

- Runtime capability registration and optional composition wiring.
- Host-provided datasource/adapter lifecycle and configuration.
- Outbox relay/worker with bounded retries, lease recovery, terminal failure and observability.
- CI verification in the repository's actual Maven reactor.

## Explicitly deferred

HTTP transport, public portal, widgets/SDKs, attachment/evidence lifecycle, comments, votes, ratings, surveys, analytics, AI classification and cross-capability automation are not claimed as complete by this M0 persistence slice.

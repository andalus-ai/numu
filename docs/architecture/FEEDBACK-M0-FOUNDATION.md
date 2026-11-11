# Numou Feedback — M0 Foundation

- **Status:** Initial implementation on branch `feedback/m0-persistence-outbox`
- **Scope:** Domain, application contract, PostgreSQL persistence, idempotency and transactional outbox
- **Parent architecture:** [ADR-003](ADR-003-reusable-capability-modules.md) and [Capability Packaging and Composition Contract](CAPABILITY-PACKAGING-AND-COMPOSITION.md)

## Ownership

Feedback owns submissions and their lifecycle. It does not own customer identity, support cases, surveys, product records, or diagnostic telemetry. It references tenant, submitter, source application, source module, and source record using opaque identifiers. Host applications remain responsible for authentication, tenant authorization, and deciding whether anonymous submissions are permitted.

Support remains the owner of service tickets and operational case management. Survey remains the owner of survey authoring, distribution, respondent sessions, and survey evaluation. Feedback may later link to either through explicit contracts; it must not duplicate either lifecycle.

## M0 implementation

The `feedback/` Maven reactor is a standalone top-level capability reactor whose parent resolves through `../core/framework/pom.xml`. This follows the repository's multiple-reactor layout and does not assume a root-level Maven POM.

Artifacts:

- `numou-feedback-domain`: feedback identity, type, priority, lifecycle, aggregate, and domain events.
- `numou-feedback-application`: submission command, handler, and tenant-scoped persistence port.

The domain has no runtime/framework dependency. The application depends on the Feedback domain and defines a tenant-scoped persistence port. The PostgreSQL adapter implements tenant-scoped reads and atomically saves the aggregate, idempotency mapping, and pending outbox events in one transaction. Repeated idempotency keys return the original feedback ID; keys are scoped by tenant. The adapter uses a conflict-safe insert path so concurrent retries cannot create duplicate feedback records.

## Initial supported types

`COMMENT`, `BUG`, `IDEA`, `FEATURE_REQUEST`, `ERROR_REPORT`, `RATING`, `REVIEW`, `COMPLAINT`, `SUGGESTION`, `QUESTION`, and `OTHER`.

This enum is a broad platform-level type vocabulary, not a tenant-specific category registry. Additional domain-specific categories belong in configuration or extensions.

## Lifecycle

- `SUBMITTED -> TRIAGED | REJECTED | DUPLICATE`
- `TRIAGED -> ACCEPTED | REJECTED | DUPLICATE`
- `ACCEPTED -> IN_PROGRESS | REJECTED`
- `IN_PROGRESS -> UNDER_REVIEW | RESOLVED`
- `UNDER_REVIEW -> IN_PROGRESS | RESOLVED`
- `RESOLVED -> REOPENED | CLOSED`
- `REOPENED -> IN_PROGRESS | REJECTED`
- `REJECTED | DUPLICATE -> CLOSED`
- `CLOSED` is terminal.

Tenant-specific workflow customization is intentionally deferred until the baseline lifecycle and compatibility contract are established.

## Verification

Run from the repository root:

```bash
mvn -f feedback/pom.xml test
```

The domain and application tests cover submission invariants, validation, tenant ownership, lifecycle transitions, versioning, and the application persistence port. The PostgreSQL adapter includes a Testcontainers integration test for atomic item/idempotency/outbox persistence, replay behavior, and tenant isolation. Tests have been added but have not been executed in this session; do not treat source commits as a passing build.

## Explicitly deferred

M1 adds public submission API and type-specific validation policies. M2 adds Document Store attachment references. HTTP transport, runtime registration, authentication integration, outbox relay/worker, public portal UI, and advanced retention/partitioning policies remain deferred. The outbox table is written transactionally; publishing and retry orchestration belong to a later adapter/worker milestone.

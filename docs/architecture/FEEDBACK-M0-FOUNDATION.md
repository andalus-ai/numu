# Numou Feedback — M0 Foundation

- **Status:** Initial implementation on branch `feat/feedback-m0-foundation`
- **Scope:** Domain and application foundation only
- **Parent architecture:** [ADR-003](ADR-003-reusable-capability-modules.md) and [Capability Packaging and Composition Contract](CAPABILITY-PACKAGING-AND-COMPOSITION.md)

## Ownership

Feedback owns submissions and their lifecycle. It does not own customer identity, support cases, surveys, product records, or diagnostic telemetry. It references tenant, submitter, source application, source module, and source record using opaque identifiers. Host applications remain responsible for authentication, tenant authorization, and deciding whether anonymous submissions are permitted.

Support remains the owner of service tickets and operational case management. Survey remains the owner of survey authoring, distribution, respondent sessions, and survey evaluation. Feedback may later link to either through explicit contracts; it must not duplicate either lifecycle.

## M0 implementation

The `feedback/` Maven reactor is a standalone top-level capability reactor whose parent resolves through `../core/framework/pom.xml`. This follows the repository's multiple-reactor layout and does not assume a root-level Maven POM.

Artifacts:

- `numou-feedback-domain`: feedback identity, type, priority, lifecycle, aggregate, and domain events.
- `numou-feedback-application`: submission command, handler, and tenant-scoped persistence port.

The domain has no runtime/framework dependency. The application depends only on the Feedback domain and defines a persistence port. The persistence adapter must implement tenant-scoped reads and atomically save the aggregate with its pending outbox events. Repeated idempotency keys must return the original feedback ID.

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

The domain and application tests cover submission invariants, validation, tenant ownership, lifecycle transitions, versioning, and the application persistence port. CI execution has not yet been observed for this branch; do not treat the source commits as a passing build.

## Explicitly deferred

M1 adds public submission API and type-specific validation policies. M2 adds Document Store attachment references. Persistence schema, PostgreSQL adapter, transactional outbox implementation, HTTP transport, runtime registration, authentication integration, and portal UI are not claimed by M0. They must be added through their owning adapters and host composition rather than leaking infrastructure into the domain.

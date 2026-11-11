# Numou Feedback Capability

**Milestone:** M0 — domain, application and persistence foundation  
**Status:** Canonical aggregate, application submission contract, PostgreSQL persistence and transactional outbox schema implemented; HTTP transport, runtime registration and outbox relay remain pending.

## Purpose

Feedback is a reusable, tenant-owned capability for comments, bug reports, ideas, error reports, complaints, reviews and future feedback kinds. It is independent of Support and Survey and may be composed into standalone applications, modular monoliths or distributed deployments.

## M0 domain contract

- `Feedback` owns the original submission and its triage/resolution lifecycle.
- `FeedbackKind` is an extensible validated code, not a closed enum. Initial well-known codes: comment, bug, idea, error-report, complaint, review and other.
- `FeedbackActor` is an opaque principal reference. Feedback does not own User, Customer, Party or Employee identity.
- `FeedbackContext` stores bounded origin references only. It must not contain request bodies, credentials, stack traces or copied business records.
- Every aggregate is tenant-owned through the shared `TenantId` primitive.
- Domain events use the shared `DomainEvent` contract. Tenant, actor, correlation and idempotency metadata belong in the application/integration envelope, not duplicated into the neutral domain event abstraction.
- `FeedbackRepository` is a capability-owned persistence port. Reads require both `TenantId` and `FeedbackId`; adapters must enforce the tenant predicate in the authoritative query.
- Comments, votes, ratings, attachments and diagnostic evidence are separate resources introduced by later milestones.

## Lifecycle

```text
SUBMITTED -> TRIAGED
TRIAGED -> ACCEPTED | REJECTED | DUPLICATE
ACCEPTED -> IN_PROGRESS | CLOSED
IN_PROGRESS -> UNDER_REVIEW | RESOLVED | CLOSED
UNDER_REVIEW -> IN_PROGRESS | RESOLVED | CLOSED
RESOLVED -> REOPENED | CLOSED
REOPENED -> IN_PROGRESS | CLOSED
REJECTED | DUPLICATE -> CLOSED
CLOSED -> (terminal)
```

A consuming capability maps its workflow to these lifecycle concepts through an explicit policy/adapter. Do not silently widen transitions in the domain.

## Integration boundaries

| Capability | Relationship |
| --- | --- |
| Support | Feedback may link to a support case; Support remains the owner of case handling, SLAs and support conversations. |
| Survey | Feedback may link to a survey response or consume satisfaction metrics; Survey remains the owner of survey definitions, respondent sessions and scoring. |
| Notification | Receives application-level delivery commands/events; Feedback domain does not send messages. |
| Workflow / Gamelan | May orchestrate triage and escalation through application contracts; workflow state is not duplicated in the aggregate. |
| Identity / Party / Customer | Actor references are resolved by host authorization and identity capabilities; no identity model is copied here. |
| Documents | Later attachment/evidence resources reference the document capability; binary storage is not owned by this domain. |
| Timeline | May consume published feedback events as a projection; Timeline is not the write model. |
| Developer Portal / Iket | May submit API, SDK and integration feedback using stable kind/context references. |

## Security and privacy invariants

1. Tenant identity is mandatory and immutable on a Feedback aggregate.
2. Authorization is enforced by application use cases, not by possession of a FeedbackId.
3. Actor references are not proof of authorization.
4. Context values are bounded references only; diagnostic secrets and PII redaction belong at ingestion boundaries.
5. Public/anonymous submission policy, rate limiting, moderation, retention and attachment scanning are application/adapter concerns and must not be bypassed by direct persistence access.

## Build

The separate Maven reactor is included in the framework reactor and follows the repository's top-level capability pattern:

```bash
mvn -f core/framework/pom.xml -pl ../../feedback -am test
```

The framework parent resolves from `core/framework/pom.xml`. The foundation artifacts must be available in the local Maven repository or built/installed first according to the repository's multi-reactor workflow.


## Persistence foundation (M0)

The PostgreSQL adapter owns the Feedback write schema and migration:

- `feedback_items` stores the canonical `Feedback` aggregate snapshot, including opaque actor reference, extensible kind code and bounded origin context.
- `feedback_idempotency` scopes submission keys by tenant and resolves concurrent retries to the original feedback ID.
- `feedback_outbox` stores versioned domain events in the same transaction as the aggregate and idempotency mapping.
- Every item read includes both `tenant_id` and `id` predicates.
- Rehydration uses the canonical `Feedback` aggregate and does not emit a second `FeedbackSubmitted` event.

The adapter currently persists new submissions and reads them back. Lifecycle update persistence, outbox dispatch/retry workers, transport endpoints, runtime composition, and CI execution evidence remain follow-up work. The PostgreSQL Testcontainers integration tests are present but have not yet been run in this implementation session.

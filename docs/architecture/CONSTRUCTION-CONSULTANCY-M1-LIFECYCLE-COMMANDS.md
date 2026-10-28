# Construction Consultancy M1 — Lifecycle Command Boundary

## Scope

This change adds an application command boundary for the consultancy engagement transitions already enforced by the domain aggregate:

- submit a draft proposal;
- revise a proposal before activation;
- activate a proposed engagement;
- complete an active engagement;
- cancel an engagement with a reason.

The handler uses trusted tenant/actor context, tenant-scoped aggregate lookup, application authorization, the existing idempotency executor, an update operation on the transaction-scoped consultancy port, and a transactional outbox event. It does not own identity membership, project data, document storage, or finance records.

## Authorization policy

Proposal submission and pre-activation proposal revision require the consultant organization's permission. Activation, completion, and cancellation require the client organization's permission. The authorization adapter must resolve effective permissions from verified identity and organization membership. This policy is a default boundary and should be reviewed against actual contract roles before production deployment; possession of an engagement ID is not authorization.

## Transaction and delivery guarantees

The aggregate update, idempotency response, and outbox insert must share the same database transaction. The SPI default for `updateEngagement` fails closed until an adapter implements it. A persistence adapter must use an optimistic concurrency/version strategy or equivalent row locking so concurrent lifecycle commands cannot overwrite one another. Outbox consumers must be idempotent because delivery is at-least-once.

## Verification and limitations

Unit tests cover command input validation and normalization. Build/test execution was not performed in this environment. The PostgreSQL adapter still needs to implement `updateEngagement` transactionally, including concurrency protection and persistence integration tests, before these lifecycle commands can be enabled in a deployed application. The related PostgreSQL persistence PR #827 has a CI run that failed in the unrelated Supply/Fulfillment reactor (`FulfillmentOrderTest` expected `IllegalArgumentException` but received `IllegalStateException`); that run did not establish consultancy persistence verification.

This is not a claim of Indonesian statutory compliance or Sharia/istisna' compliance. Signed scope snapshots, change-control documents, deliverable persistence/revisions, and compliance evidence remain later milestones.

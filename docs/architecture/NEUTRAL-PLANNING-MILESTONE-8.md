# Neutral Planning — Milestone 8: Accepted Quantities and Correction Review

## Goal

Separate recorded evidence from the value accepted by downstream planning. Quantity measurements and correction links remain immutable audit facts; accepted quantity is a deterministic projection over those facts and explicit review decisions.

## Delivered in this milestone

- QuantityCorrectionDecision records immutable APPROVED or REJECTED review outcomes with reviewer, timestamp, reason, and idempotency key.
- QuantityCorrectionDecisionRepository and JdbcQuantityCorrectionDecisionRepository provide tenant-scoped append and history reads.
- RecordQuantityCorrectionDecision verifies the referenced correction exists in the same tenant and definition before recording the decision.
- AcceptedQuantityProjection chooses a deterministic base observation and applies only explicitly approved correction edges. Pending and rejected corrections remain visible but do not change the accepted quantity.
- GetAcceptedScopeQuantity assembles measurement, correction, and decision histories for a tenant-scoped definition.
- Migration V5 adds decision persistence, foreign-key integrity, idempotency uniqueness, indexes, and immutable-row enforcement.
- Tests cover pending, approved, rejected, and cross-tenant behavior.

## Resolution policy

- No decision means PENDING; a newly recorded correction is never automatically accepted.
- The latest decision per correction wins, ordered by decidedAt, then UUID as a stable tie-breaker. This permits a later review event to supersede an earlier outcome without editing history.
- Only APPROVED corrections are traversed.
- More than one approved successor from the same measurement is a conflict and fails closed rather than silently picking one.
- Original measurements and correction records are not mutated.
- This is a neutral scope-quantity projection; it is not a construction-specific BoQ valuation rule.

## Integration and security boundaries

The projection is exposed as an application use case, but this milestone does not infer user authorization from tenant IDs. Deployments must supply tenant identity from trusted authentication context and enforce reviewer permissions at the API/service boundary. Connecting quantities to progress/schedule rollups should consume this projection rather than raw correction links.

## Validation status

Tests are authored but have not been executed in this remote GitHub editing session. Run the planning Maven reactor and PostgreSQL/Flyway integration tests before marking the PR ready. In particular, validate migration ordering against the repository's full migration set and exercise review races under PostgreSQL.

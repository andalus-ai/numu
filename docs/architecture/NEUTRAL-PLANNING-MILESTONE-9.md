# Neutral Planning — Milestone 9: Quantity-to-Schedule Progress Integration

## Objective

Use the accepted quantity projection from Milestone 8 as the source of actual progress for a schedule activity. Scope references may represent a construction deliverable, software backlog item, procurement package, or another work item.

## Included

- QuantityBasedProgressAssessment converts accepted actual quantity against planned quantity into a bounded completion percentage and preserves overrun quantity separately.
- AssessQuantityBasedActivityProgress resolves the quantity definition and accepted measurement history within an explicit tenant scope.
- The use case verifies the schedule activity's opaque WorkItemReference matches the exact quantity definition scope before calculating progress.
- Tests cover quantity-to-percent conversion, overrun handling, invalid zero planned quantity, and scope mismatch.
- No BoQ pricing, currency, tax, construction rules, or domain-specific dependency is introduced.

## Calculation policy

- Planned quantity must be positive; a zero target has no meaningful percentage denominator and is rejected explicitly.
- Completion percentage is min(accepted quantity / planned quantity × 100, 100), rounded to two decimal places.
- Quantities above plan are not lost: the positive difference is exposed as overrunQuantity.
- Schedule status and time-phased planned percentage continue to use the existing neutral ProgressAssessment policy.
- Only approved correction chains affect the accepted quantity; pending or rejected corrections cannot inflate progress.

## Boundaries and validation

The caller must supply tenant identity from trusted authentication context; a UUID argument is not itself authorization. Reviewer permissions and transport-layer enforcement remain responsibilities of the host API/service layer. This milestone returns an assessment and does not persist derived progress as new evidence.

Tests have been authored but not executed in this remote GitHub editing session. Run the planning Maven reactor and PostgreSQL/Flyway integration suite before marking the PR ready.

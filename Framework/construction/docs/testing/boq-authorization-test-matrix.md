# BOQ revision authorization test matrix

This matrix defines the integration tests required when Syirkah's real identity/permission adapter is wired. The source bundle does not identify the production role schema, so this document deliberately does not invent role names or grant policy.

| Scenario | Setup | Expected result | Must remain unchanged |
|---|---|---|---|
| Denied capture | Authorization rejects `CAPTURE_DRAFT` | Command fails as forbidden | Snapshot rows, revision counter allocation (if transactional), live BOQ state, idempotency result, outbox |
| Denied submission | Authorization rejects `SUBMIT_FOR_REVIEW` | Command fails as forbidden | Snapshot status, live BOQ status, idempotency result, outbox |
| Denied approval | Authorization rejects `APPROVE` | Command fails as forbidden | Snapshot status, live BOQ status, idempotency result, outbox |
| Cross-tenant BOQ | Actor is authorized only in tenant A; BOQ belongs to tenant B | Not found or the platform's non-disclosing equivalent | Tenant B data and all writes |
| Self-approval | Submitter attempts to approve the same revision | Conflict/domain validation failure | Snapshot and live status remain `UNDER_REVIEW`; no approval event |
| Distinct approver | Authorized actor differs from submitter | Approval may proceed if all other rules pass | All writes commit atomically |
| Outbox failure | Authorization succeeds but outbox insert fails | Whole command transaction rolls back | Snapshot, live BOQ, counter/idempotency state as applicable |
| Authorization backend failure | Authorization service errors or times out | Fail closed; propagate mapped error | All command state and outbox |
| Missing trusted context | Tenant or actor absent | Reject before resource access | All command state and outbox |

## Transaction-test requirements

Run each denied/failure case through the application's real transaction runner and inspect the database after the transaction ends. Merely asserting that a `Uni` failed is insufficient: verify that no snapshot, live-state transition, idempotency completion, or outbox event committed.

## Available local check

Run `scripts/check-boq-authorization-contract.sh`. It statically verifies that all three workflow methods invoke the correct authorization action before BOQ loading, that the dependency is mandatory, and that self-approval checks remain present. It is a source-contract check, not a runtime security test.

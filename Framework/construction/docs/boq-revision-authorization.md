# BOQ revision authorization and separation of duties

## Implemented contract

`BoqRevisionAuthorization` is a required dependency of `DefaultPgBoqRevisionOperationsFactory`. It receives the tenant, actor, BOQ, and requested action (`CAPTURE_DRAFT`, `SUBMIT_FOR_REVIEW`, `APPROVE`) and returns a `Uni<Void>` that succeeds only when access is granted. A denial or failure must fail the command; do not convert it to success or fall back to allow-all.

Authorization is invoked before the BOQ row is loaded/locked. This keeps the authorization source pluggable because the source dump does not identify Syirkah's actual role/permission schema. If authorization depends on mutable membership data, the production adapter must choose an appropriate consistency strategy (for example, evaluate grants on the same transaction/connection or revalidate before commit).

## Separation of duties

The domain transition `BoqRevisionSnapshot.approve(actorId, now)` rejects an actor whose ID equals `submittedBy`. The PostgreSQL workflow also checks this before attempting the snapshot status update. This enforces the minimum rule that a submitter cannot approve the same revision; it does not imply a full four-eyes policy across capture, project ownership, financial limits, or tenant administration.

## Required application wiring

1. Obtain actor ID and tenant ID from the authenticated server-side security context, not the request body.
2. Implement `BoqRevisionAuthorization` against Syirkah's actual permission service/schema.
3. Define and test the role/permission matrix for each action; the starter does not invent project roles.
4. Ensure the tenant in the authorization context matches the tenant passed to the BOQ repository.
5. Map authorization denial to the API's established forbidden response without leaking whether another tenant's BOQ exists.
6. Keep the authorization failure inside the command transaction so no snapshot, BOQ state, idempotency result, or outbox event commits after denial.
7. Add integration tests for denied capture, denied submission, denied approval, cross-tenant access, and self-approval.

## Scope and verification

This starter adds the required port and workflow enforcement points, plus the domain self-approval invariant. It cannot provide the production authorization adapter without the real Syirkah identity/permission model. The source bundle is not a checked-out project, so compile and integration tests against the real application remain required.

## Authorization test matrix

See `db/test/boq-authorization-test-matrix.md` for denied-action, cross-tenant, self-approval, backend-failure, and rollback scenarios. Run `scripts/check-boq-authorization-contract.sh` for a fast static guard against accidental removal or reordering of authorization calls. This check is intentionally not described as a substitute for tests using the production identity adapter and transaction runner.

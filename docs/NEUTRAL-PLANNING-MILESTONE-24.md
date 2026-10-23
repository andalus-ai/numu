# Neutral Planning — Milestone 24: Schedule Change Governance

## Goal

Provide a domain-neutral, auditable lifecycle for proposed schedule changes. Governance is separated from the change-impact calculation so applications can supply their own persistence, authorization, notification, and workflow adapters.

## Lifecycle

- **DRAFT** — request exists, with explicit tenant/structure/plan scope, base and target revisions, requester, timestamp, and rationale.
- **SUBMITTED** — immutable change-impact evidence has been attached and its scope/revisions validated.
- **APPROVED / REJECTED** — a reviewer has made a terminal decision.
- **STALE** — the live plan revision has moved beyond the evidence's base revision.
- **SUPERSEDED** — the submitted/stale request is no longer the active proposal.

## Controls

- Submission requires an impact report for exactly the requested tenant, structure, plan, base revision, and target revision.
- Decision evidence is appended immutably and includes actor, timestamp, rationale, outcome, and observed live revision.
- A requester cannot approve or reject their own request.
- Approval/rejection is rejected if the live plan revision no longer matches the report's base revision.
- A repeated decision with the same decision ID and exact payload is idempotent; reusing an ID with a different payload is rejected.
- A stale request can be superseded with an audit entry.
- State transition methods return new immutable aggregates; persistence and compare-and-set/optimistic locking remain responsibilities of the application adapter.

## Boundaries

This milestone does not introduce a database, identity provider, authorization engine, or automatic plan mutation. A domain decision record documents the actor but does not authenticate that actor; callers must authorize actors at the application boundary. The aggregate's optimistic persistence requirement must be enforced by adapters to prevent concurrent decisions from overwriting one another.

## Verification

Unit tests are authored for lifecycle transitions, stale evidence, separation of duties, idempotent decisions, and scope checks. They must run in CI before the milestone is treated as verified.

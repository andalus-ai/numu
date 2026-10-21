# Neutral Planning Milestone 5 — Durable Progress Persistence

## What this milestone adds

- An application-level `ActivityProgressRepository` port for appending observations, retrieving full activity history, and reading the latest observation on or before a business date.
- A PostgreSQL/JDBC implementation that writes observations durably and uses `ON CONFLICT` on the per-plan idempotency key to make concurrent replays safe.
- Semantic replay comparison: same key and same business payload returns `false`; same key with different payload is rejected.
- Schedule persistence now updates retained activity rows in place, inserts new activities, and deletes only activities removed from the aggregate. The database foreign key rejects deletion of an activity with progress evidence, rolling back the whole schedule revision.
- Integration tests exercise persistent history, replay handling, changed activity data without loss of history, immutable rows, and safe rejection of activity deletion.

## Transaction and consistency boundaries

Schedule aggregate saves remain a single JDBC transaction with optimistic plan revision checks. Progress append is an atomic single-statement write; idempotency uniqueness is enforced by PostgreSQL. A successful progress append and a separate schedule save are not a combined transaction, which is appropriate because progress is an independent fact rather than a schedule-edit side effect.

## Remaining production hardening

This adapter currently scopes progress by globally unique plan ID, not an authenticated tenant principal. Before multi-tenant exposure, enforce tenant authorization in application use cases and tenant predicates in every repository operation, ideally with database row-level security or tenant-bearing keys. Also add API/use-case wiring, audit policy for corrections (corrections should be new observations, not updates), operational retention policy, and CI verification against PostgreSQL.

## Validation

Integration tests were added, but have not been executed by the authoring environment. Verify the planning Maven reactor and PostgreSQL Testcontainers/Quarkus Dev Services before marking the PR ready.

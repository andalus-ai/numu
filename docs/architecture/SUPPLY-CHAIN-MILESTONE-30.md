# Supply Chain Milestone 30 — Bounded Audit History

## Scope

M30 adds stable cursor-based pagination for warehouse recovery operator audit history. It supports operator consoles and export jobs without forcing callers to load an unbounded audit table into memory.

## Contract

- The application facade requires a tenant ID and a page size from 1 to 500.
- The JDBC adapter queries only the requested tenant and orders newest first by `occurred_at` and the unique audit row `id`.
- The opaque URL-safe cursor encodes the last returned timestamp and row ID. The next page uses a strict tuple comparison, so rows sharing a timestamp are not skipped or repeated.
- The adapter fetches at most `limit + 1` rows to determine whether another page exists. The returned page contains at most `limit` events.
- The existing `auditEvents(tenantId, limit)` API remains for simple bounded reads; callers needing more than one page should use `auditPage`.

## Usage

Call `WarehouseRecoveryOperationsService.auditPage(tenantId, limit, cursor)`. Start with `cursor = null`; when `page.hasMore()` is true, pass `page.nextCursor()` to fetch the next page. Cursors are tenant-scoped in the query and must be treated as opaque continuation tokens, not authorization credentials.

Export jobs should stream each page to the destination rather than aggregate the entire history in memory. Keep exports access-controlled and record export activity in the deployment's own security/audit boundary where required.

## Validation

An opt-in PostgreSQL integration test covers bounded page sizes, cursor continuation, no duplicate events between pages, and malformed cursor rejection. It requires a disposable database with warehouse migrations V1–V6 and `NUMOU_TEST_POSTGRES_URL` configured. Maven and live PostgreSQL execution have not been run in this editing environment.

## Security and retention

Pagination is not a substitute for authorization: callers must derive the tenant from the authenticated operator context. Cursors do not grant access by themselves. Production database roles should continue to prevent UPDATE/DELETE on the audit table where feasible. Retention and archival policies remain deployment-specific.

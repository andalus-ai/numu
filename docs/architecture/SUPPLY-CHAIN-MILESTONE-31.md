# Supply Chain Milestone 31 — Recovery audit export and operational controls

## Scope

Adds a bounded CSV export adapter for the tenant-scoped warehouse recovery operator audit trail. It complements M28–M30 transactional audit writes and keyset pagination without changing the audit schema or deleting history.

## Export contract

- The CSV exporter streams pages directly to an Appendable and never loads the full audit history into memory.
- Each invocation is capped at 100,000 events and each repository page at 500 events. Callers can resume with the returned opaque cursor.
- A returned cursor means the export is incomplete. Persist it alongside export-job state and continue into a new output chunk; each chunk has its own CSV header.
- Export fails closed if a repository page crosses tenant boundaries, repeats a cursor, or reports an empty page with a continuation cursor.
- CSV values are quoted and embedded quotes escaped. Values beginning with common spreadsheet formula markers are prefixed with an apostrophe to reduce formula-injection risk.
- The exporter does not authenticate or authorize callers. The API boundary must derive tenant ID and actor identity from trusted authentication context, authorize export access separately from replay, and record export access in the platform security audit facility where available. Never trust tenant or actor identity supplied only in request-body fields.

## Retention and archival policy

Audit rows are evidence of successful operator state transitions. This milestone intentionally introduces no automatic purge, retention default, or destructive migration. Before production retention is enabled, owners must approve a policy covering regulatory obligations, incident-response needs, backup retention, archive encryption, restore testing, and deletion authorization. Prefer a reviewed archive-and-verify workflow before any deletion; preserve tenant identity, event timestamps, transition versions, actor, reason, and correlation identifiers in the archive.

## Database and operational security

- Runtime application credentials should have only the privileges required by the application. For the audit table, prefer SELECT and INSERT; do not grant UPDATE or DELETE to ordinary runtime roles.
- Schema migration and break-glass maintenance should use separate credentials with audited access.
- Restrict export authorization to a least-privilege role, apply rate limits at the API boundary, and write exports to access-controlled storage with encryption in transit and at rest.
- Audit exports contain operator identities and potentially sensitive operational reasons. Avoid public URLs, uncontrolled temporary files, and application logs containing full CSV content.
- Use tenant-scoped repository methods and opaque continuation cursors. Treat cursors as continuation tokens, not authorization credentials.

## Verification

Unit tests are intended to cover CSV escaping/formula safety, bounded paging, cursor continuation, tenant-boundary defense, and invalid limits. PostgreSQL integration and Maven reactor tests remain required in CI/deployment validation; no test execution is claimed by this change.

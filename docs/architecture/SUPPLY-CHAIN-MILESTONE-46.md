# Supply-chain milestone 46: end-to-end CSV content and manifest integrity

## Objective

Extend the PostgreSQL/filesystem recovery integration suite beyond an empty CSV export to verify a real audit row across the worker, artifact store, and archive manifest.

## Coverage

- Feed a tenant-matching audit event through the worker's bounded-page provider.
- Verify event count and completed status persisted by the real JDBC job repository.
- Read the resulting artifact from a new filesystem-store instance.
- Assert the CSV header, formula-injection neutralization, and escaping of embedded quotes and commas.
- Compare the manifest event count and byte size with the completed job and verify the manifest SHA-256 digest matches the artifact.

## Scope

This is an opt-in PostgreSQL integration test using an isolated schema and a temporary filesystem artifact root. The audit-page source is a controlled test double, so this validates export serialization and persistence boundaries, not the upstream audit-event query implementation.

## Validation

Maven tests were not run while authoring. Run the JDBC module's Surefire suite with the PostgreSQL environment variables configured, plus the warehouse application and filesystem artifact-store suites.
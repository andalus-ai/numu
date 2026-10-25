# Supply-chain milestone 49: conflicting manifest recovery

## Objective

Prove that a worker restart after manifest persistence but before the export job reaches COMPLETED is idempotent only when the persisted manifest matches the verified artifact.

## Regression coverage

The application crash-window test first injects a persistence failure when saving the final COMPLETED job state, after the manifest has been stored. It then changes the saved manifest SHA-256 metadata to simulate conflicting immutable metadata and retries the worker.

The retry must:
- verify the durable artifact without appending its chunk again;
- reject the existing manifest instead of overwriting it or inserting a duplicate;
- mark the export job FAILED with the deterministic processing failure code;
- preserve the conflicting manifest as evidence for investigation.

The existing adjacent test continues to cover the successful matching-manifest recovery path, including completion without duplicate artifact writes or manifest rows.

## Validation

This change adds an application-level regression test. Maven tests and CI were not run as part of authoring this milestone; run the warehouse application module tests before merge.

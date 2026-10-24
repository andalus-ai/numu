# Supply-chain milestone 42: worker crash-window recovery tests

## Scope

Add deterministic application-level fault injection around two database failure windows that occur after artifact bytes are durable:

- Manifest insert fails after the final artifact chunk and VERIFYING transition. A retry must reclaim the job, verify existing bytes, insert the manifest, and complete without appending the artifact twice.
- Completion update fails after the manifest is committed. A retry must verify the same artifact, accept the matching manifest, and complete without duplicate artifact bytes or duplicate manifest rows.

The tests use in-memory ports with injected persistence failures. They validate worker orchestration and retry semantics, not PostgreSQL transaction behavior; combine them with the opt-in PostgreSQL repository integration tests from milestone 41.

## Validation

Maven tests were not run while authoring this milestone. Run the warehouse application module tests and the PostgreSQL integration suite before merging. Artifact-store process-crash tests and production object-store conditional-write tests remain separate concerns.

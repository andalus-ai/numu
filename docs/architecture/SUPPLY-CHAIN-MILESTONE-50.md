# Supply-chain milestone 50: artifact/checkpoint integrity

## Objective

Exercise failure handling when a durable artifact's inspected metadata disagrees with the export checkpoint or when its checksum does not match the bytes read back from storage.

## Regression coverage

Two application-level worker tests use a tampering artifact-store decorator around the deterministic in-memory store:

- **Byte-size mismatch:** inspection reports a size different from the committed checkpoint. The worker must fail the job before manifest persistence.
- **Checksum mismatch:** inspection reports a valid-format but incorrect SHA-256 digest. The verifier reads the actual bytes, rejects the digest, and the worker records `ARCHIVE_CHECKSUM_MISMATCH` without persisting a manifest.

Both tests assert that the already-written artifact is not appended or rewritten during verification failure. The decorator alters only returned metadata; it does not mutate the underlying artifact.

## Validation

Maven tests and CI were not run while authoring this milestone. Run the warehouse application module test suite before merge.

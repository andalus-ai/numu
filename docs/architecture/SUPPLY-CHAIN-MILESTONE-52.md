# Supply Chain Milestone 52 — Artifact corruption detection

## Scope

Verify all durable chunk receipts against the artifact before exposing a filesystem artifact through `inspect()` or `open()`, and before appending another chunk.

## Behavior

- Receipt records are parsed using a strict canonical format and checked for valid numeric fields, digest syntax, overflow, and artifact bounds.
- Each receipt's byte range is read from the artifact and its SHA-256 digest is compared with the committed receipt.
- Modified or truncated bytes covered by a receipt fail with `IOException`; the adapter does not silently rewrite those bytes during a retry.
- Missing receipts remain tolerated during validation. This is intentional because a crash can occur after artifact bytes are forced but before the receipt is committed. `appendChunk()` remains responsible for recovering that window when the caller retries the matching chunk.
- Validation covers ranges described by committed receipts; it does not claim to detect arbitrary corruption in bytes that have no committed receipt.

## Operator guidance

If receipt verification fails, preserve the export directory and logs for investigation. Do not manually edit receipts or artifact bytes to bypass verification. Compare the persisted job checkpoint, chunk identity, receipt metadata, and artifact bytes. If integrity cannot be established, restore from a trusted backup or restart with a new export identity. The filesystem adapter is intended for single-host deployments; use a distributed-storage adapter for multi-host writers.

## Tests

Regression coverage modifies bytes after receipt commit and truncates a receipt-backed artifact. Both `inspect()` and `open()` must reject the modified artifact; retrying a truncated artifact must fail without restoring or rewriting it. Existing missing-receipt recovery tests continue to exercise the separate crash window.

## Validation

Maven tests were not run in this environment. CI status should be reviewed before merge.

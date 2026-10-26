# Supply Chain Milestone 56 — Filesystem artifact tail recovery

## Goal

Strengthen regression coverage for filesystem artifact recovery when a process stops during a later chunk append or during the temporary-receipt-to-durable-receipt transition.

## Findings and coverage

The filesystem adapter already supports a narrow recovery rule: an unreceipted tail may be completed only when its bytes are an exact prefix of the retried chunk. Previously committed receipt ranges are checked before the retry is accepted. This milestone adds focused tests for the less-covered nonzero-offset case rather than adding a second recovery implementation.

- Recover a partial second chunk after adapter restart while preserving the already receipted first chunk.
- Reject a conflicting partial tail without overwriting either the committed prefix or the conflicting tail.
- Recover complete durable bytes when a process left a temporary receipt file before the atomic receipt rename; only the committed receipt is treated as evidence.

## Compatibility and boundaries

- No public port, receipt format, persistence schema, or production dependency changes.
- The tests exercise the owning filesystem adapter using a temporary directory; no cloud or external service is required.
- Recovery remains fail-closed for a conflicting tail. It must not rewrite bytes covered by a committed receipt or guess which content the caller intended.

## Validation

The focused filesystem adapter tests were updated, but Maven was not run in this environment. Run the `numou-warehouse-artifact-filesystem` test reactor and inspect repository CI before merge.

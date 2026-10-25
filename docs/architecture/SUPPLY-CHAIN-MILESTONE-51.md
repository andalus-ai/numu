# Supply Chain Milestone 51 — Filesystem receipt recovery hardening

## Scope

Harden the single-host filesystem implementation of the warehouse recovery-audit artifact store against malformed or inconsistent durable chunk receipts.

## Behavior

- A committed chunk receipt must contain exactly three fields (expected offset, byte length, lowercase SHA-256) followed by one final newline.
- Truncated records, extra fields, invalid digest syntax, mismatched offsets/lengths, and digest conflicts fail closed with an `IOException`.
- When a receipt is absent but the artifact contains the exact expected bytes at the requested offset, the store can reconstruct the receipt without appending duplicate bytes.
- When existing receipt evidence conflicts with the retry, the store does not rewrite artifact bytes.

## Operational remediation

Do not manually edit a receipt to force an export to continue. Preserve the artifact directory for investigation, compare the durable artifact bytes with the export's persisted checkpoint and chunk identity, and restore from a known-good backup or restart the export with a new export identity if integrity cannot be established. For multi-host deployments, use a storage adapter designed for shared/distributed coordination rather than this single-host filesystem adapter.

## Validation

Tests cover malformed receipts with extra fields and corrupted digests, asserting retries fail and the original artifact bytes remain unchanged. Existing tests cover missing-receipt recovery when durable bytes match and rejection of conflicting retries.

Maven tests were not run as part of this change; CI status must be checked on the pull request.

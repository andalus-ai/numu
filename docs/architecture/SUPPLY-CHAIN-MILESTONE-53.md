# Supply-chain milestone 53: receipt range consistency

## Scope

Strengthen validation of durable chunk receipts for filesystem recovery-audit artifacts. Each receipt is validated for canonical syntax, bounds, and a SHA-256 digest matching its byte range; this milestone additionally validates the relationship between committed ranges.

## Range invariants

- Receipts are sorted by offset before consistency checks.
- Each committed range must start exactly where the preceding committed range ends. Overlaps and holes between committed receipts are rejected, even when each individual digest matches its claimed bytes.
- The first committed range starts at offset zero.
- The artifact may contain an unreceipted tail after the last committed range. This is a valid interruption window: a process can force chunk bytes and stop before atomically committing the receipt. A retry may reconstruct the receipt if the bytes and expected offset match.
- Detection does not silently rewrite artifact bytes. Contradictory committed evidence fails closed.

## Durability assumptions and recovery boundary

The adapter targets a single host and a filesystem that provides the expected semantics for file-channel force operations and atomic rename where supported. It forces artifact data before writing the receipt, forces the temporary receipt before rename, and attempts to force containing directories. Some filesystems/platforms do not allow directories to be opened as channels, so power-loss guarantees are not identical everywhere.

A missing final receipt with matching trailing bytes is a recoverable interruption. Malformed receipts, overlapping ranges, gaps between committed ranges, a receipt extending past EOF, or digest mismatch are inconsistent/corrupt state and are not automatically repaired. This local adapter is not a multi-host object-store durability guarantee.

## Validation

Added regression tests for corruption in an earlier chunk discovered while retrying the latest chunk, overlapping ranges with individually valid digests, and gaps between committed receipts. Existing tests cover recovery after artifact bytes are written but before the receipt is committed. Maven tests were not run for this change; check CI on the pull request.

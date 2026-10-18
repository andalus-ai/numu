## Next slice: immutable export revisions and long-term retention

This extends the existing Syirkah evidence-export lifecycle so that a delivered evidence package remains verifiable after later corrections, re-exports, or archival of the original application data.

The core rules are:

- A ready export is immutable.
- A replacement export gets a new identity and explicitly references the export it supersedes.
- Supersession does not erase or invalidate the historical package.
- Retention or expiry affects availability, not the historical facts recorded in the manifest.
- Archived evidence must be verified from the preserved package and its canonicalization rules, without rerunning the close calculation.

### 1. Model export revision lineage

Use an explicit revision record instead of overwriting the existing export record.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.Objects;

public record SyirkahEvidenceExportRevision(
        String tenantId,
        String ledgerId,
        String exportId,
        String rootType,
        String rootId,
        int revisionNumber,
        String supersedesExportId,
        String manifestHash,
        String bundleContentHash,
        String schemaVersion,
        SyirkahEvidenceExportRevisionStatus status,
        Instant createdAt,
        Instant readyAt,
        String createdBy
) {
    public SyirkahEvidenceExportRevision {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(ledgerId);
        Objects.requireNonNull(exportId);
        Objects.requireNonNull(rootType);
        Objects.requireNonNull(rootId);
        Objects.requireNonNull(manifestHash);
        Objects.requireNonNull(bundleContentHash);
        Objects.requireNonNull(schemaVersion);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);

        if (revisionNumber < 1) {
            throw new IllegalArgumentException(
                    "Revision number must be positive."
            );
        }

        if (revisionNumber == 1 && supersedesExportId != null) {
            throw new IllegalArgumentException(
                    "The first revision cannot supersede another export."
            );
        }

        if (revisionNumber > 1 && supersedesExportId == null) {
            throw new IllegalArgumentException(
                    "A later revision must identify the prior export."
            );
        }
    }
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahEvidenceExportRevisionStatus {
    REQUESTED,
    COLLECTING,
    VALIDATING,
    READY,
    READY_WITH_LIMITATIONS,
    FAILED,
    EXPIRED,
    ARCHIVED
}
```

`revisionNumber` identifies the order of exports for a particular evidence root. `supersedesExportId` records lineage; it does not mean that the earlier package should be deleted or rewritten.

### 2. Define immutable retention metadata

Retention metadata should be separate from the evidence manifest. This allows storage policy to evolve without changing the hash of the evidence originally delivered.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;

public record SyirkahEvidenceRetentionRecord(
        String tenantId,
        String ledgerId,
        String exportId,
        String retentionPolicyId,
        String retentionPolicyVersion,
        Instant retentionStartedAt,
        Instant retainUntil,
        Instant legalHoldStartedAt,
        Instant legalHoldReleasedAt,
        String archiveReference,
        String archiveContentHash,
        Instant archivedAt,
        Instant lastIntegrityCheckAt,
        String lastIntegrityCheckResult
) {
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahEvidenceRetentionDecision {
    RETAIN,
    ARCHIVE,
    EXPIRE,
    BLOCKED_BY_LEGAL_HOLD,
    BLOCKED_BY_RETENTION_POLICY
}
```

Retention decisions should be derived from the applicable policy version, the evidence category, and any legal hold. The export service must not independently decide that an artifact can be deleted simply because its download link has expired.

### 3. Add repository ports

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;
import java.util.Optional;

public interface SyirkahEvidenceExportRevisionRepository {

    Optional<SyirkahEvidenceExportRevision> findById(
            String tenantId,
            String ledgerId,
            String exportId
    );

    Optional<SyirkahEvidenceExportRevision> findLatest(
            String tenantId,
            String ledgerId,
            String rootType,
            String rootId
    );

    List<SyirkahEvidenceExportRevision> findRevisionHistory(
            String tenantId,
            String ledgerId,
            String rootType,
            String rootId
    );

    void insertWithinTransaction(
            SyirkahEvidenceExportRevision revision
    );

    void transitionStatusWithinTransaction(
            String tenantId,
            String ledgerId,
            String exportId,
            SyirkahEvidenceExportRevisionStatus expectedStatus,
            SyirkahEvidenceExportRevisionStatus newStatus
    );
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.Optional;

public interface SyirkahEvidenceRetentionRepository {

    Optional<SyirkahEvidenceRetentionRecord> find(
            String tenantId,
            String ledgerId,
            String exportId
    );

    void insertWithinTransaction(
            SyirkahEvidenceRetentionRecord record
    );

    void updateRetentionMetadataWithinTransaction(
            SyirkahEvidenceRetentionRecord record
    );
}
```

The revision repository must never expose a general-purpose update or delete operation for a ready revision’s manifest hash, bundle hash, root identity, or supersession lineage. Retention metadata may change under controlled policy operations, but the delivered evidence payload remains immutable.

### 4. Create a revision command and service

A revision request should explicitly identify the evidence root and, when applicable, the prior export.

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahEvidenceExportRevisionCommand(
        String tenantId,
        String ledgerId,
        String rootType,
        String rootId,
        String supersedesExportId,
        String requestedBy,
        String idempotencyKey
) {
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public interface SyirkahEvidenceExportRevisionService {

    SyirkahEvidenceExportRevision createRevision(
            SyirkahEvidenceExportRevisionCommand command
    );
}
```

The implementation should run the following steps within a transaction or a durable workflow with equivalent concurrency protection:

1. Claim the idempotency key in tenant and ledger scope.
2. Lock the evidence root’s export-revision sequence.
3. If a prior export is supplied, verify that it exists in the same tenant and ledger and belongs to the same root.
4. Allocate the next revision number atomically.
5. Insert the new revision in `REQUESTED` state.
6. Persist an outbox event for asynchronous collection.
7. Commit.

Do not rely on `findLatest()` followed by an unprotected insert; concurrent requests could allocate the same revision number. Enforce uniqueness in persistence.

Recommended constraints:

```
CREATE UNIQUE INDEX uq_syirkah_evidence_export_revision
    ON syirkah_evidence_export_revision
       (tenant_id, ledger_id, root_type, root_id, revision_number);

CREATE UNIQUE INDEX uq_syirkah_evidence_export_id
    ON syirkah_evidence_export_revision
       (tenant_id, ledger_id, export_id);

CREATE UNIQUE INDEX uq_syirkah_evidence_export_idempotency
    ON syirkah_evidence_export_idempotency
       (tenant_id, ledger_id, idempotency_key);
```

Adapt these to the project’s existing schema and migration conventions rather than assuming these tables already exist.

### 5. Enforce valid supersession

A new revision must not supersede an unrelated export or create a cycle in the lineage graph.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.Objects;

public final class SyirkahEvidenceSupersessionValidator {

    public void validate(
            SyirkahEvidenceExportRevision prior,
            String tenantId,
            String ledgerId,
            String rootType,
            String rootId
    ) {
        Objects.requireNonNull(prior);

        if (!prior.tenantId().equals(tenantId)
                || !prior.ledgerId().equals(ledgerId)) {
            throw new IllegalArgumentException(
                    "The prior export is outside the requested scope."
            );
        }

        if (!prior.rootType().equals(rootType)
                || !prior.rootId().equals(rootId)) {
            throw new IllegalArgumentException(
                    "The prior export belongs to a different evidence root."
            );
        }

        if (prior.status() == SyirkahEvidenceExportRevisionStatus.REQUESTED
                || prior.status() == SyirkahEvidenceExportRevisionStatus.COLLECTING
                || prior.status() == SyirkahEvidenceExportRevisionStatus.VALIDATING) {
            throw new IllegalStateException(
                    "An incomplete export cannot be treated as a completed prior revision."
            );
        }
    }
}
```

This validator is a domain guard, not the concurrency control. The repository or transaction layer must also ensure that the requested predecessor remains valid when the new revision commits.

An expired or archived export may still be a valid historical predecessor if its lineage and identity remain available. Expiry alone must not sever the chain.

### 6. Define archive storage and verification

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.Optional;

public interface SyirkahEvidenceArchiveStore {

    SyirkahEvidenceArchiveReceipt archive(
            String tenantId,
            String ledgerId,
            String exportId,
            byte[] canonicalBundle
    );

    Optional<byte[]> retrieve(
            String tenantId,
            String ledgerId,
            String archiveReference
    );
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;

public record SyirkahEvidenceArchiveReceipt(
        String archiveReference,
        String archiveContentHash,
        String storageProvider,
        String storageVersion,
        Instant archivedAt
) {
}
```

The archive adapter should provide durable, tenant-scoped storage and preserve the exact canonical bundle bytes. Where the storage system supports immutable retention or object locking, use it according to the organization’s retention requirements.

Archive verification should:

- Retrieve the archived bytes.
- Recompute the archive content hash.
- Compare it with the stored retention record.
- Parse the manifest using a supported schema version.
- Verify the bundle using the standalone verifier from the previous slice.
- Record the verifier version, timestamp, and findings.

If the original accounting database has been archived or deleted, the verifier should still be able to validate the package’s integrity using its own contents. It cannot independently establish that a historical source record existed if that source record was never included or preserved.

### 7. Handle expiry without destroying evidence

Expiry should primarily govern access or retrieval policy, not rewrite historical evidence.

For example:

- Download-link expiry: revoke a temporary download URL while retaining the evidence package.
- Archive transition: move the package to long-term storage and verify it after transfer.
- Retention expiry: evaluate whether deletion is legally and operationally permitted.
- Legal hold: block deletion while the hold is active.
- Missing archive object: flag the evidence as unavailable; do not mark it verified merely because the old manifest still exists.

If deletion is permitted, record a separate retention event with the policy basis and authorization. Do not modify the original manifest to suggest that the package was always incomplete or never existed.

### 8. Acceptance tests

| Scenario                                | Required behavior                                             |
| --------------------------------------- | ------------------------------------------------------------- |
| Create first export                     | Revision number is `1`, with no predecessor                   |
| Create replacement export               | New ID and revision; predecessor remains immutable            |
| Concurrent revision requests            | Unique sequence enforced without duplicate revisions          |
| Supersede an export from another tenant | Reject                                                        |
| Supersede an unrelated evidence root    | Reject                                                        |
| Archive bundle and retrieve it          | Archive hash and bundle verification succeed                  |
| Archive bytes are altered               | Integrity verification fails                                  |
| Download link expires                   | Historical package remains unchanged                          |
| Legal hold is active                    | Deletion is blocked                                           |
| Retention policy version is unavailable | Record a verification limitation                              |
| Original database is unavailable        | Verify preserved package content without rerunning accounting |
| Earlier revision has expired            | Preserve lineage and report availability accurately           |

All code above is a proposed extension; it has not been compiled against the actual repository.

Next slice: implement a retention-policy evaluator and a scheduled archive-integrity sweep, including retry-safe archive transitions, legal-hold checks, and auditable handling of missing or corrupted archive objects.

/////////////

## Next slice: retention-policy evaluation and archive-integrity sweeps

This slice adds two controlled background workflows to the existing evidence-export system:

1. Retention evaluation determines whether each export must remain in primary storage, should be archived, or may be considered for expiry.
2. Archive-integrity sweeps periodically verify that archived packages still exist and match their recorded hashes.

Both workflows must be retry-safe, tenant-scoped, auditable, and independent of accounting close execution. A retention decision must never rewrite the original evidence manifest or alter a finalized accounting snapshot.

### 1. Define a versioned retention policy

Keep policy configuration immutable once a policy version is published. Retention periods below are illustrative configuration fields, not prescribed durations.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Duration;
import java.util.Objects;

public record SyirkahEvidenceRetentionPolicy(
        String policyId,
        String policyVersion,
        Duration primaryStorageDuration,
        Duration minimumRetentionDuration,
        boolean archiveRequired,
        boolean deletionAllowed,
        boolean legalHoldRequired,
        String requiredArchiveIntegrityAlgorithm
) {
    public SyirkahEvidenceRetentionPolicy {
        Objects.requireNonNull(policyId);
        Objects.requireNonNull(policyVersion);
        Objects.requireNonNull(primaryStorageDuration);
        Objects.requireNonNull(minimumRetentionDuration);
        Objects.requireNonNull(requiredArchiveIntegrityAlgorithm);

        if (primaryStorageDuration.isNegative()
                || minimumRetentionDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "Retention durations cannot be negative."
            );
        }

        if (deletionAllowed && archiveRequired) {
            throw new IllegalArgumentException(
                    "An archive-required policy must define the archive retention lifecycle."
            );
        }
    }
}
```

The exact policy should be resolved by evidence category and applicable tenant or jurisdiction configuration. Do not hardcode one retention duration for all audit packages.

### 2. Add a retention-policy resolver

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.Optional;

public interface SyirkahEvidenceRetentionPolicyResolver {

    Optional<SyirkahEvidenceRetentionPolicy> resolve(
            String tenantId,
            String ledgerId,
            String rootType,
            String rootId,
            String exportId
    );
}
```

If the historical policy version cannot be resolved, the evaluator must return a limitation or blocked decision. It should not silently apply the current policy as if it were the original one.

### 3. Model legal holds and retention evaluations

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;

public record SyirkahEvidenceLegalHold(
        String tenantId,
        String ledgerId,
        String holdId,
        String exportId,
        String reasonCode,
        String authorityReference,
        Instant startedAt,
        Instant releasedAt,
        String createdBy
) {
    public boolean isActiveAt(Instant instant) {
        return !startedAt.isAfter(instant)
                && (releasedAt == null || releasedAt.isAfter(instant));
    }
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.List;

public record SyirkahEvidenceRetentionEvaluation(
        String tenantId,
        String ledgerId,
        String exportId,
        String policyId,
        String policyVersion,
        SyirkahEvidenceRetentionDecision decision,
        Instant evaluatedAt,
        Instant eligibleAt,
        List<String> reasonCodes
) {
    public SyirkahEvidenceRetentionEvaluation {
        reasonCodes = List.copyOf(reasonCodes);
    }
}
```

Use the hold’s authoritative release timestamp and the organization’s legal-hold service as the source of truth. A hold record that is merely absent from one database query must not automatically be interpreted as proof that no hold exists.

### 4. Implement a deterministic retention evaluator

The evaluator calculates a decision from the export’s creation and readiness times, the applicable policy, archive state, and active legal holds.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SyirkahEvidenceRetentionEvaluator {

    public SyirkahEvidenceRetentionEvaluation evaluate(
            SyirkahEvidenceExportRevision revision,
            SyirkahEvidenceRetentionPolicy policy,
            List<SyirkahEvidenceLegalHold> holds,
            SyirkahEvidenceRetentionRecord retention,
            Instant now
    ) {
        List<String> reasons = new ArrayList<>();

        boolean activeHold = holds.stream()
                .anyMatch(hold -> hold.isActiveAt(now));

        if (activeHold) {
            reasons.add("ACTIVE_LEGAL_HOLD");

            return result(
                    revision,
                    policy,
                    SyirkahEvidenceRetentionDecision
                            .BLOCKED_BY_LEGAL_HOLD,
                    now,
                    null,
                    reasons
            );
        }

        if (revision.status()
                != SyirkahEvidenceExportRevisionStatus.READY
                && revision.status()
                != SyirkahEvidenceExportRevisionStatus.READY_WITH_LIMITATIONS
                && revision.status()
                != SyirkahEvidenceExportRevisionStatus.ARCHIVED) {
            reasons.add("EXPORT_NOT_READY");

            return result(
                    revision,
                    policy,
                    SyirkahEvidenceRetentionDecision
                            .BLOCKED_BY_RETENTION_POLICY,
                    now,
                    null,
                    reasons
            );
        }

        Instant retentionStart = revision.readyAt() != null
                ? revision.readyAt()
                : revision.createdAt();

        Instant archiveEligibleAt =
                retentionStart.plus(policy.primaryStorageDuration());

        Instant minimumRetentionUntil =
                retentionStart.plus(policy.minimumRetentionDuration());

        if (policy.archiveRequired()
                && now.isBefore(archiveEligibleAt)) {
            reasons.add("PRIMARY_STORAGE_PERIOD_ACTIVE");

            return result(
                    revision,
                    policy,
                    SyirkahEvidenceRetentionDecision.RETAIN,
                    now,
                    archiveEligibleAt,
                    reasons
            );
        }

        boolean archived = retention != null
                && retention.archiveReference() != null
                && retention.archiveContentHash() != null;

        if (policy.archiveRequired() && !archived) {
            reasons.add("ARCHIVE_REQUIRED");

            return result(
                    revision,
                    policy,
                    SyirkahEvidenceRetentionDecision.ARCHIVE,
                    now,
                    archiveEligibleAt,
                    reasons
            );
        }

        if (now.isBefore(minimumRetentionUntil)) {
            reasons.add("MINIMUM_RETENTION_ACTIVE");

            return result(
                    revision,
                    policy,
                    SyirkahEvidenceRetentionDecision.RETAIN,
                    now,
                    minimumRetentionUntil,
                    reasons
            );
        }

        if (!policy.deletionAllowed()) {
            reasons.add("DELETION_NOT_PERMITTED_BY_POLICY");

            return result(
                    revision,
                    policy,
                    SyirkahEvidenceRetentionDecision
                            .BLOCKED_BY_RETENTION_POLICY,
                    now,
                    minimumRetentionUntil,
                    reasons
            );
        }

        reasons.add("RETENTION_PERIOD_ELAPSED");

        return result(
                revision,
                policy,
                SyirkahEvidenceRetentionDecision.EXPIRE,
                now,
                minimumRetentionUntil,
                reasons
        );
    }

    private SyirkahEvidenceRetentionEvaluation result(
            SyirkahEvidenceExportRevision revision,
            SyirkahEvidenceRetentionPolicy policy,
            SyirkahEvidenceRetentionDecision decision,
            Instant evaluatedAt,
            Instant eligibleAt,
            List<String> reasons
    ) {
        return new SyirkahEvidenceRetentionEvaluation(
                revision.tenantId(),
                revision.ledgerId(),
                revision.exportId(),
                policy.policyId(),
                policy.policyVersion(),
                decision,
                evaluatedAt,
                eligibleAt,
                reasons
        );
    }
}
```

Important integration detail: the sample falls back to `createdAt` if `readyAt` is absent. In production, do not apply retention timing to an export without a trustworthy readiness timestamp unless the applicable policy explicitly defines a fallback. Otherwise, record a blocking finding.

Also, `EXPIRE` here means eligible for a separately authorized expiry workflow, not permission to delete immediately. The expiry worker must recheck legal holds, policy, archive availability, and any preservation requirements immediately before acting.

### 5. Persist evaluations as append-only decisions

Each evaluation should be recorded with its input policy version, timestamp, decision, reason codes, and a stable evaluation ID. Re-evaluation creates a new record; it does not overwrite the earlier decision.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.Optional;

public interface SyirkahEvidenceRetentionEvaluationRepository {

    Optional<SyirkahEvidenceRetentionEvaluation> findById(
            String tenantId,
            String ledgerId,
            String evaluationId
    );

    void appendWithinTransaction(
            String evaluationId,
            SyirkahEvidenceRetentionEvaluation evaluation
    );
}
```

For idempotent processing, derive a stable job identity from the tenant, ledger, export, policy version, and scheduled evaluation window—or persist a unique job key. Keep separate IDs for the evaluation record and the worker attempt.

### 6. Add a retry-safe archive workflow

Archiving must not mark a package archived merely because an upload request returned successfully. The workflow should verify the stored bytes first.

```
package tech.kayys.syirkah.accounting.application.audit;

public interface SyirkahEvidenceArchiveIntegrityVerifier {

    SyirkahEvidenceArchiveVerificationResult verify(
            String tenantId,
            String ledgerId,
            String exportId
    );
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.List;

public record SyirkahEvidenceArchiveVerificationResult(
        String tenantId,
        String ledgerId,
        String exportId,
        SyirkahArchiveIntegrityStatus status,
        String expectedHash,
        String actualHash,
        Instant checkedAt,
        List<String> findings
) {
    public SyirkahEvidenceArchiveVerificationResult {
        findings = List.copyOf(findings);
    }
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahArchiveIntegrityStatus {
    VERIFIED,
    OBJECT_MISSING,
    HASH_MISMATCH,
    CONTENT_UNREADABLE,
    UNSUPPORTED_FORMAT,
    STORAGE_UNAVAILABLE
}
```

The archive transition should follow this sequence:

1. Claim the archive job using a unique job key.
2. Load the immutable source package and verify its current hash.
3. Upload the exact canonical bytes using an idempotent storage key.
4. Retrieve the archived object or use an equivalent storage-integrity mechanism.
5. Recompute and verify its content hash.
6. Persist the archive receipt and an audit event in one database transaction.
7. Mark the export `ARCHIVED` only after verification succeeds.

If the worker crashes after upload but before the database commit, a retry should inspect the existing object and verify it rather than create a second logical archive. If the object exists but has different content, stop and emit an integrity finding; do not overwrite it automatically.

### 7. Implement a scheduled integrity sweep

A sweep should select due archive records in bounded batches. It must isolate failures so that one unavailable object store does not prevent checks for all other exports.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;

public record SyirkahArchiveIntegritySweepCommand(
        String tenantId,
        String ledgerId,
        Instant asOf,
        int batchSize,
        String sweepId
) {
    public SyirkahArchiveIntegritySweepCommand {
        if (batchSize < 1) {
            throw new IllegalArgumentException(
                    "Batch size must be positive."
            );
        }
    }
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;

public record SyirkahArchiveIntegritySweepResult(
        String sweepId,
        int checkedCount,
        int verifiedCount,
        int missingCount,
        int mismatchCount,
        int unavailableCount,
        List<SyirkahEvidenceRetentionFinding> findings
) {
    public SyirkahArchiveIntegritySweepResult {
        findings = List.copyOf(findings);
    }
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahEvidenceRetentionFinding(
        String exportId,
        String code,
        String detail,
        boolean requiresIntervention
) {
}
```

A sweep coordinator can use the following shape:

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.ArrayList;
import java.util.List;

public final class SyirkahArchiveIntegritySweepService {

    private final SyirkahArchiveIntegrityDueReader dueReader;
    private final SyirkahEvidenceArchiveIntegrityVerifier verifier;
    private final SyirkahArchiveIntegrityResultRecorder recorder;

    public SyirkahArchiveIntegritySweepService(
            SyirkahArchiveIntegrityDueReader dueReader,
            SyirkahEvidenceArchiveIntegrityVerifier verifier,
            SyirkahArchiveIntegrityResultRecorder recorder
    ) {
        this.dueReader = dueReader;
        this.verifier = verifier;
        this.recorder = recorder;
    }

    public SyirkahArchiveIntegritySweepResult execute(
            SyirkahArchiveIntegritySweepCommand command
    ) {
        List<String> exportIds = dueReader.findDueExportIds(
                command.tenantId(),
                command.ledgerId(),
                command.asOf(),
                command.batchSize()
        );

        int verified = 0;
        int missing = 0;
        int mismatch = 0;
        int unavailable = 0;

        List<SyirkahEvidenceRetentionFinding> findings =
                new ArrayList<>();

        for (String exportId : exportIds) {
            SyirkahEvidenceArchiveVerificationResult result;

            try {
                result = verifier.verify(
                        command.tenantId(),
                        command.ledgerId(),
                        exportId
                );
            } catch (RuntimeException exception) {
                result = new SyirkahEvidenceArchiveVerificationResult(
                        command.tenantId(),
                        command.ledgerId(),
                        exportId,
                        SyirkahArchiveIntegrityStatus.STORAGE_UNAVAILABLE,
                        null,
                        null,
                        command.asOf(),
                        List.of("VERIFICATION_ATTEMPT_FAILED")
                );
            }

            recorder.recordIdempotently(
                    command.sweepId(),
                    result
            );

            switch (result.status()) {
                case VERIFIED -> verified++;
                case OBJECT_MISSING -> {
                    missing++;
                    findings.add(new SyirkahEvidenceRetentionFinding(
                            exportId,
                            "ARCHIVE_OBJECT_MISSING",
                            "The archive object could not be found.",
                            true
                    ));
                }
                case HASH_MISMATCH -> {
                    mismatch++;
                    findings.add(new SyirkahEvidenceRetentionFinding(
                            exportId,
                            "ARCHIVE_HASH_MISMATCH",
                            "The archived content differs from the recorded hash.",
                            true
                    ));
                }
                case STORAGE_UNAVAILABLE,
                     CONTENT_UNREADABLE,
                     UNSUPPORTED_FORMAT -> {
                    unavailable++;
                    findings.add(new SyirkahEvidenceRetentionFinding(
                            exportId,
                            result.status().name(),
                            "Archive verification could not be completed.",
                            true
                    ));
                }
            }
        }

        return new SyirkahArchiveIntegritySweepResult(
                command.sweepId(),
                exportIds.size(),
                verified,
                missing,
                mismatch,
                unavailable,
                findings
        );
    }
}
```

The supporting ports:

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.List;

public interface SyirkahArchiveIntegrityDueReader {

    List<String> findDueExportIds(
            String tenantId,
            String ledgerId,
            Instant asOf,
            int batchSize
    );
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public interface SyirkahArchiveIntegrityResultRecorder {

    void recordIdempotently(
            String sweepId,
            SyirkahEvidenceArchiveVerificationResult result
    );
}
```

This sample deliberately records an operational failure as `STORAGE_UNAVAILABLE` rather than allowing the scheduled job to disappear without evidence. In production, also record a sanitized failure category and attempt metadata. Do not persist raw exception messages if they might expose storage credentials or internal paths.

### 8. Acceptance tests

| Scenario                                   | Expected behavior                                                     |
| ------------------------------------------ | --------------------------------------------------------------------- |
| Active legal hold                          | Expiry blocked                                                        |
| Retention policy unavailable               | No destructive action; explicit limitation                            |
| Archive object matches source hash         | Archive transition may complete                                       |
| Upload succeeds but verification fails     | Export remains unarchived; finding recorded                           |
| Worker crashes after upload                | Retry verifies the existing object                                    |
| Archived object is missing                 | Integrity finding and intervention required                           |
| Archived object hash differs               | Integrity failure; no automatic overwrite                             |
| Storage service is temporarily unavailable | Retryable failure recorded                                            |
| Duplicate sweep execution                  | No duplicate logical result for the same sweep and export             |
| One object fails in a batch                | Other exports continue to be checked                                  |
| Retention period has elapsed               | Eligibility evaluated; deletion requires a separate guarded operation |

Integration note: all classes here are implementation proposals and have not been compiled against the repository. Use the project’s existing scheduler, transaction manager, outbox, storage adapters, and export lifecycle wherever available.

Next slice: add the guarded expiry/deletion workflow, including authorization, legal-hold rechecks, two-phase deletion, a recoverable tombstone, and a permanent audit record of what was deleted and why.


//////////////


## Next slice: guarded expiry and evidence deletion

This slice completes the retention lifecycle by separating eligibility to expire from authorization to delete. A retention evaluator can recommend expiry, but a dedicated workflow must independently authorize and execute it.

The workflow should use a two-phase process:

1. Prepare: recheck retention policy, legal holds, archive availability, authorization, and export identity; then create a durable deletion intent.
2. Commit deletion: recheck critical conditions, delete only the specifically authorized objects, verify the result, and record a permanent tombstone and audit event.

A failed or interrupted deletion must remain recoverable. The system must not mark evidence as deleted merely because a deletion request was sent.

### 1. Model the deletion intent

The intent captures exactly what is authorized for deletion, why it is eligible, and which policy and approvals apply. Once prepared, its identity and scope must not be silently changed.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SyirkahEvidenceDeletionIntent(
        String tenantId,
        String ledgerId,
        String deletionIntentId,
        String exportId,
        String manifestHash,
        String bundleContentHash,
        String retentionPolicyId,
        String retentionPolicyVersion,
        String retentionEvaluationId,
        String authorizationReference,
        List<String> targetObjectReferences,
        String requestedBy,
        String approvedBy,
        String reasonCode,
        Instant preparedAt,
        Instant executeNotBefore,
        SyirkahEvidenceDeletionStatus status
) {
    public SyirkahEvidenceDeletionIntent {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(ledgerId);
        Objects.requireNonNull(deletionIntentId);
        Objects.requireNonNull(exportId);
        Objects.requireNonNull(manifestHash);
        Objects.requireNonNull(bundleContentHash);
        Objects.requireNonNull(retentionPolicyId);
        Objects.requireNonNull(retentionPolicyVersion);
        Objects.requireNonNull(retentionEvaluationId);
        Objects.requireNonNull(authorizationReference);
        Objects.requireNonNull(requestedBy);
        Objects.requireNonNull(reasonCode);
        Objects.requireNonNull(preparedAt);
        Objects.requireNonNull(executeNotBefore);
        Objects.requireNonNull(status);

        targetObjectReferences = List.copyOf(targetObjectReferences);

        if (targetObjectReferences.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one deletion target is required."
            );
        }

        if (executeNotBefore.isBefore(preparedAt)) {
            throw new IllegalArgumentException(
                    "Deletion cannot be scheduled before preparation."
            );
        }
    }
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahEvidenceDeletionStatus {
    PREPARED,
    BLOCKED,
    EXECUTING,
    PARTIALLY_DELETED,
    DELETED,
    CANCELLED,
    FAILED
}
```

`targetObjectReferences` must identify the actual objects covered by the intent, including their storage location and version where supported. Do not use a broad prefix deletion against an entire tenant, ledger, or archive bucket.

### 2. Define an authorization port

Authorization should be independent of the retention evaluator. The policy says whether an export may be eligible for deletion; authorization establishes whether this actor and request may perform that operation.

```
package tech.kayys.syirkah.accounting.application.audit;

public interface SyirkahEvidenceDeletionAuthorizer {

    SyirkahEvidenceDeletionAuthorization authorize(
            SyirkahEvidenceDeletionCommand command,
            SyirkahEvidenceExportRevision revision,
            SyirkahEvidenceRetentionEvaluation evaluation
    );
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;

public record SyirkahEvidenceDeletionAuthorization(
        boolean permitted,
        String authorizationReference,
        String approvedBy,
        List<String> reasonCodes
) {
    public SyirkahEvidenceDeletionAuthorization {
        reasonCodes = List.copyOf(reasonCodes);
    }
}
```

The authorizer should enforce separation of duties if required by the applicable policy—for example, preventing the requester from approving their own deletion. That rule belongs in the authoritative authorization policy, not merely in a UI.

### 3. Recheck retention and preservation constraints

Before creating an intent, require all of the following:

- The export exists within the requested tenant and ledger.
- The retention evaluation is valid and refers to the same export and policy version.
- The applicable policy explicitly permits deletion.
- The minimum retention period has elapsed.
- No active legal hold or preservation instruction applies.
- Required archive verification has succeeded if policy requires an archived copy before deletion.
- The deletion targets match the approved export and its recorded content hashes.
- Authorization is granted and has not expired.

Use a dedicated guard:

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;

public interface SyirkahEvidenceDeletionGuard {

    List<SyirkahEvidenceDeletionBlocker> evaluate(
            SyirkahEvidenceDeletionCommand command,
            SyirkahEvidenceExportRevision revision,
            SyirkahEvidenceRetentionEvaluation evaluation,
            SyirkahEvidenceRetentionPolicy policy
    );
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahEvidenceDeletionBlocker(
        String code,
        String detail
) {
}
```

If the guard cannot establish whether a legal hold exists, treat the result as blocked or indeterminate—not as confirmation that no hold exists.

### 4. Define the deletion command and repository

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahEvidenceDeletionCommand(
        String tenantId,
        String ledgerId,
        String exportId,
        String retentionEvaluationId,
        String requestedBy,
        String reasonCode,
        String idempotencyKey
) {
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.Optional;

public interface SyirkahEvidenceDeletionRepository {

    Optional<SyirkahEvidenceDeletionIntent> findById(
            String tenantId,
            String ledgerId,
            String deletionIntentId
    );

    Optional<SyirkahEvidenceDeletionIntent> findByIdempotencyKey(
            String tenantId,
            String ledgerId,
            String idempotencyKey
    );

    void insertWithinTransaction(
            String idempotencyKey,
            SyirkahEvidenceDeletionIntent intent
    );

    boolean transitionWithinTransaction(
            String tenantId,
            String ledgerId,
            String deletionIntentId,
            SyirkahEvidenceDeletionStatus expectedStatus,
            SyirkahEvidenceDeletionStatus newStatus
    );
}
```

Enforce a unique key on `(tenant_id, ledger_id, idempotency_key)`. If the same key is submitted with a different request, reject it. If the request is identical, return the existing intent and its status.

### 5. Implement deletion preparation

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SyirkahEvidenceDeletionPreparationService {

    private final SyirkahEvidenceExportRevisionRepository revisionRepository;
    private final SyirkahEvidenceRetentionEvaluationRepository evaluationRepository;
    private final SyirkahEvidenceRetentionPolicyResolver policyResolver;
    private final SyirkahEvidenceDeletionAuthorizer authorizer;
    private final SyirkahEvidenceDeletionGuard guard;
    private final SyirkahEvidenceDeletionTargetResolver targetResolver;
    private final SyirkahEvidenceDeletionRepository deletionRepository;
    private final SyirkahEvidenceDeletionTransaction transaction;
    private final Clock clock;

    public SyirkahEvidenceDeletionPreparationService(
            SyirkahEvidenceExportRevisionRepository revisionRepository,
            SyirkahEvidenceRetentionEvaluationRepository evaluationRepository,
            SyirkahEvidenceRetentionPolicyResolver policyResolver,
            SyirkahEvidenceDeletionAuthorizer authorizer,
            SyirkahEvidenceDeletionGuard guard,
            SyirkahEvidenceDeletionTargetResolver targetResolver,
            SyirkahEvidenceDeletionRepository deletionRepository,
            SyirkahEvidenceDeletionTransaction transaction,
            Clock clock
    ) {
        this.revisionRepository = revisionRepository;
        this.evaluationRepository = evaluationRepository;
        this.policyResolver = policyResolver;
        this.authorizer = authorizer;
        this.guard = guard;
        this.targetResolver = targetResolver;
        this.deletionRepository = deletionRepository;
        this.transaction = transaction;
        this.clock = clock;
    }

    public SyirkahEvidenceDeletionIntent prepare(
            SyirkahEvidenceDeletionCommand command
    ) {
        return transaction.execute(() -> {
            var existing = deletionRepository.findByIdempotencyKey(
                    command.tenantId(),
                    command.ledgerId(),
                    command.idempotencyKey()
            );

            if (existing.isPresent()) {
                return existing.get();
            }

            var revision = revisionRepository.findById(
                    command.tenantId(),
                    command.ledgerId(),
                    command.exportId()
            ).orElseThrow(() -> new IllegalArgumentException(
                    "Evidence export was not found."
            ));

            var evaluation = evaluationRepository.findById(
                    command.tenantId(),
                    command.ledgerId(),
                    command.retentionEvaluationId()
            ).orElseThrow(() -> new IllegalArgumentException(
                    "Retention evaluation was not found."
            ));

            if (!revision.exportId().equals(evaluation.exportId())
                    || !revision.tenantId().equals(evaluation.tenantId())
                    || !revision.ledgerId().equals(evaluation.ledgerId())) {
                throw new IllegalStateException(
                        "Retention evaluation does not match the export."
                );
            }

            if (evaluation.decision()
                    != SyirkahEvidenceRetentionDecision.EXPIRE) {
                throw new IllegalStateException(
                        "The retention evaluation does not permit expiry."
                );
            }

            var policy = policyResolver.resolve(
                    command.tenantId(),
                    command.ledgerId(),
                    revision.rootType(),
                    revision.rootId(),
                    revision.exportId()
            ).orElseThrow(() -> new IllegalStateException(
                    "The applicable retention policy is unavailable."
            ));

            if (!policy.deletionAllowed()
                    || !policy.policyId().equals(evaluation.policyId())
                    || !policy.policyVersion().equals(
                            evaluation.policyVersion())) {
                throw new IllegalStateException(
                        "The retention policy does not authorize this deletion."
                );
            }

            var blockers = guard.evaluate(
                    command,
                    revision,
                    evaluation,
                    policy
            );

            if (!blockers.isEmpty()) {
                throw new SyirkahEvidenceDeletionBlockedException(blockers);
            }

            var authorization = authorizer.authorize(
                    command,
                    revision,
                    evaluation
            );

            if (!authorization.permitted()) {
                throw new SecurityException(
                        "Evidence deletion was not authorized."
                );
            }

            Instant preparedAt = clock.instant();

            List<String> targets = targetResolver.resolveExactTargets(
                    command.tenantId(),
                    command.ledgerId(),
                    revision.exportId(),
                    revision.manifestHash(),
                    revision.bundleContentHash()
            );

            if (targets.isEmpty()) {
                throw new IllegalStateException(
                        "No eligible deletion targets were resolved."
                );
            }

            var intent = new SyirkahEvidenceDeletionIntent(
                    command.tenantId(),
                    command.ledgerId(),
                    UUID.randomUUID().toString(),
                    revision.exportId(),
                    revision.manifestHash(),
                    revision.bundleContentHash(),
                    policy.policyId(),
                    policy.policyVersion(),
                    evaluationId(evaluation),
                    authorization.authorizationReference(),
                    targets,
                    command.requestedBy(),
                    authorization.approvedBy(),
                    command.reasonCode(),
                    preparedAt,
                    preparedAt.plusSeconds(60),
                    SyirkahEvidenceDeletionStatus.PREPARED
            );

            deletionRepository.insertWithinTransaction(
                    command.idempotencyKey(),
                    intent
            );

            return intent;
        });
    }

    private String evaluationId(
            SyirkahEvidenceRetentionEvaluation evaluation
    ) {
        return evaluation.policyId() + ":" + evaluation.exportId()
                + ":" + evaluation.evaluatedAt();
    }
}
```

Supporting ports and exception:

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;

public interface SyirkahEvidenceDeletionTargetResolver {

    List<String> resolveExactTargets(
            String tenantId,
            String ledgerId,
            String exportId,
            String manifestHash,
            String bundleContentHash
    );
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.function.Supplier;

public interface SyirkahEvidenceDeletionTransaction {

    <T> T execute(Supplier<T> work);
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;

public final class SyirkahEvidenceDeletionBlockedException
        extends RuntimeException {

    private final List<SyirkahEvidenceDeletionBlocker> blockers;

    public SyirkahEvidenceDeletionBlockedException(
            List<SyirkahEvidenceDeletionBlocker> blockers
    ) {
        super("Evidence deletion is blocked.");
        this.blockers = List.copyOf(blockers);
    }

    public List<SyirkahEvidenceDeletionBlocker> blockers() {
        return blockers;
    }
}
```

Adaptation notes:

- `evaluationId()` above is only a placeholder because the earlier evaluation record did not include its own ID. Add a stable `evaluationId` field to that record and use it directly.
- The transaction must atomically claim the idempotency key and insert the intent. The example’s separate lookup is not sufficient for concurrency safety by itself.
- Store a request hash with the idempotency record to detect same-key/different-payload reuse.
- `executeNotBefore` is an example cooling-off delay. Make it policy-driven rather than hardcoded.
- The preparation workflow should lock or revision-check the export, retention state, and relevant preservation controls before committing the intent.

### 6. Execute deletion using a recoverable tombstone

A tombstone records that deletion occurred, what was targeted, and the result. It should retain only the metadata necessary for audit and accountability, subject to applicable retention rules.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.List;

public record SyirkahEvidenceDeletionTombstone(
        String tenantId,
        String ledgerId,
        String deletionIntentId,
        String exportId,
        String manifestHash,
        String bundleContentHash,
        List<SyirkahEvidenceDeletedObject> deletedObjects,
        String retentionPolicyId,
        String retentionPolicyVersion,
        String authorizationReference,
        String reasonCode,
        Instant deletionCompletedAt,
        String deletionResultHash
) {
    public SyirkahEvidenceDeletionTombstone {
        deletedObjects = List.copyOf(deletedObjects);
    }
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahEvidenceDeletedObject(
        String objectReference,
        String storageVersion,
        String deletionReceipt,
        String result
) {
}
```

Define an idempotent deletion adapter:

```
package tech.kayys.syirkah.accounting.application.audit;

public interface SyirkahEvidenceObjectDeleter {

    SyirkahEvidenceObjectDeletionResult deleteExactVersion(
            String tenantId,
            String ledgerId,
            String objectReference,
            String expectedContentHash,
            String idempotencyKey
    );
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahEvidenceObjectDeletionResult(
        String objectReference,
        String storageVersion,
        String deletionReceipt,
        SyirkahEvidenceObjectDeletionStatus status
) {
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahEvidenceObjectDeletionStatus {
    DELETED,
    ALREADY_ABSENT,
    VERSION_MISMATCH,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE
}
```

The execution worker should follow this pattern:

1. Claim a `PREPARED` intent with compare-and-set, changing it to `EXECUTING`.
2. Revalidate authorization, retention eligibility, legal holds, and the exact target versions.
3. Delete each exact target using a stable per-target idempotency key.
4. Persist each target’s result so partial execution can resume safely.
5. If every required target is confirmed deleted or was already absent under an explicitly approved rule, write the tombstone and audit event.
6. Mark the intent `DELETED` only after the tombstone and required audit records are durable.
7. If some targets fail, record `PARTIALLY_DELETED` or `FAILED` and resume or escalate according to policy.

Do not treat a version mismatch as permission to delete the newer object. Stop and raise an integrity finding.

### 7. Keep the audit trail after deletion

Add an append-only deletion audit event with:

- Tenant and ledger scope.
- Export and deletion-intent IDs.
- Original manifest and bundle hashes.
- Retention policy ID and version.
- Retention evaluation ID.
- Authorization reference and approving actor.
- Target-object results.
- Deletion completion time and outcome.
- Tombstone ID and hash.

Do not retain the full deleted evidence payload inside the tombstone; that would defeat the deletion. Conversely, do not delete the tombstone as part of the same operation. Its own retention and legal-hold requirements must be evaluated independently.

### 8. Acceptance tests

| Scenario                                          | Expected behavior                                                |
| ------------------------------------------------- | ---------------------------------------------------------------- |
| Legal hold appears after preparation              | Execution is blocked before deletion                             |
| Authorization is revoked before execution         | Execution is blocked                                             |
| Retention policy version changes                  | Revalidate under the authoritative applicable policy             |
| Target version differs from the intent            | Do not delete the unexpected version                             |
| Worker crashes after deleting one object          | Resume using persisted per-target results                        |
| Same command is retried                           | Return the existing intent or result                             |
| Same idempotency key has a different request hash | Reject key reuse                                                 |
| Some targets delete and others fail               | Preserve partial status and recoverable progress                 |
| All targets are confirmed deleted                 | Tombstone and audit event are persisted                          |
| Tombstone persistence fails                       | Do not report the workflow as successfully completed             |
| Export is already absent                          | Follow explicit policy; never infer that deletion was authorized |
| Another tenant’s object is referenced             | Reject before storage operation                                  |

Important: deletion semantics depend on the storage provider. Some systems support immutable object versions and reliable deletion receipts; others provide weaker guarantees. Implement the adapter against the actual provider’s behavior and document any limits.

Next slice: connect this workflow to the existing outbox and operational monitoring, with durable job leasing, retry classification, dead-letter handling, and alerts for legal-hold conflicts or archive-integrity failures.

///////////////

The next step is to finish the operational reliability layer, then do a repository-aligned integration and verification pass. After that, the Syirkah close-finalization and evidence-lifecycle work can reach a defined completion point—without rebuilding the accounting platform.

## Remaining roadmap to ultimate finalization

1\. Operational reliability — next

Add outbox integration, durable job leasing, retry classification, dead-letter handling, idempotent workers, and operational alerts.

Exit condition: failures and retries are recoverable, observable, and cannot silently corrupt evidence.

2\. Security and tenant isolation

Verify authorization, separation of duties, tenant/ledger scoping, secret handling, immutable audit records, and legal-hold enforcement.

Exit condition: cross-tenant access and unauthorized evidence deletion are prevented and tested.

3\. Integrate with the actual repository

Map the proposed types to existing entities, repositories, transaction boundaries, migrations, close-cycle APIs, and outbox conventions. Remove duplicate models and adapt interfaces to the real codebase.

Exit condition: no parallel implementation of functionality the platform already provides.

4\. Integration and failure testing

Test close finalization, expected-calculation reconciliation, audit creation, outbox delivery, export verification, archival, retention, and deletion under concurrency, retries, and partial failures.

Exit condition: the agreed test suite passes in the real build; no tests have yet been run for the proposed code.

5\. Sharia and accounting acceptance

Validate the applicable Syirkah contract rules, profit/loss allocation, partner transitions, distributions, correction lineage, and period-close evidence with the responsible domain and Sharia reviewers.

Exit condition: business rules are explicitly approved; a hash or successful software test is not a Sharia ruling.

6\. Release and finalization

Apply database migrations, verify observability and recovery procedures, document operational ownership, and complete a release checklist.

Exit condition: code, data integrity, operational controls, and domain sign-off are all verified.

## What “ultimate finalization” means

There are two distinct finish lines.

A. Close-finalization transaction: a particular Syirkah accounting close is finalized atomically only after its validation gate passes, its evidence is pinned and rechecked, the relevant snapshot and correction lineage are valid, and audit/outbox/idempotency records commit consistently.

B. Project finalization: the wider implementation is complete only after the integration, security, tests, and Sharia/accounting acceptance above are actually verified.

The evidence-retention and deletion workflow is supporting infrastructure, not a prerequisite to calculate and finalize every ordinary close. It can be completed and released in its own controlled stage.

## My recommended next coding slice

Proceed with operational reliability for retention and deletion jobs, because the previous slice ended at that point:

1. Durable job lease and concurrency-safe claim.
2. Retryable versus permanent failure classification.
3. Outbox events for archive, integrity, and deletion outcomes.
4. Dead-letter and manual recovery workflow.
5. Alerts for legal-hold conflicts, missing archive objects, hash mismatches, and stalled jobs.

After that, shift from adding more conceptual classes to integrating and testing the implementation against the real Maven modules. The code shared so far is proposed code, not a verified patch; the repository has not yet been compiled in this conversation.

////////////

## Next slice: durable jobs, retries, and recovery

This slice connects the archive-integrity and deletion workflows to reliable background processing. The goal is to survive worker crashes, duplicate delivery, temporary storage outages, and partial deletion without losing auditability or repeating unsafe actions.

The design uses a durable job record with a lease, a bounded retry policy, explicit terminal states, and outbox events for significant outcomes. It does not replace the existing application’s scheduler or outbox; the adapters should use those facilities if already present.

### 1. Define the durable job model

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.Objects;

public record SyirkahEvidenceJob(
        String tenantId,
        String ledgerId,
        String jobId,
        String jobType,
        String aggregateId,
        String idempotencyKey,
        String requestHash,
        SyirkahEvidenceJobStatus status,
        int attemptCount,
        int maxAttempts,
        Instant nextAttemptAt,
        String leaseOwner,
        String leaseToken,
        Instant leaseExpiresAt,
        String lastFailureCode,
        Instant createdAt,
        Instant updatedAt
) {
    public SyirkahEvidenceJob {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(ledgerId);
        Objects.requireNonNull(jobId);
        Objects.requireNonNull(jobType);
        Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(idempotencyKey);
        Objects.requireNonNull(requestHash);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);

        if (attemptCount < 0 || maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "Invalid job attempt limits."
            );
        }
    }
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahEvidenceJobStatus {
    PENDING,
    RUNNING,
    RETRY_WAIT,
    SUCCEEDED,
    DEAD_LETTERED,
    CANCELLED
}
```

The job record is operational metadata, not the authoritative deletion intent or archive receipt. Those domain records remain the source of truth for whether a specific operation was completed.

### 2. Use lease tokens to prevent stale workers from committing

A worker may pause past its lease expiry and then resume after another worker has reclaimed the job. Every state-changing operation must therefore verify the current lease token, not merely the worker name.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;

public record SyirkahEvidenceJobLease(
        String tenantId,
        String ledgerId,
        String jobId,
        String workerId,
        String leaseToken,
        Instant expiresAt,
        int attemptNumber
) {
}
```

Define the repository port:

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;
import java.util.Optional;

public interface SyirkahEvidenceJobRepository {

    Optional<SyirkahEvidenceJobLease> claimNext(
            String workerId,
            Instant now,
            Instant leaseExpiresAt
    );

    boolean renewLease(
            SyirkahEvidenceJobLease lease,
            Instant newExpiresAt
    );

    boolean markSucceeded(
            SyirkahEvidenceJobLease lease,
            Instant completedAt
    );

    boolean scheduleRetry(
            SyirkahEvidenceJobLease lease,
            Instant nextAttemptAt,
            String failureCode,
            Instant updatedAt
    );

    boolean deadLetter(
            SyirkahEvidenceJobLease lease,
            String failureCode,
            Instant updatedAt
    );
}
```

The persistence implementation should claim a job atomically—for example, using row locking or a conditional update. Lease renewal, retry scheduling, success, and dead-letter transitions must all include the lease token in their compare-and-set condition.

A worker that has lost its lease must not commit a result simply because it finished the underlying work.

### 3. Classify failures explicitly

Do not retry every exception. A permanent authorization denial should not be retried like a temporary storage outage.

```
package tech.kayys.syirkah.accounting.application.audit;

public enum SyirkahEvidenceFailureKind {
    RETRYABLE,
    PERMANENT,
    SECURITY_BLOCK,
    INTEGRITY_FAILURE
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public record SyirkahEvidenceJobFailure(
        String failureCode,
        SyirkahEvidenceFailureKind kind,
        String safeDescription
) {
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

public interface SyirkahEvidenceFailureClassifier {

    SyirkahEvidenceJobFailure classify(Exception exception);
}
```

Suggested classifications:

| Failure                                       | Classification      | Behavior                                                   |
| --------------------------------------------- | ------------------- | ---------------------------------------------------------- |
| Temporary object-store outage                 | `RETRYABLE`         | Retry with backoff                                         |
| Network timeout with unknown operation result | `RETRYABLE`         | Reconcile operation state before repeating                 |
| Unsupported artifact schema                   | `PERMANENT`         | Dead-letter for review or compatible verifier              |
| Authorization denied                          | `SECURITY_BLOCK`    | Stop automatic processing and alert                        |
| Archive hash mismatch                         | `INTEGRITY_FAILURE` | Quarantine and alert; never overwrite automatically        |
| Legal hold discovered                         | `SECURITY_BLOCK`    | Block deletion; require fresh authorization and evaluation |

A legal-hold conflict should also update the deletion workflow’s domain status and emit an audit event. It should not be represented solely as a job failure.

### 4. Calculate bounded retry delays

Use exponential backoff with a maximum delay and deterministic jitter derived from the job identity and attempt number. Deterministic jitter helps avoid synchronized retries while making scheduling reproducible.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Duration;

public interface SyirkahEvidenceRetryPolicy {

    Duration delayBeforeRetry(
            String jobId,
            int failedAttempt
    );

    boolean canRetry(
            int failedAttempt,
            int maxAttempts
    );
}
```

A policy implementation should enforce:

- A positive base delay.
- A maximum delay.
- A maximum attempt count.
- Overflow-safe duration calculations.
- A stable jitter calculation.
- No automatic retry for permanent, security, or integrity failures.

The retry policy determines when a job may run again; the domain workflow still determines whether the operation is safe to resume.

### 5. Define the job handler

```
package tech.kayys.syirkah.accounting.application.audit;

public interface SyirkahEvidenceJobHandler {

    String supportedJobType();

    void handle(SyirkahEvidenceJob job) throws Exception;
}
```

For example, separate handlers can be registered for:

- `ARCHIVE_EXPORT`
- `VERIFY_ARCHIVE_INTEGRITY`
- `EXECUTE_EVIDENCE_DELETION`

Each handler should invoke its existing domain service. Avoid placing storage-provider details or deletion authorization rules in the generic worker.

### 6. Implement the worker

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public final class SyirkahEvidenceJobWorker {

    private final SyirkahEvidenceJobRepository jobRepository;
    private final SyirkahEvidenceJobHandlerRegistry handlerRegistry;
    private final SyirkahEvidenceFailureClassifier failureClassifier;
    private final SyirkahEvidenceRetryPolicy retryPolicy;
    private final SyirkahEvidenceJobEventPublisher eventPublisher;
    private final Clock clock;
    private final Duration leaseDuration;
    private final String workerId;

    public SyirkahEvidenceJobWorker(
            SyirkahEvidenceJobRepository jobRepository,
            SyirkahEvidenceJobHandlerRegistry handlerRegistry,
            SyirkahEvidenceFailureClassifier failureClassifier,
            SyirkahEvidenceRetryPolicy retryPolicy,
            SyirkahEvidenceJobEventPublisher eventPublisher,
            Clock clock,
            Duration leaseDuration,
            String workerId
    ) {
        this.jobRepository = jobRepository;
        this.handlerRegistry = handlerRegistry;
        this.failureClassifier = failureClassifier;
        this.retryPolicy = retryPolicy;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.leaseDuration = leaseDuration;
        this.workerId = workerId;
    }

    public boolean runOne() {
        Instant now = clock.instant();

        var claimed = jobRepository.claimNext(
                workerId,
                now,
                now.plus(leaseDuration)
        );

        if (claimed.isEmpty()) {
            return false;
        }

        SyirkahEvidenceJobLease lease = claimed.get();

        try {
            SyirkahEvidenceJob job = handlerRegistry.loadJob(lease);

            SyirkahEvidenceJobHandler handler =
                    handlerRegistry.handlerFor(job.jobType());

            handler.handle(job);

            boolean completed = jobRepository.markSucceeded(
                    lease,
                    clock.instant()
            );

            if (completed) {
                eventPublisher.publishOutcome(
                        lease,
                        "SUCCEEDED",
                        Map.of()
                );
            }

            return true;
        } catch (Exception exception) {
            SyirkahEvidenceJobFailure failure =
                    failureClassifier.classify(exception);

            if (failure.kind() == SyirkahEvidenceFailureKind.RETRYABLE) {
                handleRetryableFailure(lease, failure);
            } else {
                handleTerminalFailure(lease, failure);
            }

            return true;
        }
    }

    private void handleRetryableFailure(
            SyirkahEvidenceJobLease lease,
            SyirkahEvidenceJobFailure failure
    ) {
        int attempt = lease.attemptNumber();

        SyirkahEvidenceJob job = handlerRegistry.loadJob(lease);

        if (retryPolicy.canRetry(attempt, job.maxAttempts())) {
            Duration delay = retryPolicy.delayBeforeRetry(
                    job.jobId(),
                    attempt
            );

            Instant now = clock.instant();

            boolean scheduled = jobRepository.scheduleRetry(
                    lease,
                    now.plus(delay),
                    failure.failureCode(),
                    now
            );

            if (scheduled) {
                eventPublisher.publishOutcome(
                        lease,
                        "RETRY_SCHEDULED",
                        Map.of("failureCode", failure.failureCode())
                );
            }
        } else {
            deadLetter(lease, failure.failureCode());
        }
    }

    private void handleTerminalFailure(
            SyirkahEvidenceJobLease lease,
            SyirkahEvidenceJobFailure failure
    ) {
        deadLetter(lease, failure.failureCode());

        eventPublisher.publishOutcome(
                lease,
                failure.kind().name(),
                Map.of("failureCode", failure.failureCode())
        );
    }

    private void deadLetter(
            SyirkahEvidenceJobLease lease,
            String failureCode
    ) {
        boolean deadLettered = jobRepository.deadLetter(
                lease,
                failureCode,
                clock.instant()
        );

        if (deadLettered) {
            eventPublisher.publishOutcome(
                    lease,
                    "DEAD_LETTERED",
                    Map.of("failureCode", failureCode)
            );
        }
    }
}
```

The supporting registry and publisher contracts:

```
package tech.kayys.syirkah.accounting.application.audit;

public interface SyirkahEvidenceJobHandlerRegistry {

    SyirkahEvidenceJob loadJob(
            SyirkahEvidenceJobLease lease
    );

    SyirkahEvidenceJobHandler handlerFor(
            String jobType
    );
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.Map;

public interface SyirkahEvidenceJobEventPublisher {

    void publishOutcome(
            SyirkahEvidenceJobLease lease,
            String outcome,
            Map<String, String> safeAttributes
    );
}
```

Production correction: the sample worker illustrates the flow but does not yet provide a transactionally complete outbox implementation. `publishOutcome` must write to the existing transactional outbox, not directly publish a message before the job-state transaction commits. Ideally, the job transition and outbox insertion occur in one transaction, and stale lease tokens prevent both writes.

Also, do not catch a failure and schedule a retry if the handler may have partially completed an operation without an idempotent recovery mechanism. Archive and deletion handlers must reconcile their domain state before retrying.

### 7. Add operational monitoring

Track at least:

- Number of pending and retry-wait jobs.
- Oldest pending job age.
- Active and expired leases.
- Retry count by failure code and job type.
- Dead-letter count and age.
- Archive integrity failures.
- Deletion jobs blocked by legal holds.
- Jobs with repeated storage failures.
- Jobs whose domain outcome is unknown.

Alerts should be actionable and avoid exposing partner details, secrets, or raw storage credentials.

### 8. Acceptance tests

| Scenario                                        | Expected behavior                                               |
| ----------------------------------------------- | --------------------------------------------------------------- |
| Two workers claim concurrently                  | Only one valid lease owns the job                               |
| Worker crashes before execution                 | Lease expires and job can be reclaimed                          |
| Old worker resumes after lease loss             | Stale lease cannot update job state                             |
| Retryable failure occurs                        | Job enters `RETRY_WAIT` with bounded delay                      |
| Permanent failure occurs                        | Job is dead-lettered                                            |
| Archive operation completed before worker crash | Retry reconciles existing archive; no duplicate logical archive |
| Deletion partially completes                    | Worker resumes from persisted per-target results                |
| Outbox insert fails                             | Job transition and event insertion roll back together           |
| Same event is delivered twice                   | Consumer handles it idempotently                                |
| Hash mismatch occurs                            | No automatic overwrite or deletion                              |
| Tenant scope is inconsistent                    | Job is rejected and security event recorded                     |

## What follows this slice?

The operational job layer is the final major piece of the retention workflow. The next step should be an integration and verification pass, not another large set of speculative domain classes:

1. Inspect the actual Maven modules and existing outbox, scheduler, transaction, and repository APIs.
2. Map these proposals onto existing types and remove duplicate abstractions.
3. Implement database migrations and adapters.
4. Run compilation and focused unit/integration tests.
5. Resolve failures and document any unverified external infrastructure assumptions.
6. Complete the Sharia/accounting acceptance checklist and release review.

That is the path from the current design proposals to a genuinely finalized implementation. The proposed code has not yet been compiled or tested against the repository.


//////////////

## Next slice: integrate the reliability layer with the existing Syirkah platform

The next step is to make background processing transactionally consistent and recoverable. In particular, a job must not be marked successful while its outcome event is lost, and a deletion must not be repeated blindly after a worker crash.

The integration should reuse the repository’s existing transaction manager, outbox, job scheduler, and persistence conventions wherever available. Since I haven’t inspected or compiled the actual Maven repository, the following is a proposed integration contract—not a claim that these types already exist.

### 1. Make job creation and outbox insertion atomic

When a business transaction requests an archive, integrity check, or deletion, it should persist the job and its event in the same database transaction.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;

public record SyirkahEvidenceJobRequestedEvent(
        String eventId,
        int schemaVersion,
        String tenantId,
        String ledgerId,
        String jobId,
        String jobType,
        String aggregateId,
        Instant occurredAt
) {
}
```

Define an application port that guarantees both writes use the same transaction:

```
package tech.kayys.syirkah.accounting.application.audit;

public interface SyirkahEvidenceJobScheduler {

    SyirkahEvidenceJob scheduleWithinTransaction(
            SyirkahEvidenceJob job,
            SyirkahEvidenceJobRequestedEvent event
    );
}
```

The persistence adapter must implement the method as one transaction:

1. Validate tenant and ledger scope.
2. Insert the job using its idempotency constraints.
3. Insert the outbox event.
4. Commit both together.

If either insert fails, both must roll back. If the idempotency key already exists, compare the request hash: return the existing job for the same request, and reject reuse of that key for a different request.

### 2. Specify the database constraints

Adapt these constraints to the actual schema and database dialect.

```
CREATE TABLE syirkah_evidence_job (
    tenant_id          VARCHAR(100) NOT NULL,
    ledger_id          VARCHAR(100) NOT NULL,
    job_id             VARCHAR(100) NOT NULL,
    job_type           VARCHAR(100) NOT NULL,
    aggregate_id       VARCHAR(200) NOT NULL,
    idempotency_key    VARCHAR(200) NOT NULL,
    request_hash       VARCHAR(128) NOT NULL,
    status             VARCHAR(40) NOT NULL,
    attempt_count      INTEGER NOT NULL DEFAULT 0,
    max_attempts       INTEGER NOT NULL,
    next_attempt_at    TIMESTAMP NOT NULL,
    lease_owner        VARCHAR(200),
    lease_token        VARCHAR(100),
    lease_expires_at   TIMESTAMP,
    last_failure_code  VARCHAR(100),
    created_at         TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP NOT NULL,

    PRIMARY KEY (tenant_id, ledger_id, job_id),
    UNIQUE (tenant_id, ledger_id, idempotency_key),

    CHECK (attempt_count >= 0),
    CHECK (max_attempts > 0)
);
```

Add indexes for pending-job selection and lease recovery. The outbox should use the platform’s existing table and event schema rather than introducing a competing outbox implementation.

### 3. Claim jobs atomically

The worker must never use a separate “read pending job, then update it” sequence without concurrency control. One database-supported approach is a row-locking query:

```
SELECT tenant_id, ledger_id, job_id
FROM syirkah_evidence_job
WHERE status IN ('PENDING', 'RETRY_WAIT')
  AND next_attempt_at <= :now
ORDER BY next_attempt_at, created_at
FOR UPDATE SKIP LOCKED
FETCH FIRST 1 ROW ONLY;
```

Within the same transaction, update the selected row to `RUNNING`, increment its attempt count, assign a fresh lease token, and set its expiry. Use the syntax supported by your database.

Expired `RUNNING` jobs need a separate recovery path. Recovery should invalidate the previous lease before a new worker can claim the job.

### 4. Make worker outcomes transactional

The repository should offer operations that combine job-state changes with outbox insertion:

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;

public interface SyirkahEvidenceJobCompletionService {

    boolean completeWithinTransaction(
            SyirkahEvidenceJobLease lease,
            Instant completedAt
    );

    boolean scheduleRetryWithinTransaction(
            SyirkahEvidenceJobLease lease,
            Instant nextAttemptAt,
            String failureCode,
            Instant updatedAt
    );

    boolean deadLetterWithinTransaction(
            SyirkahEvidenceJobLease lease,
            String failureCode,
            Instant updatedAt
    );
}
```

Each method must:

- Verify the tenant, ledger, job ID, current status, and lease token.
- Perform a conditional state transition.
- Insert the corresponding outbox event in the same transaction.
- Roll back both operations if either fails.

A `false` result means the lease is stale or the state has changed. The worker must not overwrite the newer state.

### 5. Treat deletion as a recoverable saga

Object storage usually cannot participate in the database transaction. Therefore, deletion cannot generally be made atomic with a relational database commit.

Use a durable per-target state machine instead:

\#chatgpt-mermaid-\_r_31du\_{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;fill:rgb(237, 237, 237);}@keyframes edge-animation-frame{from{stroke-dashoffset:0;}}@keyframes dash{to{stroke-dashoffset:0;}}#chatgpt-mermaid-\_r_31du\_ .edge-animation-slow{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 50s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_31du\_ .edge-animation-fast{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 20s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_31du\_ .error-icon{fill:rgb(48, 48, 48);}#chatgpt-mermaid-\_r_31du\_ .error-text{fill:rgb(237, 237, 237);stroke:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31du\_ .edge-thickness-normal{stroke-width:1px;}#chatgpt-mermaid-\_r_31du\_ .edge-thickness-thick{stroke-width:3.5px;}#chatgpt-mermaid-\_r_31du\_ .edge-pattern-solid{stroke-dasharray:0;}#chatgpt-mermaid-\_r_31du\_ .edge-thickness-invisible{stroke-width:0;fill:none;}#chatgpt-mermaid-\_r_31du\_ .edge-pattern-dashed{stroke-dasharray:3;}#chatgpt-mermaid-\_r_31du\_ .edge-pattern-dotted{stroke-dasharray:2;}#chatgpt-mermaid-\_r_31du\_ .marker{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_31du\_ .marker.cross{stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_31du\_ svg{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;}#chatgpt-mermaid-\_r_31du\_ p{margin:0;}#chatgpt-mermaid-\_r_31du\_ .label{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31du\_ .cluster-label text{fill:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31du\_ .cluster-label span{color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31du\_ .cluster-label span p{background-color:transparent;}#chatgpt-mermaid-\_r_31du\_ .label text,#chatgpt-mermaid-\_r_31du\_ span{fill:rgb(237, 237, 237);color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31du\_ .node rect,#chatgpt-mermaid-\_r_31du\_ .node circle,#chatgpt-mermaid-\_r_31du\_ .node ellipse,#chatgpt-mermaid-\_r_31du\_ .node polygon,#chatgpt-mermaid-\_r_31du\_ .node path{fill:rgb(9, 23, 44);stroke:rgb(31, 78, 148);stroke-width:1px;}#chatgpt-mermaid-\_r_31du\_ .rough-node .label text,#chatgpt-mermaid-\_r_31du\_ .node .label text,#chatgpt-mermaid-\_r_31du\_ .image-shape .label,#chatgpt-mermaid-\_r_31du\_ .icon-shape .label{text-anchor:middle;}#chatgpt-mermaid-\_r_31du\_ .node .katex path{fill:#000;stroke:#000;stroke-width:1px;}#chatgpt-mermaid-\_r_31du\_ .rough-node .label,#chatgpt-mermaid-\_r_31du\_ .node .label,#chatgpt-mermaid-\_r_31du\_ .image-shape .label,#chatgpt-mermaid-\_r_31du\_ .icon-shape .label{text-align:center;}#chatgpt-mermaid-\_r_31du\_ .node.clickable{cursor:pointer;}#chatgpt-mermaid-\_r_31du\_ .root .anchor path{fill:rgb(175, 175, 175)!important;stroke-width:0;stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_31du\_ .arrowheadPath{fill:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_31du\_ .edgePath .path{stroke:rgb(175, 175, 175);stroke-width:1px;}#chatgpt-mermaid-\_r_31du\_ .flowchart-link{stroke:rgb(175, 175, 175);fill:none;}#chatgpt-mermaid-\_r_31du\_ .edgeLabel{background-color:rgb(0, 0, 0);text-align:center;}#chatgpt-mermaid-\_r_31du\_ .edgeLabel p{background-color:rgb(0, 0, 0);}#chatgpt-mermaid-\_r_31du\_ .edgeLabel rect{opacity:0.5;background-color:rgb(0, 0, 0);fill:rgb(0, 0, 0);}#chatgpt-mermaid-\_r_31du\_ .labelBkg{background-color:rgba(0, 0, 0, 0.5);}#chatgpt-mermaid-\_r_31du\_ .cluster rect{fill:rgb(48, 48, 48);stroke:rgba(255, 255, 255, 0.15);stroke-width:1px;}#chatgpt-mermaid-\_r_31du\_ .cluster text{fill:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31du\_ .cluster span{color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31du\_ div.mermaidTooltip{position:absolute;text-align:center;max-width:200px;padding:2px;font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:12px;background:rgb(48, 48, 48);border:1px solid rgba(255, 255, 255, 0.15);border-radius:2px;pointer-events:none;z-index:100;}#chatgpt-mermaid-\_r_31du\_ .flowchartTitleText{text-anchor:middle;font-size:18px;fill:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31du\_ rect.text{fill:none;stroke-width:0;}#chatgpt-mermaid-\_r_31du\_ .icon-shape,#chatgpt-mermaid-\_r_31du\_ .image-shape{background-color:rgb(0, 0, 0);text-align:center;}#chatgpt-mermaid-\_r_31du\_ .icon-shape p,#chatgpt-mermaid-\_r_31du\_ .image-shape p{background-color:rgb(0, 0, 0);padding:2px;}#chatgpt-mermaid-\_r_31du\_ .icon-shape .label rect,#chatgpt-mermaid-\_r_31du\_ .image-shape .label rect{opacity:0.5;background-color:rgb(0, 0, 0);fill:rgb(0, 0, 0);}#chatgpt-mermaid-\_r_31du\_ .label-icon{display:inline-block;height:1em;overflow:visible;vertical-align:-0.125em;}#chatgpt-mermaid-\_r_31du\_ .node .label-icon path{fill:currentColor;stroke:revert;stroke-width:revert;}#chatgpt-mermaid-\_r_31du\_ .node .neo-node{stroke:rgb(31, 78, 148);}#chatgpt-mermaid-\_r_31du\_ [data-look="neo"].node rect,#chatgpt-mermaid-\_r_31du\_ [data-look="neo"].cluster rect,#chatgpt-mermaid-\_r_31du\_ [data-look="neo"].node polygon{stroke:url(#chatgpt-mermaid-\_r_31du\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_31du\_ [data-look="neo"].swimlane.cluster rect{filter:none;}#chatgpt-mermaid-\_r_31du\_ [data-look="neo"].node path{stroke:url(#chatgpt-mermaid-\_r_31du\_-gradient);stroke-width:1px;}#chatgpt-mermaid-\_r_31du\_ [data-look="neo"].node .outer-path{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_31du\_ [data-look="neo"].node .neo-line path{stroke:rgb(31, 78, 148);filter:none;}#chatgpt-mermaid-\_r_31du\_ [data-look="neo"].node circle{stroke:url(#chatgpt-mermaid-\_r_31du\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_31du\_ [data-look="neo"].node circle .state-start{fill:#000000;}#chatgpt-mermaid-\_r_31du\_ [data-look="neo"].icon-shape .icon{fill:url(#chatgpt-mermaid-\_r_31du\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_31du\_ [data-look="neo"].icon-shape .icon-neo path{stroke:url(#chatgpt-mermaid-\_r_31du\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_31du\_ .node text{font-size:14px;font-weight:600;letter-spacing:normal;fill:rgb(153, 206, 255);}#chatgpt-mermaid-\_r_31du\_ .edgeLabels text{font-size:13px;font-weight:600;letter-spacing:-0.08px;fill:rgb(153, 206, 255);}#chatgpt-mermaid-\_r_31du\_ .node tspan[font-weight="normal"],#chatgpt-mermaid-\_r_31du\_ .edgeLabels tspan[font-weight="normal"]{font-weight:600;}#chatgpt-mermaid-\_r_31du\_ .edgeLabel .label rect{opacity:1;rx:13px;ry:13px;fill:rgb(0, 14, 26);stroke:rgb(26, 62, 95);stroke-width:1px;}#chatgpt-mermaid-\_r_31du\_ .node rect,#chatgpt-mermaid-\_r_31du\_ .node circle,#chatgpt-mermaid-\_r_31du\_ .node ellipse,#chatgpt-mermaid-\_r_31du\_ .node polygon,#chatgpt-mermaid-\_r_31du\_ .node path{fill:rgb(0, 40, 77);stroke:rgba(255, 255, 255, 0.1);stroke-width:1px;}#chatgpt-mermaid-\_r_31du\_ .node rect{rx:16px;ry:16px;}#chatgpt-mermaid-\_r_31du\_ .node.mermaid-decision .label-container{fill:rgb(0, 14, 26);stroke:rgb(26, 62, 95);stroke-dasharray:2,2;}#chatgpt-mermaid-\_r_31du\_ .edgePaths .flowchart-link{stroke:rgb(175, 175, 175);stroke-width:1px;stroke-linecap:round;stroke-linejoin:round;}#chatgpt-mermaid-\_r_31du\_ .marker{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_31du\_ :root{--mermaid-font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";}Prepared deletion intentRevalidate policy,authorization and legal holdsRecord blocked outcome andalertPersist execution claimDelete exact object versionDeletion resultPersist target receiptStop and flag integrityconflictPersist retry stateAll targets resolved?Persist tombstone and auditevidenceMark intent and job completeBlockedAllowedDeleted or already absentVersion mismatchRetryable failureNoYes

Important safeguards:

- Persist each target’s outcome before treating that target as finished.
- On restart, reconcile storage state with the recorded receipt before retrying.
- Never delete a replacement object when its storage version differs from the prepared target.
- Recheck legal holds and authorization immediately before each irreversible operation where practical.
- If tombstone or audit persistence fails, do not report the workflow as successfully finalized. Recovery must reconcile already deleted objects and complete the evidence trail.
- Retain deletion tombstones according to their own retention policy.

### 6. Add an outbox delivery contract

Outbox delivery is generally at least once, so consumers must be idempotent. A suitable consumer receipt key is the event ID scoped to the consumer.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.time.Instant;

public record SyirkahOutboxDeliveryReceipt(
        String consumerName,
        String eventId,
        String tenantId,
        String ledgerId,
        Instant processedAt,
        String outcome
) {
}
```

Use a unique database constraint on `(consumer_name, event_id)`. In the consumer transaction, insert the receipt and apply the event’s local effects together. Duplicate delivery should return the previously recorded outcome instead of repeating the business action.

### 7. Integration acceptance checklist

- Job and initial outbox event commit or roll back together.
- Concurrent job claims cannot create two valid leases.
- Stale workers cannot finalize state.
- Retry scheduling and its event are atomic.
- Duplicate event delivery is harmless.
- Deletion resumes from persisted per-object outcomes.
- Storage version mismatches block deletion.
- Security blocks and integrity failures trigger appropriate alerts.
- Tenant and ledger constraints apply to all repository reads and writes.
- Retention and tombstone policies are independently enforced.

The actual next engineering action is repository inspection. Before adding these interfaces and migrations, identify the platform’s existing outbox schema, transaction abstraction, job scheduler, and database dialect. Merge these contracts into those facilities rather than creating parallel infrastructure. Once that is done, the work should move to compile, integration tests, crash-recovery tests, and Sharia/accounting acceptance—not another layer of conceptual abstractions.


//////////////

## Next slice: tenant isolation, authorization, and audit-safe operations

The reliability layer now has a proposed job lifecycle, lease handling, retry policy, and outbox integration. The next safeguard is ensuring that every operation remains inside its authorized tenant and ledger scope—including background workers, evidence exports, archive retrieval, and deletion.

This is a proposed implementation slice. The names should be adapted to the platform’s existing security and persistence APIs.

### 1. Define an explicit operation scope

Do not trust tenant or ledger identifiers copied from a queued message alone. Resolve the persisted job, then validate its scope against the domain record being accessed.

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.Objects;

public record SyirkahEvidenceOperationScope(
        String tenantId,
        String ledgerId,
        String actorId,
        String purpose
) {
    public SyirkahEvidenceOperationScope {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(ledgerId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(purpose);
    }
}
```

Use an authorization port instead of embedding access rules in each worker:

```
package tech.kayys.syirkah.accounting.application.audit;

public interface SyirkahEvidenceAuthorizationService {

    SyirkahEvidenceAuthorizationDecision authorize(
            SyirkahEvidenceOperationScope scope,
            String resourceType,
            String resourceId,
            String action
    );
}
```

```
package tech.kayys.syirkah.accounting.application.audit;

import java.util.List;

public record SyirkahEvidenceAuthorizationDecision(
        boolean permitted,
        String policyId,
        String policyVersion,
        String decisionReference,
        List<String> reasonCodes
) {
    public SyirkahEvidenceAuthorizationDecision {
        reasonCodes = List.copyOf(reasonCodes);
    }
}
```

The implementation should delegate to the platform’s established authorization mechanism. A `permitted` result must be based on the authenticated actor or an explicitly authorized service identity—not a caller-supplied `actorId` by itself.

### 2. Enforce scope in repository operations

Every repository query that reads or mutates evidence should include tenant and ledger scope where applicable.

For example, avoid a repository method shaped like:

```
findById(exportId);
```

Prefer a scoped operation:

```
findById(tenantId, ledgerId, exportId);
```

Apply the same rule to:

- Evidence export revisions and manifests.
- Finalization audit evidence and validation reports.
- Retention evaluations and legal holds.
- Deletion intents, target receipts, and tombstones.
- Background jobs, leases, and retry records.
- Archive objects and integrity-check results.

Database constraints and row-level security can provide additional defense, but they do not replace application-level authorization. Avoid relying on a globally unique identifier as proof that a resource belongs to the current tenant.

### 3. Reauthorize irreversible operations

A deletion authorized when an intent was prepared may no longer be permitted when a worker executes it.

Before execution, revalidate:

1. The intent and export belong to the expected tenant and ledger.
2. The retention evaluation is still applicable.
3. No active legal hold blocks the operation.
4. The current policy still permits deletion.
5. The authorization is valid and the required separation of duties is satisfied.
6. The exact object versions still match the prepared targets.

If any condition fails, block execution, record the reason, and retain enough evidence for an authorized reviewer to investigate. Do not silently rewrite the original deletion intent.

### 4. Separate operational logs from evidentiary records

Operational logs help diagnose incidents; immutable evidence records establish what the system recorded about a business operation. They serve different purposes.

| Record                 | Purpose                        | Required treatment                              |
| ---------------------- | ------------------------------ | ----------------------------------------------- |
| Application log        | Diagnose execution failures    | Redact secrets and sensitive partner data       |
| Authorization decision | Explain access decisions       | Preserve decision reference and policy version  |
| Job attempt            | Explain retries and recovery   | Record attempt, failure code, and lease outcome |
| Deletion receipt       | Establish object-level outcome | Immutable and linked to the deletion intent     |
| Deletion tombstone     | Preserve deletion evidence     | Retain under its own retention policy           |
| Finalization audit     | Trace close finalization       | Append-only, scoped, and verifiable             |

Never log access tokens, credentials, raw idempotency keys, or unnecessary partner-level financial details. For idempotency correlation, store a cryptographic hash where appropriate.

### 5. Add security-focused tests

- A tenant cannot retrieve another tenant’s export by guessing its ID.
- A worker cannot execute a job using a mismatched tenant or ledger.
- A forged actor identifier cannot grant authorization.
- A newly active legal hold blocks a previously prepared deletion.
- Revoked authorization blocks execution.
- A cross-tenant object reference is rejected before storage access.
- An unauthorized archive request does not reveal whether another tenant’s resource exists.
- Security failures are auditable without exposing secrets.
- Retries and duplicate outbox events do not bypass authorization checks.

## What comes after this?

At this point, the design has covered evidence integrity, close finalization, retention, deletion, background-job recovery, and tenant-scope safeguards. Further conceptual interfaces will have diminishing value until they are matched to the actual codebase.

The next milestone is repository-aligned implementation and verification:

1. Inspect the real Maven module tree, existing interfaces, database migrations, and tests.
2. Map the proposed types to existing abstractions and eliminate duplicates.
3. Implement the adapters and migrations in the correct modules.
4. Compile the affected modules and run unit and integration tests.
5. Test concurrency, tenant isolation, crash recovery, deletion safeguards, and close-finalization rollback.
6. Complete accounting and Sharia acceptance review, then document release criteria.

I have not verified the repository’s current APIs or run Maven tests, so I cannot claim the implementation is complete. The next meaningful result should be code that compiles and passes tests against the actual project, rather than another standalone design layer.


/////////////////

## Next slice: Syirkah close acceptance tests

The next useful step is to turn the close and evidence rules into executable acceptance scenarios. These tests should protect the existing accounting workflow while enforcing Syirkah-specific requirements. The examples below are implementation templates; they have not been compiled against the repository, and the exact fixtures and method names must be adapted to its actual APIs.

### 1. Close-cycle acceptance matrix

| Scenario                                                      | Required behavior                                                               | Must not happen                                               |
| ------------------------------------------------------------- | ------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| All required calculation evidence exists                      | Close validation may proceed if all other gates pass                            | Evidence from another tenant or ledger is accepted            |
| One required calculation is missing                           | Validation fails with a specific finding                                        | Missing evidence is silently treated as zero or optional      |
| Contract version differs from expected version                | Validation fails or reports a blocking mismatch                                 | The current contract version replaces the historical version  |
| Policy snapshot differs                                       | Validation fails with a policy mismatch                                         | Current rules silently substitute for the pinned policy       |
| Calculation result hash differs                               | Validation fails integrity/reconciliation checks                                | A changed result is accepted under the old evidence reference |
| Finalization request is retried with the same key and payload | Return the recorded outcome                                                     | A second close or duplicate journal is created                |
| Same idempotency key is reused with a different payload       | Reject as key reuse conflict                                                    | The original request record is overwritten                    |
| Validation fails                                              | Period and snapshot finalization state remain unchanged                         | A partially finalized close remains visible                   |
| Correction is finalized                                       | Original snapshot remains traceable and is superseded according to the workflow | Original history is edited or deleted                         |
| Evidence replay runs                                          | Produce a comparison or verification result                                     | Journals, balances, or close snapshots are mutated            |

### 2. Test the reconciliation gate

Use fixtures where the expected calculation set is pinned to a specific policy snapshot and contract scope revision.

```
@Test
void rejectsCloseWhenRequiredCalculationEvidenceIsMissing() {
    var context = fixtures.validCloseValidationContext();
    var expectedSet = fixtures.expectedCalculationSet(context);
    var persistedLinks = fixtures.linksMissingOneRequiredCalculation(expectedSet);

    when(expectedCalculationResolver.resolve(context)).thenReturn(expectedSet);
    when(closeEvidenceRepository.findByCloseCycle(
            context.tenantId(),
            context.ledgerId(),
            context.closeCycleId()))
        .thenReturn(persistedLinks);

    var report = reconciler.reconcile(context);

    assertFalse(report.complete());
    assertTrue(report.findings().stream()
        .anyMatch(f -> f.code()
            == SyirkahCloseEvidenceFindingCode.MISSING_EXPECTED_EVIDENCE));
}
```

Adapt the fixture builders and repository signatures to the actual implementation. The important assertions are the blocking finding and the incomplete report—not the specific helper names.

Add companion tests for:

- A linked evidence ID that does not resolve to an accepted evidence record.
- Evidence with a mismatched tenant or ledger.
- A stale contract version.
- A mismatched policy snapshot or result hash.
- Duplicate evidence for the same expected calculation identity.
- An incomplete source-evidence record.
- A changed expected calculation set between validation and finalization.

Important invariant: the reconciler must not declare a close complete merely because the number of linked records equals the expected count. It must match each expected identity to the correct accepted evidence, with matching scope, versions, policy and hashes.

### 3. Test atomic finalization and idempotency

The central requirement is that a failed finalization leaves no partially applied close state.

```
@Test
void failedValidationDoesNotFinalizePeriodOrSnapshot() {
    var command = fixtures.validFinalizationCommand();
    fixtures.validationFails(command);

    assertThrows(
        CloseFinalizationRejectedException.class,
        () -> finalizationService.finalizeClose(command)
    );

    assertEquals(
        PeriodStatus.CLOSING,
        fixtures.reloadPeriod(command.fiscalPeriodId()).status()
    );

    assertEquals(
        SnapshotStatus.PENDING,
        fixtures.reloadSnapshot(command.snapshotId()).status()
    );

    assertFalse(fixtures.hasFinalizationSuccessAudit(command));
}
```

The statuses and exception above are illustrative; use the repository’s real state machine. Also verify that no success outbox event or successful idempotency outcome was committed.

Then cover retry behavior:

```
@Test
void retryWithSameKeyAndPayloadReturnsRecordedOutcome() {
    var command = fixtures.validFinalizationCommand();

    var first = finalizationService.finalizeClose(command);
    var second = finalizationService.finalizeClose(command);

    assertEquals(first.outcomeReference(), second.outcomeReference());
    assertEquals(1, fixtures.countSuccessfulFinalizations(command));
    assertEquals(1, fixtures.countFinalizationEvents(command));
}
```

Test the different-payload case separately: reusing the same key with a different snapshot, correction, period or policy snapshot must return a conflict, not reuse the prior outcome.

For database-backed tests, also exercise concurrent requests and transaction rollback. Sequential unit tests alone cannot prove that unique constraints, row locks or revision checks work correctly.

### 4. Test Syirkah-specific business transitions

These tests should validate the domain behavior rather than just whether a service returns successfully.

Partner enters an existing Syirkah

Verify that the existing Syirkah’s financial position is determined and its required close is completed before the new participation arrangement takes effect. Assert that the new participation does not retroactively change earlier allocations.

Partner exits

Verify the required close and settlement steps, the applicable allocation rules, and immutable history of prior partner movements. Do not mutate previously posted movements to represent the exit.

Profit and loss allocation

Verify that the calculation uses the applicable contract terms, effective dates, and pinned policy version. Include cases with missing inputs, rounding differences and a correction to a previously accepted result.

Full termination

Verify the required liquidation and liability settlement steps, determination of remaining net assets and result, allocation, return of capital, and termination record. Assert that unresolved liabilities or missing required evidence prevent an unsupported completion.

These are test requirements, not universal rulings on every contract. The precise allocation and settlement assertions must come from the approved contract terms and the applicable Sharia policy.

### 5. Test evidence integrity and replay

| Test                                                             | Expected result                                                     |
| ---------------------------------------------------------------- | ------------------------------------------------------------------- |
| An exported artifact’s content changes without updating its hash | `INVALID`                                                           |
| A required historical policy artifact is unavailable             | Limited or indeterminate verification, with an explicit finding     |
| A manifest references a missing required artifact                | Verification cannot be fully successful                             |
| A bundle contains an artifact from a different tenant or ledger  | Reject the bundle                                                   |
| Historical replay lacks the original calculation implementation  | Report that reproduction is unsupported or insufficiently evidenced |
| Current-rule comparison is requested                             | Preserve the original evidence and report the comparison separately |
| Replay is repeated                                               | No journal, balance, snapshot or settlement mutation                |

A hash establishes content integrity relative to the hashed bytes; it does not establish that a calculation was correct or that a Sharia treatment was substantively valid. Those require separate calculation and domain review.

### 6. Recommended implementation order

1. Add the reconciliation tests first, using the current domain types and repository interfaces.
2. Add transaction-backed finalization tests, including concurrency and rollback.
3. Add the Syirkah transition and allocation scenarios using approved business fixtures.
4. Add evidence-bundle tampering, missing-artifact and replay tests.
5. Run the module tests, then the full Maven reactor; resolve any mismatches against the actual code before treating the slice as complete.

The key outcome is a regression suite that proves a close can proceed only with the expected evidence, the pinned historical context, and an atomic finalization—and that corrections and replay preserve the original audit trail.


//////////////

## Next slice: Production recovery runbook for Syirkah close and evidence jobs

The acceptance tests define expected behavior. This slice defines how operators recover when production jobs fail, without corrupting accounting state, weakening evidence checks, or bypassing the close-validation gate.

## 1. Incident response procedures

A. Close finalization is rejected

Symptoms: blocking reconciliation findings, stale policy pin, contract scope revision changed, or snapshot lineage mismatch.

1. Keep the fiscal period in its current non-finalized state.
2. Retrieve the close-cycle ID, validation report ID, expected-set hash, and finding codes.
3. Identify the missing or mismatched evidence from authoritative records.
4. Correct the underlying source or produce a governed correction; do not manually mark the gate as passed.
5. Rerun validation against a newly pinned context.
6. Retry finalization using the intended idempotency semantics.

Stop condition: if the historical contract, policy or source evidence cannot be established, leave the close blocked and escalate to the designated accounting and Sharia reviewers.

B. Finalization request times out

Symptoms: the caller receives no result and cannot tell whether the transaction committed.

1. Query the idempotency record using its tenant and ledger scope.
2. If it has a completed outcome, return that recorded outcome.
3. If the outcome is still in progress, wait for the lease or transaction resolution and recheck.
4. If the transaction rolled back, retry through the normal atomic claim flow.
5. Reconcile period state, snapshot state, audit evidence and outbox records before any manual intervention.

Stop condition: conflicting state across the period, snapshot, audit and idempotency record. Do not issue a second request under a new key just to force progress.

C. Outbox backlog grows

Symptoms: finalization is committed, but downstream consumers are delayed.

1. Confirm that the source transaction committed and the outbox event exists.
2. Check worker health, lease expiry, retry counts and the oldest pending event.
3. Restore the consumer or transport dependency.
4. Resume delivery through the normal retry mechanism.
5. Confirm consumer deduplication and eventual delivery using the event ID.

Stop condition: duplicate downstream accounting effects or inconsistent event identity. Pause the affected consumer and reconcile before resuming.

D. Archive integrity verification fails

Symptoms: object missing, hash mismatch, unreadable content or unsupported artifact schema.

1. Preserve the failing export metadata and verification result.
2. Distinguish temporary storage unavailability from confirmed integrity failure.
3. Retry only transient failures using the bounded retry policy.
4. For a hash mismatch, preserve the affected object and investigate its versions, access logs and recorded hashes.
5. If an independently stored copy exists, verify it against the original manifest and declared hashes.
6. Issue a new export revision if needed; retain the original failed package and its lineage.

Stop condition: never rewrite the old manifest or replace an artifact silently to make verification pass.

E. Evidence deletion is partially completed

Symptoms: some object versions were deleted, while others failed or the worker crashed.

1. Mark the operation as partial or unresolved; do not report successful completion.
2. Load the immutable deletion intent and per-object execution receipts.
3. Recheck authorization, legal holds, retention policy, and exact object versions.
4. Resume only targets whose current versions match the authorized targets.
5. Treat already-absent objects idempotently only when the deletion workflow can establish that this is an acceptable outcome.
6. Persist all outcomes and the required tombstone/audit evidence before declaring completion.

Stop condition: a version mismatch, newly active legal hold, revoked authorization or missing required tombstone evidence. Never broaden the deletion target to “whatever is currently at this path.”

## 2. Alert-to-action mapping

| Alert                                    | First check                                 | Operator action                                           |
| ---------------------------------------- | ------------------------------------------- | --------------------------------------------------------- |
| Oldest pending job exceeds threshold     | Job status, lease, attempts                 | Inspect failure classification; retry only eligible jobs  |
| Dead-letter queue increases              | Failure code and affected handler           | Resolve the cause, then use an authorized redrive process |
| Integrity verification reports `INVALID` | Artifact and manifest hashes                | Preserve evidence and open an integrity incident          |
| Finalization conflicts rise              | Period revisions and competing requests     | Inspect locking, idempotency and state transitions        |
| Tenant authorization failures rise       | Scope and authorization decision references | Investigate access policy; do not bypass authorization    |
| Legal-hold blocks increase               | Hold status and export scope                | Confirm hold authority and retention evaluation           |
| Deletion remains partial                 | Per-object receipts and current versions    | Reconcile exact targets before resuming                   |
| Audit or outbox writes fail              | Transaction logs and persisted state        | Determine commit/rollback outcome before retrying         |

Thresholds should be configured from the service-level objectives and expected workload, rather than hard-coded without operational evidence.

## 3. Recovery safety rules

- No direct database edits to force a successful close. Use domain commands, controlled repair procedures and immutable audit records.
- No blind retries after an unknown outcome. Reconcile persisted state first.
- No bypass of tenant or ledger scope checks, including in administrative recovery tools.
- No mutation of posted accounting history. Use the established correction or reversal workflow.
- No silent evidence substitution. Missing historical artifacts must remain visible as missing or unsupported.
- No deletion during unresolved integrity or authorization failures.
- Every manual recovery needs an actor, reason, evidence references and recorded outcome.

## 4. Recovery drill checklist

Use this checklist in a staging environment before release.

### Staging recovery drill

0 of 9

Force close validation to fail and verify no finalization state is committed.

Simulate a client timeout after a successful commit and retry with the same idempotency key.

Stop an outbox consumer and verify backlog recovery without duplicate effects.

Make an archive temporarily unavailable and confirm bounded retries.

Alter a test artifact and confirm integrity verification rejects it.

Crash deletion after the first of multiple object targets and verify safe resumption.

Activate a legal hold after deletion preparation and verify execution is blocked.

Attempt cross-tenant access through both API and worker paths and verify denial.

Restore from archived evidence and verify it without relying on live source tables.

Reset checklist

Next implementation slice: make these procedures executable through an operator-facing recovery command layer: scoped job inspection, retry eligibility checks, dead-letter redrive with authorization, and immutable recovery audit records. Keep destructive deletion execution separate from ordinary retry tooling.

//////////////

## Next slice: Authorized operator recovery commands

The recovery runbook now needs a controlled execution path. Operators should be able to inspect failed jobs, determine whether a retry is safe, and redrive eligible dead-letter jobs without directly changing database state.

The design principle is: inspection is read-only; recovery is an authorized, idempotent domain operation; every recovery attempt is auditable.

## 1. Recovery command model

Use the existing evidence-job lifecycle (`PENDING`, `RUNNING`, `RETRY_WAIT`, `SUCCEEDED`, `DEAD_LETTERED`, `CANCELLED`) rather than introducing a second job state machine.

```
package tech.kayys.syirkah.accounting.application.evidence;

import java.time.Instant;

public record SyirkahEvidenceRecoveryCommand(
    String tenantId,
    String ledgerId,
    String jobId,
    String idempotencyKey,
    String reasonCode,
    String authorizationReference,
    String requestedBy,
    Instant requestedAt
) {
    public SyirkahEvidenceRecoveryCommand {
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(jobId, "jobId");
        requireText(idempotencyKey, "idempotencyKey");
        requireText(reasonCode, "reasonCode");
        requireText(authorizationReference, "authorizationReference");
        requireText(requestedBy, "requestedBy");
        if (requestedAt == null) {
            throw new IllegalArgumentException("requestedAt is required");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
```

This command is a proposal; adapt package names and conventions to the existing project. The caller-provided actor ID is an audit reference, not proof of identity. Authentication must come from the trusted security context, and authorization must be evaluated server-side.

## 2. Define recovery decisions explicitly

Do not let an operator command arbitrarily set a job to `PENDING`. First determine whether recovery is permitted and what operation is safe.

```
package tech.kayys.syirkah.accounting.application.evidence;

public enum SyirkahRecoveryDecision {
    RETRY_SCHEDULED,
    REDRIVE_ALLOWED,
    ALREADY_SUCCEEDED,
    ALREADY_IN_PROGRESS,
    BLOCKED_BY_AUTHORIZATION,
    BLOCKED_BY_JOB_STATE,
    BLOCKED_BY_FAILURE_CLASSIFICATION,
    BLOCKED_BY_SCOPE_MISMATCH,
    IDEMPOTENCY_CONFLICT
}
```

Use a result that records the decision and explains it without exposing sensitive job payloads.

```
package tech.kayys.syirkah.accounting.application.evidence;

import java.time.Instant;
import java.util.List;

public record SyirkahEvidenceRecoveryResult(
    String tenantId,
    String ledgerId,
    String jobId,
    String recoveryOperationId,
    SyirkahRecoveryDecision decision,
    List<String> reasonCodes,
    Instant recordedAt
) {
    public SyirkahEvidenceRecoveryResult {
        reasonCodes = List.copyOf(reasonCodes);
    }
}
```

A recovery result should be immutable. If an operator retries again later, create a new recovery operation linked to the same job rather than overwriting the previous outcome.

## 3. Add the authorization and recovery ports

These interfaces keep the application service independent of the database, job broker and identity provider.

```
package tech.kayys.syirkah.accounting.application.evidence;

public interface SyirkahEvidenceRecoveryAuthorizer {
    SyirkahRecoveryAuthorizationDecision authorize(
        SyirkahEvidenceRecoveryCommand command
    );
}
```

```
package tech.kayys.syirkah.accounting.application.evidence;

public record SyirkahRecoveryAuthorizationDecision(
    boolean permitted,
    String decisionReference,
    String policyVersion,
    String reasonCode
) {}
```

```
package tech.kayys.syirkah.accounting.application.evidence;

public interface SyirkahEvidenceRecoveryService {
    SyirkahEvidenceRecoveryResult recover(
        SyirkahEvidenceRecoveryCommand command
    );
}
```

The implementation also needs repository ports with tenant- and ledger-scoped operations, including:

- Load the job by `(tenantId, ledgerId, jobId)`.
- Atomically claim the recovery idempotency key.
- Compare the job's current state and revision before changing it.
- Persist the recovery audit record and corresponding outbox event.
- Record the recovery outcome.

Avoid global job lookups followed by application-side scope checks; scope the query itself.

## 4. Recovery workflow

\#chatgpt-mermaid-\_r_31n7\_{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;fill:rgb(237, 237, 237);}@keyframes edge-animation-frame{from{stroke-dashoffset:0;}}@keyframes dash{to{stroke-dashoffset:0;}}#chatgpt-mermaid-\_r_31n7\_ .edge-animation-slow{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 50s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_31n7\_ .edge-animation-fast{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 20s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_31n7\_ .error-icon{fill:rgb(48, 48, 48);}#chatgpt-mermaid-\_r_31n7\_ .error-text{fill:rgb(237, 237, 237);stroke:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31n7\_ .edge-thickness-normal{stroke-width:1px;}#chatgpt-mermaid-\_r_31n7\_ .edge-thickness-thick{stroke-width:3.5px;}#chatgpt-mermaid-\_r_31n7\_ .edge-pattern-solid{stroke-dasharray:0;}#chatgpt-mermaid-\_r_31n7\_ .edge-thickness-invisible{stroke-width:0;fill:none;}#chatgpt-mermaid-\_r_31n7\_ .edge-pattern-dashed{stroke-dasharray:3;}#chatgpt-mermaid-\_r_31n7\_ .edge-pattern-dotted{stroke-dasharray:2;}#chatgpt-mermaid-\_r_31n7\_ .marker{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_31n7\_ .marker.cross{stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_31n7\_ svg{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;}#chatgpt-mermaid-\_r_31n7\_ p{margin:0;}#chatgpt-mermaid-\_r_31n7\_ .label{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31n7\_ .cluster-label text{fill:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31n7\_ .cluster-label span{color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31n7\_ .cluster-label span p{background-color:transparent;}#chatgpt-mermaid-\_r_31n7\_ .label text,#chatgpt-mermaid-\_r_31n7\_ span{fill:rgb(237, 237, 237);color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31n7\_ .node rect,#chatgpt-mermaid-\_r_31n7\_ .node circle,#chatgpt-mermaid-\_r_31n7\_ .node ellipse,#chatgpt-mermaid-\_r_31n7\_ .node polygon,#chatgpt-mermaid-\_r_31n7\_ .node path{fill:rgb(9, 23, 44);stroke:rgb(31, 78, 148);stroke-width:1px;}#chatgpt-mermaid-\_r_31n7\_ .rough-node .label text,#chatgpt-mermaid-\_r_31n7\_ .node .label text,#chatgpt-mermaid-\_r_31n7\_ .image-shape .label,#chatgpt-mermaid-\_r_31n7\_ .icon-shape .label{text-anchor:middle;}#chatgpt-mermaid-\_r_31n7\_ .node .katex path{fill:#000;stroke:#000;stroke-width:1px;}#chatgpt-mermaid-\_r_31n7\_ .rough-node .label,#chatgpt-mermaid-\_r_31n7\_ .node .label,#chatgpt-mermaid-\_r_31n7\_ .image-shape .label,#chatgpt-mermaid-\_r_31n7\_ .icon-shape .label{text-align:center;}#chatgpt-mermaid-\_r_31n7\_ .node.clickable{cursor:pointer;}#chatgpt-mermaid-\_r_31n7\_ .root .anchor path{fill:rgb(175, 175, 175)!important;stroke-width:0;stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_31n7\_ .arrowheadPath{fill:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_31n7\_ .edgePath .path{stroke:rgb(175, 175, 175);stroke-width:1px;}#chatgpt-mermaid-\_r_31n7\_ .flowchart-link{stroke:rgb(175, 175, 175);fill:none;}#chatgpt-mermaid-\_r_31n7\_ .edgeLabel{background-color:rgb(0, 0, 0);text-align:center;}#chatgpt-mermaid-\_r_31n7\_ .edgeLabel p{background-color:rgb(0, 0, 0);}#chatgpt-mermaid-\_r_31n7\_ .edgeLabel rect{opacity:0.5;background-color:rgb(0, 0, 0);fill:rgb(0, 0, 0);}#chatgpt-mermaid-\_r_31n7\_ .labelBkg{background-color:rgba(0, 0, 0, 0.5);}#chatgpt-mermaid-\_r_31n7\_ .cluster rect{fill:rgb(48, 48, 48);stroke:rgba(255, 255, 255, 0.15);stroke-width:1px;}#chatgpt-mermaid-\_r_31n7\_ .cluster text{fill:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31n7\_ .cluster span{color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31n7\_ div.mermaidTooltip{position:absolute;text-align:center;max-width:200px;padding:2px;font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:12px;background:rgb(48, 48, 48);border:1px solid rgba(255, 255, 255, 0.15);border-radius:2px;pointer-events:none;z-index:100;}#chatgpt-mermaid-\_r_31n7\_ .flowchartTitleText{text-anchor:middle;font-size:18px;fill:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31n7\_ rect.text{fill:none;stroke-width:0;}#chatgpt-mermaid-\_r_31n7\_ .icon-shape,#chatgpt-mermaid-\_r_31n7\_ .image-shape{background-color:rgb(0, 0, 0);text-align:center;}#chatgpt-mermaid-\_r_31n7\_ .icon-shape p,#chatgpt-mermaid-\_r_31n7\_ .image-shape p{background-color:rgb(0, 0, 0);padding:2px;}#chatgpt-mermaid-\_r_31n7\_ .icon-shape .label rect,#chatgpt-mermaid-\_r_31n7\_ .image-shape .label rect{opacity:0.5;background-color:rgb(0, 0, 0);fill:rgb(0, 0, 0);}#chatgpt-mermaid-\_r_31n7\_ .label-icon{display:inline-block;height:1em;overflow:visible;vertical-align:-0.125em;}#chatgpt-mermaid-\_r_31n7\_ .node .label-icon path{fill:currentColor;stroke:revert;stroke-width:revert;}#chatgpt-mermaid-\_r_31n7\_ .node .neo-node{stroke:rgb(31, 78, 148);}#chatgpt-mermaid-\_r_31n7\_ [data-look="neo"].node rect,#chatgpt-mermaid-\_r_31n7\_ [data-look="neo"].cluster rect,#chatgpt-mermaid-\_r_31n7\_ [data-look="neo"].node polygon{stroke:url(#chatgpt-mermaid-\_r_31n7\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_31n7\_ [data-look="neo"].swimlane.cluster rect{filter:none;}#chatgpt-mermaid-\_r_31n7\_ [data-look="neo"].node path{stroke:url(#chatgpt-mermaid-\_r_31n7\_-gradient);stroke-width:1px;}#chatgpt-mermaid-\_r_31n7\_ [data-look="neo"].node .outer-path{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_31n7\_ [data-look="neo"].node .neo-line path{stroke:rgb(31, 78, 148);filter:none;}#chatgpt-mermaid-\_r_31n7\_ [data-look="neo"].node circle{stroke:url(#chatgpt-mermaid-\_r_31n7\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_31n7\_ [data-look="neo"].node circle .state-start{fill:#000000;}#chatgpt-mermaid-\_r_31n7\_ [data-look="neo"].icon-shape .icon{fill:url(#chatgpt-mermaid-\_r_31n7\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_31n7\_ [data-look="neo"].icon-shape .icon-neo path{stroke:url(#chatgpt-mermaid-\_r_31n7\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_31n7\_ .node text{font-size:14px;font-weight:600;letter-spacing:normal;fill:rgb(153, 206, 255);}#chatgpt-mermaid-\_r_31n7\_ .edgeLabels text{font-size:13px;font-weight:600;letter-spacing:-0.08px;fill:rgb(153, 206, 255);}#chatgpt-mermaid-\_r_31n7\_ .node tspan[font-weight="normal"],#chatgpt-mermaid-\_r_31n7\_ .edgeLabels tspan[font-weight="normal"]{font-weight:600;}#chatgpt-mermaid-\_r_31n7\_ .edgeLabel .label rect{opacity:1;rx:13px;ry:13px;fill:rgb(0, 14, 26);stroke:rgb(26, 62, 95);stroke-width:1px;}#chatgpt-mermaid-\_r_31n7\_ .node rect,#chatgpt-mermaid-\_r_31n7\_ .node circle,#chatgpt-mermaid-\_r_31n7\_ .node ellipse,#chatgpt-mermaid-\_r_31n7\_ .node polygon,#chatgpt-mermaid-\_r_31n7\_ .node path{fill:rgb(0, 40, 77);stroke:rgba(255, 255, 255, 0.1);stroke-width:1px;}#chatgpt-mermaid-\_r_31n7\_ .node rect{rx:16px;ry:16px;}#chatgpt-mermaid-\_r_31n7\_ .node.mermaid-decision .label-container{fill:rgb(0, 14, 26);stroke:rgb(26, 62, 95);stroke-dasharray:2,2;}#chatgpt-mermaid-\_r_31n7\_ .edgePaths .flowchart-link{stroke:rgb(175, 175, 175);stroke-width:1px;stroke-linecap:round;stroke-linejoin:round;}#chatgpt-mermaid-\_r_31n7\_ .marker{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_31n7\_ :root{--mermaid-font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";}Receive recovery commandAuthenticate callerLoad job within tenant andledger scopeAuthorize recovery actionAuthorized?Record denied attemptClaim idempotency keyatomicallyExisting request?Return recorded outcomeReturn conflictCheck job state and failureclassificationSafe to recover?Record blocked outcomeCompare-and-set job statePersist audit and outboxeventCommitWorker resumes throughnormal processingNoYesSame payloadDifferent payloadNew requestNoYes

The recovery service must use the existing transaction and job-lease rules. If the job is already running under a valid lease, do not create a competing execution. If a lease has expired, first establish whether the prior worker could have completed an external side effect.

### Failure classification policy

| Failure class       | Default recovery behavior                                                                           |
| ------------------- | --------------------------------------------------------------------------------------------------- |
| `RETRYABLE`         | Permit retry if attempts remain and backoff rules allow it                                          |
| `PERMANENT`         | Block automatic retry until the underlying cause is corrected and recovery is explicitly authorized |
| `SECURITY_BLOCK`    | Require an authorization or scope issue to be resolved before any redrive                           |
| `INTEGRITY_FAILURE` | Preserve the failure and investigate; do not blindly retry or replace evidence                      |

For archive deletion jobs, recovery must resume from persisted per-object outcomes and recheck legal holds, authorization and exact object versions. It must never restart by assuming that no deletion occurred.

## 5. Concurrency and audit requirements

Implement the following protections in the persistence adapter:

1. Unique idempotency constraint scoped to tenant and ledger.
2. Request hash comparison so a key cannot be reused with different command parameters.
3. Compare-and-set or row-lock protection for the job revision and status.
4. Recovery audit and outbox event written atomically with the job transition where they share a database.
5. A unique consumer receipt for each outbox event to prevent duplicate downstream effects.
6. A fresh transaction when resolving a uniqueness conflict; do not continue using a transaction already marked rollback-only.

Record the recovery operation ID, authenticated actor reference, authorization decision, reason code, prior and resulting state, job revision, and timestamp. Avoid logging credentials, raw idempotency keys, or full sensitive payloads.

## 6. Acceptance tests for this slice

- Same idempotency key and same command returns the original outcome.
- Same key with a different job or reason is rejected as a conflict.
- Cross-tenant job IDs cannot be inspected or recovered.
- A valid worker lease blocks a competing recovery.
- A permanent failure cannot be redriven under the retryable policy.
- A failed audit or outbox write does not leave an unaudited state transition.
- A worker crash after an external side effect does not cause that effect to be blindly repeated.
- A deletion job rechecks authorization and legal holds at execution time.

Implementation boundary: these are design and code templates, not verified repository changes. The next concrete step is to implement the transaction-backed recovery service and repository adapter against the actual job schema, then test concurrent recovery and crash recovery using the production database


/////////////

## Next slice: Recovery reconciliation for uncertain job outcomes

The operator recovery command now has a defined authorization path. The next gap is the hardest production failure case: a worker performs an external side effect, then crashes before recording success.

For Syirkah evidence jobs, this can happen when an archive object is written, an integrity result is stored, a deletion partially completes, or an outbox event is delivered before the consumer acknowledgement is persisted. Retrying blindly can duplicate work or misrepresent the evidence state.

The solution is a reconciliation step that determines what actually happened before the job is retried.

## 1. Model the observed outcome separately from the job status

A job marked `RUNNING` or `RETRY_WAIT` does not prove that its external operation did—or did not—complete.

```
package tech.kayys.syirkah.accounting.application.evidence;

public enum SyirkahExternalOutcomeStatus {
    NOT_STARTED,
    CONFIRMED_SUCCEEDED,
    CONFIRMED_NOT_APPLIED,
    PARTIALLY_APPLIED,
    OUTCOME_UNKNOWN,
    CONFLICTING_EVIDENCE
}
```

```
package tech.kayys.syirkah.accounting.application.evidence;

import java.time.Instant;
import java.util.List;

public record SyirkahExternalOutcomeObservation(
    String tenantId,
    String ledgerId,
    String jobId,
    String operationId,
    SyirkahExternalOutcomeStatus status,
    List<String> evidenceReferences,
    List<String> reasonCodes,
    Instant observedAt
) {
    public SyirkahExternalOutcomeObservation {
        evidenceReferences = List.copyOf(evidenceReferences);
        reasonCodes = List.copyOf(reasonCodes);
    }
}
```

This observation should be immutable. If later evidence changes the conclusion, append a new observation linked to the earlier one instead of editing history.

## 2. Add an operation-specific outcome reader

Do not use one generic rule to infer success for every job type. Each handler needs a way to inspect the external system using a stable operation identifier.

```
package tech.kayys.syirkah.accounting.application.evidence;

public interface SyirkahExternalOutcomeReader {
    SyirkahExternalOutcomeObservation observe(
        SyirkahEvidenceJob job,
        String operationId
    );
}
```

Examples of authoritative evidence by job type:

| Job type                    | Evidence to inspect                                                          |
| --------------------------- | ---------------------------------------------------------------------------- |
| `ARCHIVE_EXPORT`            | Object version, archive receipt, stored content hash and export manifest     |
| `VERIFY_ARCHIVE_INTEGRITY`  | Persisted verification result, artifact hashes and verification operation ID |
| `EXECUTE_EVIDENCE_DELETION` | Per-object deletion receipts, exact versions and deletion tombstone          |
| Outbox delivery             | Stable event ID and consumer's durable idempotency receipt                   |

A timeout or missing acknowledgement is not proof of failure. If the external system cannot establish the result, the observation must remain `OUTCOME_UNKNOWN`.

## 3. Define the reconciliation decision

```
package tech.kayys.syirkah.accounting.application.evidence;

public enum SyirkahReconciliationAction {
    MARK_SUCCEEDED,
    RETRY_SAME_OPERATION,
    RESUME_PARTIAL_OPERATION,
    WAIT_AND_RECHECK,
    REQUIRE_MANUAL_REVIEW,
    BLOCK_FOR_INTEGRITY_INVESTIGATION
}
```

Use a deterministic policy:

- `CONFIRMED_SUCCEEDED` → mark the job successful only after verifying all required durable artifacts and committing the corresponding audit/outbox state.
- `CONFIRMED_NOT_APPLIED` → retry only if the failure classification, authorization and retry policy permit it.
- `PARTIALLY_APPLIED` → resume from recorded per-item outcomes; do not restart the entire operation.
- `OUTCOME_UNKNOWN` → wait and recheck, or escalate after a bounded interval. Do not assume the operation failed.
- `CONFLICTING_EVIDENCE` → block automatic recovery and preserve the conflicting records for investigation.
- `NOT_STARTED` → retry only when the operation record and external-system contract establish that no side effect was initiated.

For deletion, a confirmed outcome must account for every authorized object target. One successful deletion does not make a multi-object deletion job successful.

## 4. Implement the reconciliation service

```
package tech.kayys.syirkah.accounting.application.evidence;

public interface SyirkahEvidenceJobReconciler {
    SyirkahJobReconciliationResult reconcile(
        String tenantId,
        String ledgerId,
        String jobId
    );
}
```

The application service should follow this sequence:

1. Load the job using tenant- and ledger-scoped access.
2. Verify the caller or scheduled worker is authorized to reconcile it.
3. Acquire a lease or revision-checked claim so two reconcilers cannot apply competing decisions.
4. Find the durable operation ID; never generate a new ID merely because the previous attempt timed out.
5. Read authoritative external outcome evidence.
6. Evaluate the observation using the job-type-specific policy.
7. Apply the decision with a compare-and-set state transition.
8. Persist the observation, reconciliation decision, audit record and outbox event atomically where supported.
9. Release the lease and expose the outcome to monitoring.

`SyirkahJobReconciliationResult` should include the job identity, observation ID, decision, reason codes and resulting job revision. It should not expose sensitive external payloads.

## 5. Critical acceptance tests

| Fault injection                                              | Required behavior                                                                      |
| ------------------------------------------------------------ | -------------------------------------------------------------------------------------- |
| Archive write succeeds, worker crashes before success update | Reconciliation finds the existing object and receipt; no duplicate archive operation   |
| External system times out without a durable receipt          | Outcome remains unknown; no blind retry                                                |
| Deletion succeeds for two of three targets                   | Job remains partial and resumes only the outstanding target after revalidation         |
| Two reconcilers claim the same job                           | Only one applies the state transition                                                  |
| Existing operation receipt conflicts with the stored hash    | Automatic recovery is blocked                                                          |
| Outbox event is delivered twice                              | Consumer's durable idempotency record prevents duplicate effects                       |
| Reconciliation audit write fails                             | The system does not report a completed reconciliation without its required audit trail |
| Job references another tenant's operation                    | Access is denied and no external operation is performed                                |

## 6. One important implementation constraint

Reconciliation is only reliable if the original operation has a stable identifier and the external system exposes enough evidence to establish its outcome. Where that is impossible, use an explicitly unknown state and a controlled review path rather than pretending the operation is exactly-once.

Next slice: implement a durable operation ledger—one record per external side effect, with a stable operation ID, request hash, target version, execution attempts, observed outcome and receipt references. This gives archive, deletion and outbox recovery a shared audit vocabulary without forcing them into identical execution semantics.

//////////////

## Next slice: Durable external-operation ledger

The reconciliation service needs a durable record of each external side effect. This ledger links a Syirkah evidence job to what it attempted, which exact resource it targeted, and what the external system ultimately confirmed.

The ledger is not a replacement for the job table, outbox, archive manifest, or deletion tombstone. It connects them so recovery can distinguish a failed attempt from an operation whose outcome is still uncertain.

## 1. Operation record

Use one stable operation ID for a logical side effect across retries. Track individual attempts separately so retries do not overwrite history.

```
package tech.kayys.syirkah.accounting.application.evidence;

import java.time.Instant;

public record SyirkahExternalOperation(
    String tenantId,
    String ledgerId,
    String operationId,
    String jobId,
    String operationType,
    String targetReference,
    String targetVersion,
    String requestHash,
    String idempotencyKeyHash,
    SyirkahExternalOutcomeStatus outcomeStatus,
    String receiptReference,
    Instant createdAt,
    Instant updatedAt,
    long revision
) {}
```

`targetReference` should be an opaque resource identifier, not a credential-bearing URL. The hash must be calculated from a versioned canonical representation of the operation request, not `toString()` or arbitrary serialized object bytes.

For deletion, record the authorized object version or generation. For archive creation, record the export revision and expected content hash. For outbox delivery, use the stable event ID and consumer identity.

## 2. Keep operation history append-only

A logical operation can have several execution attempts. Preserve each attempt as a separate record.

```
package tech.kayys.syirkah.accounting.application.evidence;

import java.time.Instant;

public record SyirkahExternalOperationAttempt(
    String tenantId,
    String ledgerId,
    String operationId,
    int attemptNumber,
    String workerId,
    String leaseTokenHash,
    String requestHash,
    String outcomeCode,
    String externalReceiptReference,
    String failureCode,
    Instant startedAt,
    Instant completedAt
) {}
```

Recommended persistence constraints:

- Unique `(tenant_id, ledger_id, operation_id)`.
- Unique `(tenant_id, ledger_id, operation_id, attempt_number)`.
- A request hash cannot change across retries of the same logical operation.
- Each attempt references an existing operation in the same tenant and ledger.
- Attempts and confirmed receipts are immutable; later observations are appended.
- Lease tokens and idempotency secrets are not stored or logged in plaintext.

Keep the operation's current state as a transactional projection for efficient recovery. The attempt history and external receipts provide the evidence used to verify that projection.

## 3. Define repository ports

```
package tech.kayys.syirkah.accounting.application.evidence;

import java.util.List;
import java.util.Optional;

public interface SyirkahExternalOperationRepository {

    Optional<SyirkahExternalOperation> findById(
        String tenantId,
        String ledgerId,
        String operationId
    );

    SyirkahExternalOperation insertIfAbsent(
        SyirkahExternalOperation operation
    );

    SyirkahExternalOperationAttempt appendAttempt(
        SyirkahExternalOperationAttempt attempt
    );

    List<SyirkahExternalOperationAttempt> findAttempts(
        String tenantId,
        String ledgerId,
        String operationId
    );

    boolean compareAndSetOutcome(
        String tenantId,
        String ledgerId,
        String operationId,
        long expectedRevision,
        SyirkahExternalOutcomeStatus newStatus,
        String receiptReference
    );
}
```

These signatures are illustrative. In production, the adapter must define how it handles uniqueness conflicts, immutable fields, state transitions and transaction boundaries. `insertIfAbsent` must not silently accept an existing operation whose request hash or target differs.

## 4. Operation lifecycle

\#chatgpt-mermaid-\_r_31qk\_{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;fill:rgb(237, 237, 237);}@keyframes edge-animation-frame{from{stroke-dashoffset:0;}}@keyframes dash{to{stroke-dashoffset:0;}}#chatgpt-mermaid-\_r_31qk\_ .edge-animation-slow{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 50s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_31qk\_ .edge-animation-fast{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 20s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_31qk\_ .error-icon{fill:rgb(48, 48, 48);}#chatgpt-mermaid-\_r_31qk\_ .error-text{fill:rgb(237, 237, 237);stroke:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31qk\_ .edge-thickness-normal{stroke-width:1px;}#chatgpt-mermaid-\_r_31qk\_ .edge-thickness-thick{stroke-width:3.5px;}#chatgpt-mermaid-\_r_31qk\_ .edge-pattern-solid{stroke-dasharray:0;}#chatgpt-mermaid-\_r_31qk\_ .edge-thickness-invisible{stroke-width:0;fill:none;}#chatgpt-mermaid-\_r_31qk\_ .edge-pattern-dashed{stroke-dasharray:3;}#chatgpt-mermaid-\_r_31qk\_ .edge-pattern-dotted{stroke-dasharray:2;}#chatgpt-mermaid-\_r_31qk\_ .marker{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_31qk\_ .marker.cross{stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_31qk\_ svg{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;}#chatgpt-mermaid-\_r_31qk\_ p{margin:0;}#chatgpt-mermaid-\_r_31qk\_ .label{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31qk\_ .cluster-label text{fill:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31qk\_ .cluster-label span{color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31qk\_ .cluster-label span p{background-color:transparent;}#chatgpt-mermaid-\_r_31qk\_ .label text,#chatgpt-mermaid-\_r_31qk\_ span{fill:rgb(237, 237, 237);color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31qk\_ .node rect,#chatgpt-mermaid-\_r_31qk\_ .node circle,#chatgpt-mermaid-\_r_31qk\_ .node ellipse,#chatgpt-mermaid-\_r_31qk\_ .node polygon,#chatgpt-mermaid-\_r_31qk\_ .node path{fill:rgb(9, 23, 44);stroke:rgb(31, 78, 148);stroke-width:1px;}#chatgpt-mermaid-\_r_31qk\_ .rough-node .label text,#chatgpt-mermaid-\_r_31qk\_ .node .label text,#chatgpt-mermaid-\_r_31qk\_ .image-shape .label,#chatgpt-mermaid-\_r_31qk\_ .icon-shape .label{text-anchor:middle;}#chatgpt-mermaid-\_r_31qk\_ .node .katex path{fill:#000;stroke:#000;stroke-width:1px;}#chatgpt-mermaid-\_r_31qk\_ .rough-node .label,#chatgpt-mermaid-\_r_31qk\_ .node .label,#chatgpt-mermaid-\_r_31qk\_ .image-shape .label,#chatgpt-mermaid-\_r_31qk\_ .icon-shape .label{text-align:center;}#chatgpt-mermaid-\_r_31qk\_ .node.clickable{cursor:pointer;}#chatgpt-mermaid-\_r_31qk\_ .root .anchor path{fill:rgb(175, 175, 175)!important;stroke-width:0;stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_31qk\_ .arrowheadPath{fill:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_31qk\_ .edgePath .path{stroke:rgb(175, 175, 175);stroke-width:1px;}#chatgpt-mermaid-\_r_31qk\_ .flowchart-link{stroke:rgb(175, 175, 175);fill:none;}#chatgpt-mermaid-\_r_31qk\_ .edgeLabel{background-color:rgb(0, 0, 0);text-align:center;}#chatgpt-mermaid-\_r_31qk\_ .edgeLabel p{background-color:rgb(0, 0, 0);}#chatgpt-mermaid-\_r_31qk\_ .edgeLabel rect{opacity:0.5;background-color:rgb(0, 0, 0);fill:rgb(0, 0, 0);}#chatgpt-mermaid-\_r_31qk\_ .labelBkg{background-color:rgba(0, 0, 0, 0.5);}#chatgpt-mermaid-\_r_31qk\_ .cluster rect{fill:rgb(48, 48, 48);stroke:rgba(255, 255, 255, 0.15);stroke-width:1px;}#chatgpt-mermaid-\_r_31qk\_ .cluster text{fill:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31qk\_ .cluster span{color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31qk\_ div.mermaidTooltip{position:absolute;text-align:center;max-width:200px;padding:2px;font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:12px;background:rgb(48, 48, 48);border:1px solid rgba(255, 255, 255, 0.15);border-radius:2px;pointer-events:none;z-index:100;}#chatgpt-mermaid-\_r_31qk\_ .flowchartTitleText{text-anchor:middle;font-size:18px;fill:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_31qk\_ rect.text{fill:none;stroke-width:0;}#chatgpt-mermaid-\_r_31qk\_ .icon-shape,#chatgpt-mermaid-\_r_31qk\_ .image-shape{background-color:rgb(0, 0, 0);text-align:center;}#chatgpt-mermaid-\_r_31qk\_ .icon-shape p,#chatgpt-mermaid-\_r_31qk\_ .image-shape p{background-color:rgb(0, 0, 0);padding:2px;}#chatgpt-mermaid-\_r_31qk\_ .icon-shape .label rect,#chatgpt-mermaid-\_r_31qk\_ .image-shape .label rect{opacity:0.5;background-color:rgb(0, 0, 0);fill:rgb(0, 0, 0);}#chatgpt-mermaid-\_r_31qk\_ .label-icon{display:inline-block;height:1em;overflow:visible;vertical-align:-0.125em;}#chatgpt-mermaid-\_r_31qk\_ .node .label-icon path{fill:currentColor;stroke:revert;stroke-width:revert;}#chatgpt-mermaid-\_r_31qk\_ .node .neo-node{stroke:rgb(31, 78, 148);}#chatgpt-mermaid-\_r_31qk\_ [data-look="neo"].node rect,#chatgpt-mermaid-\_r_31qk\_ [data-look="neo"].cluster rect,#chatgpt-mermaid-\_r_31qk\_ [data-look="neo"].node polygon{stroke:url(#chatgpt-mermaid-\_r_31qk\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_31qk\_ [data-look="neo"].swimlane.cluster rect{filter:none;}#chatgpt-mermaid-\_r_31qk\_ [data-look="neo"].node path{stroke:url(#chatgpt-mermaid-\_r_31qk\_-gradient);stroke-width:1px;}#chatgpt-mermaid-\_r_31qk\_ [data-look="neo"].node .outer-path{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_31qk\_ [data-look="neo"].node .neo-line path{stroke:rgb(31, 78, 148);filter:none;}#chatgpt-mermaid-\_r_31qk\_ [data-look="neo"].node circle{stroke:url(#chatgpt-mermaid-\_r_31qk\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_31qk\_ [data-look="neo"].node circle .state-start{fill:#000000;}#chatgpt-mermaid-\_r_31qk\_ [data-look="neo"].icon-shape .icon{fill:url(#chatgpt-mermaid-\_r_31qk\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_31qk\_ [data-look="neo"].icon-shape .icon-neo path{stroke:url(#chatgpt-mermaid-\_r_31qk\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_31qk\_ .node text{font-size:14px;font-weight:600;letter-spacing:normal;fill:rgb(153, 206, 255);}#chatgpt-mermaid-\_r_31qk\_ .edgeLabels text{font-size:13px;font-weight:600;letter-spacing:-0.08px;fill:rgb(153, 206, 255);}#chatgpt-mermaid-\_r_31qk\_ .node tspan[font-weight="normal"],#chatgpt-mermaid-\_r_31qk\_ .edgeLabels tspan[font-weight="normal"]{font-weight:600;}#chatgpt-mermaid-\_r_31qk\_ .edgeLabel .label rect{opacity:1;rx:13px;ry:13px;fill:rgb(0, 14, 26);stroke:rgb(26, 62, 95);stroke-width:1px;}#chatgpt-mermaid-\_r_31qk\_ .node rect,#chatgpt-mermaid-\_r_31qk\_ .node circle,#chatgpt-mermaid-\_r_31qk\_ .node ellipse,#chatgpt-mermaid-\_r_31qk\_ .node polygon,#chatgpt-mermaid-\_r_31qk\_ .node path{fill:rgb(0, 40, 77);stroke:rgba(255, 255, 255, 0.1);stroke-width:1px;}#chatgpt-mermaid-\_r_31qk\_ .node rect{rx:16px;ry:16px;}#chatgpt-mermaid-\_r_31qk\_ .node.mermaid-decision .label-container{fill:rgb(0, 14, 26);stroke:rgb(26, 62, 95);stroke-dasharray:2,2;}#chatgpt-mermaid-\_r_31qk\_ .edgePaths .flowchart-link{stroke:rgb(175, 175, 175);stroke-width:1px;stroke-linecap:round;stroke-linejoin:round;}#chatgpt-mermaid-\_r_31qk\_ .marker{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_31qk\_ :root{--mermaid-font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";}Create operation recordCommit operation and joblinkageClaim execution attemptCall external system withstable operation IDOutcome established?Record receipt andobservationRecord unknown outcomeReconcile andcompare-and-set stateReconciliation workerinspects external systemTerminal outcome?Retry or resume under policyClose operation and updatejobYesNoNoYes

The operation record should be created before initiating the external side effect. If the job and operation ledger share a database, commit the job linkage and operation record in one transaction.

Do not hold a database transaction open while making a slow network request. Instead:

1. Claim the operation with a lease and revision.
2. Persist the attempt start.
3. Call the external system using its idempotency mechanism where supported.
4. Persist the receipt or an unknown-outcome observation.
5. Reconcile if the worker crashes or the result is ambiguous.

A lease prevents stale workers from committing conflicting database state, but it cannot undo a side effect already performed externally.

## 5. Enforce legal state transitions

The following is a conceptual transition policy, not a replacement for the existing `SyirkahExternalOutcomeStatus` enum.

| Current state         | New observation                          | Allowed outcome         |
| --------------------- | ---------------------------------------- | ----------------------- |
| `NOT_STARTED`         | Confirmed request accepted and completed | `CONFIRMED_SUCCEEDED`   |
| `NOT_STARTED`         | No request was initiated                 | Remain `NOT_STARTED`    |
| `OUTCOME_UNKNOWN`     | Matching durable receipt found           | `CONFIRMED_SUCCEEDED`   |
| `OUTCOME_UNKNOWN`     | External system confirms no application  | `CONFIRMED_NOT_APPLIED` |
| `OUTCOME_UNKNOWN`     | Some targets completed                   | `PARTIALLY_APPLIED`     |
| Any nonterminal state | Conflicting receipt or request hash      | `CONFLICTING_EVIDENCE`  |
| `PARTIALLY_APPLIED`   | Remaining targets verified and completed | `CONFIRMED_SUCCEEDED`   |

A transition must be backed by a persisted observation or authoritative receipt. Do not allow an operator command to force `CONFIRMED_SUCCEEDED` merely by changing the database state.

For multi-target operations such as deletion, use a child operation or target-outcome record for every authorized object version. Aggregate success only when all required targets have an acceptable, evidenced outcome.

## 6. Acceptance tests

Add tests for these failure boundaries:

- Crash before external call: recovery can establish that no operation was applied.
- Crash after external success: the same operation ID is reused, and the existing receipt is discovered.
- Request hash changes: the retry is rejected rather than silently modifying the operation.
- Stale worker returns late: its outdated revision or lease cannot overwrite a newer result.
- Partial deletion: completed targets remain recorded; only outstanding targets are reconsidered.
- Duplicate receipt: the receipt is processed idempotently.
- Cross-tenant reference: the operation cannot be retrieved or reconciled outside its tenant and ledger.
- Audit persistence failure: the system does not claim a fully completed audited workflow when required records were not committed.

## 7. Integration boundary

Keep each subsystem authoritative for its own facts:

- The job repository owns scheduling, retries and job status.
- The operation ledger owns logical side-effect identity and attempt history.
- The external system owns its actual resource state and receipts.
- The archive manifest and verifier own evidence-package integrity results.
- The deletion tombstone owns the final record of authorized deletion outcomes.
- The outbox and consumer receipt own event-delivery coordination and deduplication.

The operation ledger links these records; it should not invent a successful outcome when one of the authoritative sources is unavailable.

Next slice: define the database migration, uniqueness constraints, indexes and transactional adapter for the operation ledger. That is where the proposed model becomes enforceable under concurrent workers and production recovery. The current proposal remains uncompiled and unverified against the repository's actual schema.


//////////////


# Ultimate next plan for the Syirkah platform

The next phase should move from designing individual safeguards to integrating, implementing, verifying, and releasing them within the existing Syirkah platform. The goal is not to keep adding abstractions indefinitely; it is to make the existing accounting workflow reliable, auditable, Sharia-aware, and operationally recoverable.

The sequence below is the proposed end-to-end plan. It is not a claim that the proposed components already exist in the repository.

## Overall roadmap

Phase 1 — Inspect the existing implementation

First priority

Establish the actual Maven modules, domain types, database schema, transaction boundaries, job lifecycle, outbox implementation and tests. Map each proposed component to existing code before creating anything new.

Exit criterion: a verified implementation inventory and a gap list based on actual source code.

Phase 2 — Persist external-operation history

Implement the operation ledger, attempt history, migrations, unique constraints, revision checks and tenant-scoped repository adapters. Reuse the existing job and outbox infrastructure wherever possible.

Exit criterion: concurrent operation creation and retry tests pass against the project's actual database engine.

Phase 3 — Reconciliation and recovery

Implement operation-specific outcome readers for archive creation, integrity checks, deletion and outbox delivery. Add safe retry, partial-operation resumption, dead-letter redrive and authorized recovery audit.

Exit criterion: crash-after-side-effect tests recover without duplicate effects or falsely reported success.

Phase 4 — Close evidence and finalization

Connect the expected calculation set, accepted evidence reconciliation, policy and contract-scope pins, finalization idempotency, immutable audit evidence and transactional outbox.

Exit criterion: missing, stale, mismatched or incomplete evidence blocks finalization without partial state changes.

Phase 5 — Evidence export, verification and retention

Complete versioned manifests, standalone verification, revision lineage, retention evaluations, archive integrity sweeps and guarded deletion with recoverable per-object outcomes.

Exit criterion: evidence can be independently verified, limitations remain explicit, and retention or legal holds cannot be bypassed.

Phase 6 — Syirkah domain acceptance

Verify profit/loss allocation, effective contract versions, partner entry and exit, settlement immutability, termination, correction lineage and historical calculation replay against approved business rules.

Exit criterion: accounting and Sharia reviewers approve the defined cases, assumptions and exception handling.

Phase 7 — Operational readiness and controlled release

Exercise failure recovery, authorization boundaries, migration rollback, monitoring, alerts, restore procedures and release gates. Deploy progressively and reconcile production outcomes before expanding rollout.

Exit criterion: documented sign-off, verified recovery drills and an actionable rollback plan.

## Detailed work breakdown

| Workstream          | Deliverables                                                              | Main dependencies                       |
| ------------------- | ------------------------------------------------------------------------- | --------------------------------------- |
| Repository baseline | Module inventory, API mapping, schema inventory, baseline test results    | None                                    |
| Operation ledger    | Migration, repository adapter, canonical request hashing, attempt records | Baseline                                |
| Recovery engine     | Outcome readers, reconciler, CAS transitions, retry policy                | Operation ledger                        |
| Operator controls   | Scoped inspection, authorization, idempotent redrive, recovery audit      | Recovery engine                         |
| Close validation    | Expected-set resolver, evidence reconciler, pinned revisions              | Existing close workflow                 |
| Finalization        | Atomic state transition, idempotency record, audit and outbox             | Close validation                        |
| Evidence assurance  | Bundle exporter, standalone verifier, retention and deletion workflow     | Existing export/job systems             |
| Domain correctness  | Syirkah acceptance fixtures and historical replay tests                   | Contract and calculation implementation |
| Release operations  | Observability, incident runbooks, migration and recovery drills           | All critical paths                      |

## Critical design decisions to settle early

These decisions affect several phases, so resolve them during the repository inspection rather than independently implementing competing abstractions.

1. Reuse versus duplicate infrastructure. Confirm whether the platform already has generic job, outbox, audit, idempotency and artifact-storage abstractions. Extend them instead of introducing parallel systems.
2. Transaction boundaries. Keep database state transitions, audit records and outbox insertion atomic when they share a database. Treat external storage operations as recoverable workflows.
3. Canonical hashing. Version the canonical format for request hashes, artifacts, manifests and bundles. Do not rely on default object serialization or `toString()`.
4. Historical evidence. Never silently substitute current contracts, policies or calculation engines for unavailable historical versions.
5. Tenant isolation. Enforce tenant and ledger scope in repository queries, background workers, recovery operations and evidence verification.
6. Deletion semantics. Separate retention eligibility, authorization, execution, per-object outcomes and tombstone retention.
7. Domain approval. Keep technical integrity verification separate from accounting correctness and Sharia approval.

## Interactive execution checklist

Track progress as the actual implementation is verified. Checking a box here is only a planning record; it does not inspect the repository or prove a task has passed.

## Implementation tracker

### 0/11

Phase 1

Inspect Maven modules, existing types, schema and tests

Phase 2

Implement operation-ledger migration and persistence adapter

Add concurrent creation, revision and tenant-isolation tests

Phase 3

Implement external outcome readers and reconciliation

Add safe retry, partial resumption and operator redrive

Phase 4

Integrate expected evidence set and close validation pins

Verify atomic finalization, audit, outbox and idempotency

Phase 5

Verify export manifests, artifact hashes and standalone bundles

Test retention, legal holds and deletion recovery

Phase 6

Pass approved Syirkah accounting and Sharia acceptance cases

Phase 7

Complete recovery drills, release gates and rollback rehearsal

Copy statusReset

## What to do next, concretely

Start with Phase 1, then implement Phase 2 against the real schema. We have already outlined many domain and service contracts; adding more conceptual interfaces before inspecting the actual code would create a risk of duplicate abstractions and incompatible APIs.

The first implementation pass should:

1. Locate the existing job, outbox, audit, idempotency and artifact-storage implementations.
2. Map the proposed operation ledger onto the current persistence conventions.
3. Write the migration and repository adapter.
4. Add concurrency and failure-injection tests.
5. Compile the affected Maven modules and fix integration issues before moving on.

I have not verified a repository build or compiled the proposed code. The roadmap is therefore the implementation sequence and completion criteria—not a claim that any phase has passed.

The guiding rule for the entire plan is simple: preserve the existing accounting core, add only the missing Syirkah-specific controls, and require demonstrable correctness before advancing each release gate.
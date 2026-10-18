Yes. The next step should make **`tutup buku` a real transactional boundary**, not merely another close task.

The important change is:

> **`SyirkahCloseSnapshot` must be frozen before the accounting period becomes `LOCKED`, and snapshot creation + final validation + period lock must be coordinated atomically.**

The existing framework currently has `CloseCycle.approve()` and `lock()` as separate in-memory operations, while `FinancialCloseService.approveAndLock()` immediately calls `lockEngine.lockPeriod()`. syirkah-framework(1) syirkah-framework(1)

## 1. Final close boundary

I would make the sequence:

```text
OPEN
  │
  ├─ ordinary close tasks
  ├─ SYIRKAH_RESULT
  ├─ SYIRKAH_ALLOCATION
  ├─ SYIRKAH_DISTRIBUTION
  │
  ▼
VALIDATING
  │
  ├─ validate all mandatory tasks
  ├─ validate Syirkah calculation
  ├─ validate allocation
  ├─ reconcile distribution accounting
  ├─ build SyirkahCloseSnapshot
  ├─ freeze snapshot
  │
  ▼
APPROVED
  │
  ├─ acquire final close transaction/lock
  ├─ verify snapshot still valid
  ├─ lock fiscal period
  └─ mark CloseCycle LOCKED
  │
  ▼
LOCKED
```

The crucial invariant becomes:

```text
LOCKED => SyirkahCloseSnapshot exists and is FROZEN
```

Never allow:

```text
LOCKED + no snapshot
```

---

# 2. Make the snapshot immutable

The existing snapshot concept should become an actual immutable historical artifact.

```java
public final class SyirkahCloseSnapshot {

    private final String snapshotId;
    private final String tenantId;
    private final String ledgerId;
    private final String closeCycleId;
    private final String calculationId;

    private final String contractId;
    private final int contractVersion;

    private final LocalDate periodStart;
    private final LocalDate periodEnd;

    private final BigDecimal revenue;
    private final BigDecimal directCosts;
    private final BigDecimal operatingCosts;
    private final BigDecimal otherCosts;
    private final BigDecimal netResult;

    private final ResultType resultType;

    private final List<PartnerClosePositionSnapshot> partnerPositions;
    private final List<PartnerAllocationSnapshot> allocations;
    private final List<LossResponsibilitySnapshot> responsibilities;

    private final SyirkahPositionReconciliation reconciliation;

    private final String policySnapshotId;
    private final String policySnapshotHash;

    private final String snapshotHash;

    private final Instant frozenAt;
    private final String frozenBy;

    private final int schemaVersion;

    public SyirkahCloseSnapshot(
            String snapshotId,
            String tenantId,
            String ledgerId,
            String closeCycleId,
            String calculationId,
            String contractId,
            int contractVersion,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal revenue,
            BigDecimal directCosts,
            BigDecimal operatingCosts,
            BigDecimal otherCosts,
            BigDecimal netResult,
            ResultType resultType,
            List<PartnerClosePositionSnapshot> partnerPositions,
            List<PartnerAllocationSnapshot> allocations,
            List<LossResponsibilitySnapshot> responsibilities,
            SyirkahPositionReconciliation reconciliation,
            String policySnapshotId,
            String policySnapshotHash,
            String snapshotHash,
            Instant frozenAt,
            String frozenBy,
            int schemaVersion) {

        this.snapshotId = Objects.requireNonNull(snapshotId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.ledgerId = Objects.requireNonNull(ledgerId);
        this.closeCycleId = Objects.requireNonNull(closeCycleId);
        this.calculationId = Objects.requireNonNull(calculationId);
        this.contractId = Objects.requireNonNull(contractId);

        if (contractVersion <= 0) {
            throw new IllegalArgumentException("Contract version must be positive");
        }

        this.contractVersion = contractVersion;
        this.periodStart = Objects.requireNonNull(periodStart);
        this.periodEnd = Objects.requireNonNull(periodEnd);

        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("Period end cannot precede period start");
        }

        this.revenue = Objects.requireNonNull(revenue);
        this.directCosts = Objects.requireNonNull(directCosts);
        this.operatingCosts = Objects.requireNonNull(operatingCosts);
        this.otherCosts = Objects.requireNonNull(otherCosts);
        this.netResult = Objects.requireNonNull(netResult);

        this.resultType = Objects.requireNonNull(resultType);

        this.partnerPositions = List.copyOf(
                Objects.requireNonNull(partnerPositions));

        this.allocations = List.copyOf(
                Objects.requireNonNull(allocations));

        this.responsibilities = List.copyOf(
                Objects.requireNonNull(responsibilities));

        this.reconciliation = Objects.requireNonNull(reconciliation);

        this.policySnapshotId = Objects.requireNonNull(policySnapshotId);
        this.policySnapshotHash = Objects.requireNonNull(policySnapshotHash);
        this.snapshotHash = Objects.requireNonNull(snapshotHash);

        this.frozenAt = Objects.requireNonNull(frozenAt);
        this.frozenBy = Objects.requireNonNull(frozenBy);

        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Schema version must be positive");
        }

        this.schemaVersion = schemaVersion;
    }

    public String snapshotId() {
        return snapshotId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String ledgerId() {
        return ledgerId;
    }

    public String closeCycleId() {
        return closeCycleId;
    }

    public String calculationId() {
        return calculationId;
    }

    public String contractId() {
        return contractId;
    }

    public int contractVersion() {
        return contractVersion;
    }

    public LocalDate periodStart() {
        return periodStart;
    }

    public LocalDate periodEnd() {
        return periodEnd;
    }

    public BigDecimal netResult() {
        return netResult;
    }

    public List<PartnerClosePositionSnapshot> partnerPositions() {
        return partnerPositions;
    }

    public List<PartnerAllocationSnapshot> allocations() {
        return allocations;
    }

    public SyirkahPositionReconciliation reconciliation() {
        return reconciliation;
    }

    public String policySnapshotId() {
        return policySnapshotId;
    }

    public String policySnapshotHash() {
        return policySnapshotHash;
    }

    public String snapshotHash() {
        return snapshotHash;
    }

    public Instant frozenAt() {
        return frozenAt;
    }

    public String frozenBy() {
        return frozenBy;
    }

    public int schemaVersion() {
        return schemaVersion;
    }
}
```

There should be **no setters**.

The snapshot is historical evidence, not a mutable projection.

---

# 3. Repository must enforce immutability

```java
public interface SyirkahCloseSnapshotRepository {

    Uni<Optional<SyirkahCloseSnapshot>> findByCloseCycle(
            String tenantId,
            String ledgerId,
            String closeCycleId
    );

    Uni<Optional<SyirkahCloseSnapshot>> findByCalculation(
            String tenantId,
            String ledgerId,
            String calculationId
    );

    Uni<SyirkahCloseSnapshot> save(
            SyirkahCloseSnapshot snapshot
    );
}
```

Database constraints:

```sql
CREATE UNIQUE INDEX ux_syirkah_close_snapshot_cycle
ON syirkah_close_snapshot (
    tenant_id,
    ledger_id,
    close_cycle_id
);

CREATE UNIQUE INDEX ux_syirkah_close_snapshot_calculation
ON syirkah_close_snapshot (
    tenant_id,
    ledger_id,
    calculation_id
);
```

And importantly:

```sql
ALTER TABLE syirkah_close_snapshot
ADD CONSTRAINT chk_syirkah_close_snapshot_immutable
CHECK (frozen_at IS NOT NULL);
```

The application/database adapter should treat the table as **append-only**.

No:

```text
UPDATE snapshot
```

No:

```text
DELETE snapshot
```

If something is wrong, create a correction/reversal process rather than modifying history.

---

# 4. Snapshot hash

The hash must be calculated from canonical business content.

Do **not** hash Java serialization.

Use a deterministic canonical representation:

```java
public interface SyirkahSnapshotHasher {

    String hash(SyirkahCloseSnapshotContent content);
}
```

Where:

```java
public record SyirkahCloseSnapshotContent(
        int schemaVersion,
        String tenantId,
        String ledgerId,
        String closeCycleId,
        String calculationId,
        String contractId,
        int contractVersion,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal revenue,
        BigDecimal directCosts,
        BigDecimal operatingCosts,
        BigDecimal otherCosts,
        BigDecimal netResult,
        ResultType resultType,
        List<PartnerClosePositionSnapshot> partnerPositions,
        List<PartnerAllocationSnapshot> allocations,
        List<LossResponsibilitySnapshot> responsibilities,
        SyirkahPositionReconciliation reconciliation,
        String policySnapshotId,
        String policySnapshotHash
) {}
```

Then:

```text
canonical content
       │
       ▼
SHA-256
       │
       ▼
snapshotHash
```

This gives us:

```text
same business content
        =
same hash
```

and therefore supports later historical integrity verification.

---

# 5. Snapshot builder

The builder should not fetch arbitrary live information after it starts.

```java
public interface SyirkahCloseSnapshotBuilder {

    Uni<SyirkahCloseSnapshot> build(
            SyirkahCloseSnapshotBuildRequest request
    );
}
```

Request:

```java
public record SyirkahCloseSnapshotBuildRequest(
        String tenantId,
        String ledgerId,
        String closeCycleId,
        String calculationId,
        String operator
) {
    public SyirkahCloseSnapshotBuildRequest {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID is required");
        }

        if (ledgerId == null || ledgerId.isBlank()) {
            throw new IllegalArgumentException("Ledger ID is required");
        }

        if (closeCycleId == null || closeCycleId.isBlank()) {
            throw new IllegalArgumentException("Close cycle ID is required");
        }

        if (calculationId == null || calculationId.isBlank()) {
            throw new IllegalArgumentException("Calculation ID is required");
        }

        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("Operator is required");
        }
    }
}
```

The builder performs:

```text
Load CloseCalculation
       │
       ├── contract/version
       ├── policy snapshot
       ├── opening positions
       ├── posted partner movements
       ├── allocations
       ├── responsibilities
       │
       ▼
Build partner positions
       │
       ▼
Validate roll-forwards
       │
       ▼
Validate allocation totals
       │
       ▼
Validate movement integrity
       │
       ▼
Validate reconciliation
       │
       ▼
Calculate snapshot hash
       │
       ▼
Create immutable snapshot
```

---

# 6. Critical rule: pending movements block snapshot

This remains especially important.

For the period:

```text
POSTED
    → included

CANCELLED
    → excluded

PENDING_ACCOUNTING
    → BLOCK CLOSE

unknown status
    → BLOCK CLOSE
```

Never silently do:

```java
movements.stream()
    .filter(m -> m.status() == POSTED)
```

because that can hide an accounting failure.

Instead:

```java
for (PartnerAccountMovement movement : movements) {

    if (movement.status() == MovementStatus.PENDING_ACCOUNTING) {
        throw new SyirkahCloseBlockedException(
                "Pending partner accounting movement: "
                        + movement.sourceType()
                        + "/"
                        + movement.sourceId());
    }

    if (movement.status() != MovementStatus.POSTED
            && movement.status() != MovementStatus.CANCELLED) {
        throw new SyirkahCloseBlockedException(
                "Unsupported partner movement status: "
                        + movement.status());
    }
}
```

---

# 7. Final close transaction

This is the most important new abstraction.

```java
public interface SyirkahFinalCloseTransaction {

    Uni<CloseCycle> finalizeClose(
            String tenantId,
            String ledgerId,
            String closeCycleId,
            String approver
    );
}
```

The implementation must execute one database transaction:

```text
BEGIN

  lock close_cycle row

  verify status = VALIDATING

  verify all mandatory tasks completed

  verify Syirkah result task completed

  verify Syirkah allocation task completed

  verify distribution task completed

  verify distribution reconciliation

  verify no pending partner movements

  verify calculation

  verify policy snapshot

  build/fetch close snapshot

  verify snapshot hash

  persist snapshot

  mark close cycle APPROVED

  lock fiscal period

  mark close cycle LOCKED

  write audit evidence

  write outbox events

COMMIT
```

If anything fails:

```text
ROLLBACK
```

Therefore we never get:

```text
period locked
+
snapshot missing
```

---

# 8. Do not use the current `approveAndLock()` as-is

The existing implementation currently does:

```java
cycle.approve(approver);
cycle.lock();
lockEngine.lockPeriod(...);
```

That is too weak for Syirkah finalization. syirkah-framework(1)

Change the responsibility so that:

```java
public Uni<CloseCycle> approveAndLock(
        CloseCycleId cycleId,
        String approver) {

    CloseCycle cycle = load(cycleId);

    if (isSyirkahClose(cycle)) {
        return syirkahFinalCloseTransaction.finalizeClose(
                cycle.tenantId(),
                cycle.ledgerId(),
                cycle.id().value(),
                approver
        );
    }

    return finalizeOrdinaryClose(cycle, approver);
}
```

This preserves the existing generic close workflow for non-Syirkah accounting.

---

# 9. Add an explicit finalization gate

I recommend a dedicated object rather than putting everything into `CloseCycle`.

```java
public record SyirkahFinalCloseReadiness(
        boolean resultReady,
        boolean allocationReady,
        boolean distributionReady,
        boolean snapshotReady,
        boolean accountingReconciled,
        boolean pendingMovementsAbsent,
        List<String> errors
) {

    public SyirkahFinalCloseReadiness {
        errors = errors == null
                ? List.of()
                : List.copyOf(errors);
    }

    public boolean ready() {
        return errors.isEmpty()
                && resultReady
                && allocationReady
                && distributionReady
                && snapshotReady
                && accountingReconciled
                && pendingMovementsAbsent;
    }
}
```

Then:

```java
public interface SyirkahFinalCloseReadinessService {

    Uni<SyirkahFinalCloseReadiness> check(
            String tenantId,
            String ledgerId,
            String closeCycleId
    );
}
```

This gives the UI/API a meaningful answer:

```text
Cannot close period

- Distribution accounting: OK
- Partner allocation: OK
- Snapshot: NOT READY
- Pending movements: 2
- Accounting reconciliation: FAILED
```

rather than a generic:

```text
IllegalStateException
```

---

# 10. The exact irreversible point

There should be one clearly defined boundary:

```text
Before final transaction:
    period is still open
    snapshot can be prepared
    close can fail safely

Inside final transaction:
    snapshot becomes historical artifact
    period lock is established

After COMMIT:
    period is immutable
    snapshot is historical truth
```

I would make the semantic rule:

> **`SyirkahCloseSnapshot` becomes authoritative only when the transaction that persists it also successfully commits the fiscal-period lock.**

This avoids a dangerous state where a snapshot says "closed" but the accounting period remains open.

---

# 11. Events

On successful commit, emit:

```text
SYIRKAH_CLOSE_SNAPSHOT_FROZEN
SYIRKAH_CLOSE_APPROVED
ACCOUNTING_PERIOD_LOCKED
SYIRKAH_CLOSE_COMPLETED
```

Do not publish these as external events before commit.

Use the existing outbox mechanism.

So:

```text
DB transaction
   │
   ├── snapshot
   ├── close cycle
   ├── fiscal period lock
   ├── audit evidence
   └── outbox records
          │
       COMMIT
          │
          ▼
    Outbox dispatcher
          │
          ▼
     external consumers
```

This is consistent with the framework's existing close and accounting infrastructure rather than introducing another event mechanism. syirkah-framework(1)

---

# 12. Audit evidence

The snapshot itself should contain the cryptographic identity:

```text
snapshotId
snapshotHash
policySnapshotId
policySnapshotHash
calculationId
contractId/version
closeCycleId
```

The audit evidence should additionally record:

```java
public record SyirkahCloseFinalizationEvidence(
        String closeCycleId,
        String snapshotId,
        String snapshotHash,
        String policySnapshotId,
        String policySnapshotHash,
        String calculationId,
        String contractId,
        int contractVersion,
        String approvedBy,
        Instant finalizedAt
) {}
```

Then an auditor can reconstruct:

```text
Close Cycle
   ↓
Calculation
   ↓
Policy Snapshot
   ↓
Partner Movements
   ↓
Partner Positions
   ↓
Allocations
   ↓
Distribution Journals
   ↓
Close Snapshot
   ↓
Period Lock
```

That is the real value of this architecture.

---

# 13. One more important protection

The existing `PeriodLockEngine` is currently an in-memory `ConcurrentHashMap`. syirkah-framework(1)

That is fine as a prototype, but it **cannot be the financial source of truth**.

Eventually the actual lock must live in the database:

```sql
CREATE UNIQUE INDEX ux_fiscal_period_lock
ON fiscal_period_lock (
    tenant_id,
    ledger_id,
    fiscal_period_id
);
```

And journal posting must check that persistent lock inside the same transaction as posting.

Otherwise:

```text
Node A:
  checks period → open

Node B:
  locks period

Node A:
  posts journal
```

can still happen.

The real invariant must be enforced at the database transaction boundary.

---

## Final architecture

We now have a clean chain:

```text
Syirkah business movements
        ↓
Syirkah calculation
        ↓
Partner allocations
        ↓
Distribution obligations
        ↓
Distribution journals
        ↓
Accounting reconciliation
        ↓
SyirkahCloseSnapshot
        ↓
FINAL CLOSE TRANSACTION
        ↓
Fiscal period lock
        ↓
Historical reporting
```

And the key rule is:

> **After `LOCKED`, reporting for that period must read the frozen `SyirkahCloseSnapshot`, not current partner accounts or mutable movements.**

That gives us a proper Islamic/Syirkah `tutup buku` boundary without replacing the accounting engine that already exists.

/////////////


Next, I would close the loop on **historical reporting and period reopening/correction**.

Once `SyirkahCloseSnapshot` becomes the authoritative record after `LOCKED`, the next architectural question is:

> What happens when someone asks for a Syirkah report for an old period, and what happens if a locked period must be corrected?

## 1. Introduce the historical reporting resolver

Do not let reporting services decide independently whether to read live movements or snapshots.

```java
public interface SyirkahPeriodStatementProvider {

    Uni<SyirkahPeriodStatement> getStatement(
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate periodStart,
            LocalDate periodEnd
    );
}
```

Behind it:

```java
public interface SyirkahPeriodSnapshotResolver {

    Uni<Optional<SyirkahCloseSnapshot>> findLockedSnapshot(
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate periodStart,
            LocalDate periodEnd
    );
}
```

Then the rule is explicit:

```text
period OPEN
    ↓
live read models / posted movements

period LOCKED
    ↓
SyirkahCloseSnapshot
```

No report should accidentally mix:

```text
March snapshot
+
April current PartnerAccount
```

---

# 2. Add provenance to every statement

A financial report should tell us where its numbers came from.

```java
public enum StatementProvenance {
    LIVE,
    LOCKED_SNAPSHOT
}
```

```java
public record SyirkahStatementProvenance(
        StatementProvenance source,
        String snapshotId,
        String calculationId,
        String policySnapshotId,
        String policySnapshotHash,
        String snapshotHash
) {}
```

For an open period:

```text
source = LIVE
snapshotId = null
```

For a locked period:

```text
source = LOCKED_SNAPSHOT
snapshotId = ...
snapshotHash = ...
```

This is extremely useful for audit, UI, exports, and reconciliation.

---

# 3. Partner statement

The first concrete statement should be the partner statement.

```java
public record SyirkahPartnerStatement(
        String tenantId,
        String ledgerId,
        String contractId,
        String partyId,
        LocalDate periodStart,
        LocalDate periodEnd,

        BigDecimal openingCapital,
        BigDecimal capitalContributions,
        BigDecimal capitalReturns,
        BigDecimal capitalImpairments,
        BigDecimal closingCapital,

        BigDecimal openingCurrentAccount,
        BigDecimal profitAllocations,
        BigDecimal lossAllocations,
        BigDecimal withdrawals,
        BigDecimal closingCurrentAccount,

        BigDecimal cashSettlements,

        SyirkahStatementProvenance provenance
) {}
```

The roll-forward must be explicit:

```text
closingCapital
=
openingCapital
+ contributions
- returns
- impairments
```

and:

```text
closingCurrentAccount
=
openingCurrentAccount
+ profit allocations
- loss allocations
- withdrawals
```

Cash settlement remains separate.

That prevents a very common accounting mistake:

> profit allocation ≠ automatic capital contribution.

---

# 4. Consolidated Syirkah statement

Then provide the contract-level statement.

```java
public record SyirkahPartnerConsolidatedStatement(
        String contractId,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<SyirkahPartnerStatement> partners,

        BigDecimal totalOpeningCapital,
        BigDecimal totalClosingCapital,

        BigDecimal totalProfitAllocation,
        BigDecimal totalLossAllocation,

        SyirkahStatementProvenance provenance
) {}
```

Mandatory invariants:

```java
public void validate() {
    BigDecimal partnerClosingCapital =
            partners.stream()
                    .map(SyirkahPartnerStatement::closingCapital)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (partnerClosingCapital.compareTo(totalClosingCapital) != 0) {
        throw new IllegalStateException(
                "Partner closing capital does not reconcile");
    }

    BigDecimal partnerProfit =
            partners.stream()
                    .map(SyirkahPartnerStatement::profitAllocations)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (partnerProfit.compareTo(totalProfitAllocation) != 0) {
        throw new IllegalStateException(
                "Partner profit allocations do not reconcile");
    }

    BigDecimal partnerLoss =
            partners.stream()
                    .map(SyirkahPartnerStatement::lossAllocations)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (partnerLoss.compareTo(totalLossAllocation) != 0) {
        throw new IllegalStateException(
                "Partner loss allocations do not reconcile");
    }
}
```

These are not merely reporting checks. They are **financial invariants**.

---

# 5. Historical period lookup

We should not determine "locked" merely from a report request.

Introduce:

```java
public interface AccountingPeriodStatusProvider {

    Uni<PeriodStatus> getStatus(
            String tenantId,
            String ledgerId,
            LocalDate date
    );
}
```

```java
public enum PeriodStatus {
    OPEN,
    CLOSING,
    LOCKED
}
```

Then:

```java
public Uni<SyirkahPeriodStatement> getStatement(...) {

    return periodStatusProvider
            .getStatus(tenantId, ledgerId, periodEnd)
            .chain(status -> {

                if (status == PeriodStatus.LOCKED) {
                    return snapshotBasedStatement(...);
                }

                return liveStatement(...);
            });
}
```

This centralizes the historical boundary.

---

# 6. Never silently fall back from snapshot to live

This is critical.

Bad:

```java
if (snapshot.isPresent()) {
    return fromSnapshot(snapshot.get());
}

return fromLiveMovements(...);
```

For a locked period, that could hide corrupted/missing historical data.

Instead:

```java
if (status == PeriodStatus.LOCKED) {

    SyirkahCloseSnapshot snapshot =
            snapshotResolver.findLockedSnapshot(...)
                    .await()
                    .orElseThrow(() ->
                            new HistoricalSnapshotMissingException(
                                    "Locked period has no Syirkah close snapshot"));

    return fromSnapshot(snapshot);
}
```

The invariant is:

```text
LOCKED + missing snapshot
        =
DATA INTEGRITY FAILURE
```

not:

```text
LOCKED + missing snapshot
        =
use current data
```

---

# 7. Contract transition uses snapshots

This connects directly to the transition work we already designed.

Suppose:

```text
Contract V1
January–March
A = 100m
B = 50m
```

March is locked.

The transition starts in April.

The new contract must use:

```text
March SyirkahCloseSnapshot
```

as its predecessor state.

Not:

```text
current PartnerAccount
```

because current PartnerAccount may already contain April activity.

So:

```text
V1
 │
 ├── March movements
 │
 ▼
March CloseSnapshot
 │
 ├── B exits
 ├── A retained
 │
 ▼
Transition
 │
 ▼
V2 OpeningSnapshot
```

This makes partner entry/exit historically deterministic.

---

# 8. Now solve locked-period correction

We should **not** add:

```java
snapshot.update(...)
```

or:

```java
period.unlock()
```

as normal application operations.

Instead introduce an explicit correction/reopen workflow.

```java
public enum PeriodCorrectionStatus {
    REQUESTED,
    APPROVED,
    REOPENED,
    CORRECTED,
    RECLOSED,
    CANCELLED
}
```

```java
public final class SyirkahPeriodCorrection {

    private final String correctionId;
    private final String tenantId;
    private final String ledgerId;
    private final String fiscalPeriodId;

    private final String originalSnapshotId;
    private final String reason;

    private PeriodCorrectionStatus status;

    // lifecycle methods...
}
```

The important distinction:

> A correction creates a new historical version. It does not mutate the original snapshot.

---

# 9. Snapshot versioning

Add:

```java
private final int snapshotVersion;
private final String supersedesSnapshotId;
```

Example:

```text
March snapshot v1
    │
    │ correction
    ▼
March snapshot v2
```

The original remains preserved.

```text
v1
hash = ABC123
status = SUPERSEDED

v2
hash = XYZ987
status = ACTIVE
supersedes = v1
```

This gives us a full audit trail.

---

# 10. But do not casually reopen a fiscal period

A correction should require explicit authorization.

Something like:

```java
public interface SyirkahPeriodCorrectionPolicy {

    CorrectionDecision evaluate(
            SyirkahPeriodCorrectionContext context
    );
}
```

The context should include:

```text
reason
requester
original snapshot
affected journal entries
affected partner allocations
affected settlements
materiality
approval requirement
```

The policy decides whether reopening is allowed.

The normal path becomes:

```text
LOCKED
   ↓
Correction requested
   ↓
Maker review
   ↓
Checker approval
   ↓
Period reopened
   ↓
Corrective accounting
   ↓
Recalculate Syirkah
   ↓
New allocation
   ↓
New distribution/reversal
   ↓
New snapshot
   ↓
Reclose
```

---

# 11. Never rewrite old journals

If a correction changes a previous accounting result, use:

```text
original journal
      ↓
reversal journal
      ↓
corrected journal
```

rather than:

```text
UPDATE journal_entry
```

The existing journal architecture already has reversal semantics, so Syirkah should use those rather than introducing another accounting mechanism.

---

# 12. Historical chain

We are now approaching a very strong audit model:

```text
Contract
   │
   ▼
Close Calculation
   │
   ▼
Policy Snapshot
   │
   ▼
Partner Movements
   │
   ▼
Partner Positions
   │
   ▼
Allocations
   │
   ▼
Accounting Journals
   │
   ▼
Close Snapshot v1
   │
   ├── correction
   │
   ▼
Close Snapshot v2
   │
   ▼
Current historical truth
```

And every important artifact has identifiers linking it back to its predecessor.

---

# 13. Add snapshot verification

We should have a service specifically for historical integrity:

```java
public interface SyirkahSnapshotIntegrityService {

    Uni<SyirkahSnapshotIntegrityResult> verify(
            String tenantId,
            String ledgerId,
            String snapshotId
    );
}
```

Result:

```java
public record SyirkahSnapshotIntegrityResult(
        String snapshotId,
        String storedHash,
        String calculatedHash,
        boolean hashValid,
        boolean reconciliationValid,
        boolean policyValid,
        boolean lineageValid,
        List<String> errors
) {

    public boolean valid() {
        return hashValid
                && reconciliationValid
                && policyValid
                && lineageValid
                && errors.isEmpty();
    }
}
```

This becomes valuable for:

- audit
- regulator inspection
- financial export
- migration verification
- disaster recovery validation
- automated integrity monitoring.

---

# 14. Reporting architecture now becomes clean

```text
                    ┌─────────────────────┐
                    │ Syirkah Statement   │
                    │ Provider            │
                    └──────────┬──────────┘
                               │
                     ┌─────────▼─────────┐
                     │ Period Status     │
                     │ Provider          │
                     └─────────┬─────────┘
                               │
                 ┌─────────────┴─────────────┐
                 │                           │
              OPEN/CLOSING                LOCKED
                 │                           │
                 ▼                           ▼
        Live Read Models            Close Snapshot
                 │                           │
                 └─────────────┬─────────────┘
                               ▼
                    Syirkah Statements
                               │
                               ▼
                         Reconciliation
```

This also keeps the architecture reusable for:

- Mudharabah
- Musyarakah
- other Syirkah variants

without embedding accounting logic into the reporting layer.

---

## The next implementation after this

The next logical piece is **the correction/reopen mechanism itself**, because now we have established the immutable historical boundary.

I would implement it in this order:

1. `SyirkahPeriodCorrection`
2. correction approval/maker-checker
3. persistent period reopen transaction
4. journal reversal/correction linkage
5. recalculation from the corrected boundary
6. new allocation/distribution
7. new `SyirkahCloseSnapshot` version
8. re-close transaction
9. snapshot lineage and integrity verification
10. correction audit/outbox events

That will make `tutup buku` not only **close correctly**, but also **correctly recover from mistakes without destroying historical evidence**.

///////////

Good. The next step is to make **locked-period correction a controlled workflow**, while preserving the original `tutup buku` snapshot forever.

One important adjustment from the existing framework: `FiscalPeriod` already exposes `reopen()`, but it explicitly refuses to reopen a `LOCKED` period without an audit unlock. syirkah-framework(1) So we should **not bypass that rule**. Syirkah correction needs a separate authorized unlock transaction.

## 1. Correction aggregate

```java
public enum SyirkahPeriodCorrectionStatus {
    REQUESTED,
    APPROVED,
    REOPENED,
    CORRECTING,
    READY_TO_RECLOSE,
    RECLOSED,
    CANCELLED
}
```

```java
public final class SyirkahPeriodCorrection {

    private final String correctionId;
    private final String tenantId;
    private final String ledgerId;
    private final String fiscalPeriodId;
    private final String closeCycleId;
    private final String originalSnapshotId;

    private final String reason;
    private final String requestedBy;
    private final Instant requestedAt;

    private SyirkahPeriodCorrectionStatus status;

    private String approvedBy;
    private Instant approvedAt;

    private String newSnapshotId;
    private Instant completedAt;

    public SyirkahPeriodCorrection(
            String correctionId,
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String closeCycleId,
            String originalSnapshotId,
            String reason,
            String requestedBy) {

        this.correctionId = require(correctionId, "correctionId");
        this.tenantId = require(tenantId, "tenantId");
        this.ledgerId = require(ledgerId, "ledgerId");
        this.fiscalPeriodId = require(fiscalPeriodId, "fiscalPeriodId");
        this.closeCycleId = require(closeCycleId, "closeCycleId");
        this.originalSnapshotId = require(originalSnapshotId, "originalSnapshotId");
        this.reason = require(reason, "reason");
        this.requestedBy = require(requestedBy, "requestedBy");

        this.requestedAt = Instant.now();
        this.status = SyirkahPeriodCorrectionStatus.REQUESTED;
    }

    public void approve(String approver) {
        requireDifferentActor(requestedBy, approver);

        if (status != SyirkahPeriodCorrectionStatus.REQUESTED) {
            throw new IllegalStateException(
                    "Correction must be REQUESTED");
        }

        this.approvedBy = require(approver, "approver");
        this.approvedAt = Instant.now();
        this.status = SyirkahPeriodCorrectionStatus.APPROVED;
    }

    public void markReopened() {
        requireStatus(SyirkahPeriodCorrectionStatus.APPROVED);
        this.status = SyirkahPeriodCorrectionStatus.REOPENED;
    }

    public void beginCorrection() {
        requireStatus(SyirkahPeriodCorrectionStatus.REOPENED);
        this.status = SyirkahPeriodCorrectionStatus.CORRECTING;
    }

    public void readyToReclose() {
        requireStatus(SyirkahPeriodCorrectionStatus.CORRECTING);
        this.status = SyirkahPeriodCorrectionStatus.READY_TO_RECLOSE;
    }

    public void markReclosed(String snapshotId) {
        requireStatus(SyirkahPeriodCorrectionStatus.READY_TO_RECLOSE);

        this.newSnapshotId = require(snapshotId, "snapshotId");
        this.completedAt = Instant.now();
        this.status = SyirkahPeriodCorrectionStatus.RECLOSED;
    }

    public void cancel() {
        if (status == SyirkahPeriodCorrectionStatus.RECLOSED) {
            throw new IllegalStateException(
                    "Reclosed correction cannot be cancelled");
        }

        this.status = SyirkahPeriodCorrectionStatus.CANCELLED;
    }

    private void requireStatus(SyirkahPeriodCorrectionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Expected " + expected + " but was " + status);
        }
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static void requireDifferentActor(
            String maker,
            String checker) {

        if (maker.equals(checker)) {
            throw new IllegalStateException(
                    "Maker-checker separation is required");
        }
    }
}
```

This gives correction its own lifecycle rather than abusing `CloseCycle`.

---

# 2. Correction repository

```java
public interface SyirkahPeriodCorrectionRepository {

    Uni<Optional<SyirkahPeriodCorrection>> findById(
            String tenantId,
            String ledgerId,
            String correctionId);

    Uni<Optional<SyirkahPeriodCorrection>> findActiveForPeriod(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId);

    Uni<SyirkahPeriodCorrection> save(
            SyirkahPeriodCorrection correction);
}
```

Database protection:

```sql
CREATE UNIQUE INDEX ux_syirkah_active_period_correction
ON syirkah_period_correction (
    tenant_id,
    ledger_id,
    fiscal_period_id
)
WHERE status NOT IN ('RECLOSED', 'CANCELLED');
```

Therefore we cannot have:

```text
March correction A → active
March correction B → active
```

at the same time.

---

# 3. Correction policy

Do not allow every locked period to be reopened automatically.

```java
public record SyirkahPeriodCorrectionContext(
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        String closeCycleId,
        String snapshotId,
        String requestedBy,
        String reason
) {}
```

```java
public record SyirkahCorrectionDecision(
        boolean allowed,
        boolean requiresAdditionalApproval,
        List<String> errors
) {
    public SyirkahCorrectionDecision {
        errors = errors == null
                ? List.of()
                : List.copyOf(errors);
    }
}
```

```java
public interface SyirkahPeriodCorrectionPolicy {

    SyirkahCorrectionDecision evaluate(
            SyirkahPeriodCorrectionContext context);
}
```

This leaves room for policies such as:

```text
ordinary correction
material correction
regulatory correction
fraud investigation
system migration correction
year-end correction
```

without hardcoding those rules into the aggregate.

---

# 4. The actual reopen transaction

This is where the existing `FiscalPeriod.reopen()` limitation matters.

We need a port:

```java
public interface FiscalPeriodCorrectionTransaction {

    Uni<Void> reopenForCorrection(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String correctionId,
            String approvedBy);
}
```

The implementation performs:

```text
BEGIN

lock fiscal_period

verify status = LOCKED

lock SyirkahCloseSnapshot

verify correction = APPROVED

verify snapshot belongs to period

verify no other active correction

record audit unlock

change fiscal period:
    LOCKED → OPEN

mark correction:
    APPROVED → REOPENED

COMMIT
```

The important point is that reopening is **not**:

```java
period.reopen();
```

from an arbitrary application service.

It is an authorized financial operation.

---

# 5. Preserve the original snapshot

Do not mutate:

```text
March Snapshot v1
```

Instead introduce snapshot lifecycle:

```java
public enum SyirkahCloseSnapshotStatus {
    ACTIVE,
    SUPERSEDED
}
```

And extend the snapshot metadata:

```java
private final int snapshotVersion;
private final String supersedesSnapshotId;
private final SyirkahCloseSnapshotStatus status;
```

Initial close:

```text
March Snapshot v1
status = ACTIVE
supersedes = null
```

Correction:

```text
March Snapshot v1
status = SUPERSEDED

March Snapshot v2
status = ACTIVE
supersedes = v1
```

The original hash remains untouched.

---

# 6. Snapshot lineage

Repository:

```java
public interface SyirkahCloseSnapshotRepository {

    Uni<Optional<SyirkahCloseSnapshot>> findActiveForPeriod(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId);

    Uni<List<SyirkahCloseSnapshot>> findHistory(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId);

    Uni<Optional<SyirkahCloseSnapshot>> findById(
            String tenantId,
            String ledgerId,
            String snapshotId);

    Uni<SyirkahCloseSnapshot> save(
            SyirkahCloseSnapshot snapshot);

    Uni<Void> supersede(
            String tenantId,
            String ledgerId,
            String snapshotId,
            String supersededBySnapshotId);
}
```

Database:

```sql
CREATE UNIQUE INDEX ux_syirkah_active_snapshot
ON syirkah_close_snapshot (
    tenant_id,
    ledger_id,
    fiscal_period_id
)
WHERE status = 'ACTIVE';
```

So only one historical snapshot can represent the current truth for a period.

---

# 7. Correct journals by reversal

The correction process must never edit posted financial history.

Example:

```text
Original:
SYIRKAH_DISTRIBUTION
100,000,000

Correction:
actual distribution
90,000,000
```

Accounting becomes:

```text
Original journal
100m
   ↓
Reversal journal
-100m
   ↓
Corrected journal
90m
```

The original journal remains permanently traceable.

Use the existing journal reversal mechanism and source metadata.

Correction source types can be:

```java
public final class SyirkahAccountingSourceTypes {

    public static final String CORRECTION_REVERSAL =
            "SYIRKAH_CORRECTION_REVERSAL";

    public static final String CORRECTION_ADJUSTMENT =
            "SYIRKAH_CORRECTION_ADJUSTMENT";

    private SyirkahAccountingSourceTypes() {
    }
}
```

And every correction journal carries:

```text
tenantId
ledgerId
sourceType
sourceId
correctionId
reversalOfEntryId
```

---

# 8. Correction transaction boundary

We should now introduce one higher-level transaction:

```java
public interface SyirkahPeriodCorrectionTransaction {

    Uni<SyirkahPeriodCorrectionResult> execute(
            String correctionId,
            String operator);
}
```

Its lifecycle:

```text
REQUESTED
   │
   ▼
APPROVED
   │
   ▼
REOPENED
   │
   ▼
CORRECTING
   │
   ├── reverse affected journals
   ├── correct source business facts
   ├── recalculate financial result
   ├── recalculate allocations
   ├── recreate obligations
   ├── post corrected accounting
   ├── reconcile
   │
   ▼
READY_TO_RECLOSE
```

Then the normal final-close transaction runs again:

```text
READY_TO_RECLOSE
      │
      ▼
new SyirkahCloseSnapshot
      │
      ▼
new snapshot hash
      │
      ▼
period LOCKED
      │
      ▼
RECLOSED
```

---

# 9. Do not automatically recalculate everything

A correction needs an explicit affected-boundary.

```java
public record SyirkahCorrectionScope(
        boolean financialResultAffected,
        boolean partnerAllocationAffected,
        boolean distributionAffected,
        boolean settlementAffected,
        boolean contractPositionAffected,
        List<String> affectedSourceIds
) {}
```

For example:

```text
Wrong expense
    ↓
financial result affected
    ↓
partner allocation affected
    ↓
distribution affected
```

But:

```text
Wrong report label
    ↓
no accounting correction
```

This prevents unnecessarily reopening/reprocessing unrelated artifacts.

---

# 10. Recalculation must start from immutable inputs

For a correction, don't use the already-derived snapshot as the new source of truth.

Use:

```text
original business facts
+
corrective business facts
+
reversal journals
+
new journal entries
```

then calculate:

```text
new result
   ↓
new allocation
   ↓
new distribution
   ↓
new positions
   ↓
new snapshot
```

The old snapshot is evidence of what was previously closed.

The new snapshot is evidence of the corrected close.

---

# 11. Integrity chain

Now we can make the lineage explicit:

```text
Snapshot V1
hash = H1
   │
   │ superseded by
   ▼
Snapshot V2
hash = H2
   │
   ▼
current historical truth
```

And the correction itself records:

```text
correctionId
originalSnapshotId
newSnapshotId
originalSnapshotHash
newSnapshotHash
reason
requestedBy
approvedBy
reopenedAt
reclosedAt
```

That makes an audit question easy to answer:

> "Why is March's current result different from the March report that was originally issued?"

Answer:

```text
March Snapshot V1
    ↓
Correction CR-2026-0017
    ↓
approved by X
    ↓
journal reversals
    ↓
recalculation
    ↓
March Snapshot V2
```

---

# 12. Final state machine

We now have two independent but connected lifecycles.

### Normal close

```text
OPEN
 ↓
VALIDATING
 ↓
APPROVED
 ↓
LOCKED
```

### Correction

```text
REQUESTED
 ↓
APPROVED
 ↓
REOPENED
 ↓
CORRECTING
 ↓
READY_TO_RECLOSE
 ↓
RECLOSED
```

And the important invariant is:

```text
LOCKED
    ↓
correction approved
    ↓
temporary authorized reopen
    ↓
CORRECTING
    ↓
new snapshot
    ↓
LOCKED again
```

Never:

```text
LOCKED → OPEN
```

through a generic period-management API.

The framework already distinguishes ordinary reopen from locked-period audit unlock, so this Syirkah workflow should preserve that separation. syirkah-framework(1)

---

## 13. The resulting architecture

```text
                    ┌──────────────────────┐
                    │ Syirkah Close V1     │
                    └──────────┬───────────┘
                               │
                         LOCKED PERIOD
                               │
                         correction request
                               │
                               ▼
                    ┌──────────────────────┐
                    │ Correction Approval  │
                    └──────────┬───────────┘
                               │
                        authorized unlock
                               │
                               ▼
                    ┌──────────────────────┐
                    │ Corrective Accounting│
                    │ + Recalculation      │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │ Syirkah Close V2     │
                    └──────────┬───────────┘
                               │
                               ▼
                         LOCKED AGAIN
```

This is the point where `tutup buku` becomes genuinely **audit-grade**: the system can correct historical Syirkah accounting without pretending the original close never happened.

**Next after this:** I would implement the **correction impact engine**—the component that determines exactly which profit result, partner allocations, distributions, settlements, and snapshots must be reversed/recalculated when a particular business fact is corrected. That will prevent a correction from becoming an unsafe "recalculate the whole period" operation.

//////////

Yes. The next layer should be the **Syirkah correction impact engine**.

The key principle is:

> A correction must propagate through the dependency chain, but only through artifacts actually affected by the corrected fact.

So we should not “recalculate the whole period” by default.

### 1. Correction impact model

I would introduce a small domain model around the existing `SyirkahCorrectionScope`.

```java
public enum SyirkahImpactType {

    FINANCIAL_RESULT,

    PARTNER_ALLOCATION,

    LOSS_RESPONSIBILITY,

    DISTRIBUTION_OBLIGATION,

    SETTLEMENT_OBLIGATION,

    PARTNER_POSITION,

    ACCOUNTING_JOURNAL,

    CLOSE_SNAPSHOT,

    REPORTING_READ_MODEL
}
```

Then represent one concrete affected artifact:

```java
public record SyirkahCorrectionImpact(
        SyirkahImpactType type,
        String artifactId,
        String sourceType,
        String sourceId,
        String reason,
        boolean requiresReversal,
        boolean requiresRecalculation
) {
}
```

And the complete impact:

```java
public record SyirkahCorrectionImpactPlan(
        String correctionId,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        String originalSnapshotId,
        List<SyirkahCorrectionImpact> impacts,
        List<String> errors
) {

    public boolean isSafe() {
        return errors.isEmpty();
    }
}
```

### 2. Do not make the correction engine know every business module

The correction engine should orchestrate dependency providers.

```java
public interface SyirkahCorrectionImpactProvider {

    SyirkahImpactType supportedType();

    Uni<List<SyirkahCorrectionImpact>> findImpacts(
            SyirkahCorrectionContext context);
}
```

Context:

```java
public record SyirkahCorrectionContext(
        String correctionId,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        String closeCycleId,
        String originalSnapshotId,
        SyirkahCorrectionScope scope,
        List<String> affectedSourceIds
) {
}
```

Then the application service becomes extensible:

```java
public interface SyirkahCorrectionImpactEngine {

    Uni<SyirkahCorrectionImpactPlan> analyze(
            SyirkahCorrectionContext context);
}
```

Implementation:

```java
public final class DefaultSyirkahCorrectionImpactEngine
        implements SyirkahCorrectionImpactEngine {

    private final List<SyirkahCorrectionImpactProvider> providers;

    public DefaultSyirkahCorrectionImpactEngine(
            List<SyirkahCorrectionImpactProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    @Override
    public Uni<SyirkahCorrectionImpactPlan> analyze(
            SyirkahCorrectionContext context) {

        Uni<List<SyirkahCorrectionImpact>> chain =
                Uni.createFrom().item(List.of());

        for (SyirkahCorrectionImpactProvider provider : providers) {
            chain = chain.chain(existing ->
                    provider.findImpacts(context)
                            .map(found -> {
                                List<SyirkahCorrectionImpact> result =
                                        new ArrayList<>(existing);
                                result.addAll(found);
                                return List.copyOf(result);
                            }));
        }

        return chain.map(impacts ->
                new SyirkahCorrectionImpactPlan(
                        context.correctionId(),
                        context.tenantId(),
                        context.ledgerId(),
                        context.fiscalPeriodId(),
                        context.originalSnapshotId(),
                        impacts,
                        List.of()
                ));
    }
}
```

This fits the existing platform's extension-oriented architecture rather than creating a giant Syirkah service.

The framework already uses extension points such as `FinancialModule` and module registration, so this approach is consistent with the existing architecture. syirkah-framework(1)

---

# 3. The dependency graph

The important part is not the provider interface. It is the dependency graph.

For Syirkah close:

```text
Business Fact
     │
     ▼
Financial Result
     │
     ▼
Partner Allocation
     │
     ├──────────────► Loss Responsibility
     │
     ▼
Distribution Obligation
     │
     ▼
Settlement Obligation
     │
     ▼
Partner Position
     │
     ▼
Close Snapshot
     │
     ▼
Locked Reporting
```

Accounting runs alongside those artifacts:

```text
Financial Result
      │
      ▼
Accounting Journal

Partner Allocation
      │
      ▼
Accounting Journal

Distribution
      │
      ▼
Accounting Journal

Settlement
      │
      ▼
Accounting Journal
```

Therefore a correction to an upstream fact propagates downstream.

For example:

```text
Correct revenue
   ↓
financial result affected
   ↓
profit allocation affected
   ↓
distribution affected
   ↓
settlement potentially affected
   ↓
partner position affected
   ↓
close snapshot affected
   ↓
reports affected
```

But:

```text
Correct partner bank account
   ↓
settlement affected
   ↓
cash settlement reconciliation affected
```

does **not necessarily** mean:

```text
financial result affected
profit allocation affected
```

That distinction is essential.

---

# 4. Dependency classification

I recommend explicit propagation rules rather than generic graph magic.

```java
public enum SyirkahImpactDependency {

    RESULT_TO_ALLOCATION,

    RESULT_TO_RESPONSIBILITY,

    ALLOCATION_TO_DISTRIBUTION,

    ALLOCATION_TO_PARTNER_POSITION,

    RESPONSIBILITY_TO_RECOVERY,

    DISTRIBUTION_TO_SETTLEMENT,

    SETTLEMENT_TO_PARTNER_POSITION,

    ACCOUNTING_TO_ARTIFACT,

    ARTIFACT_TO_SNAPSHOT,

    SNAPSHOT_TO_REPORTING
}
```

Then the engine can produce a deterministic dependency trail:

```java
public record SyirkahCorrectionImpactLink(
        String upstreamType,
        String upstreamId,
        String downstreamType,
        String downstreamId,
        SyirkahImpactDependency dependency
) {
}
```

This becomes extremely useful for audit.

For example:

```text
CORRECTION-001
  └── INVOICE-778
       └── RESULT-CALC-2026-03
            └── ALLOCATION-001
                 └── DISTRIBUTION-001
                      └── SETTLEMENT-001
```

---

# 5. Financial result provider

The first provider should determine whether the corrected fact changes the result.

```java
public final class FinancialResultImpactProvider
        implements SyirkahCorrectionImpactProvider {

    private final SyirkahFinancialSourceRepository sourceRepository;

    @Override
    public SyirkahImpactType supportedType() {
        return SyirkahImpactType.FINANCIAL_RESULT;
    }

    @Override
    public Uni<List<SyirkahCorrectionImpact>> findImpacts(
            SyirkahCorrectionContext context) {

        return sourceRepository.findAffectedResults(
                        context.tenantId(),
                        context.ledgerId(),
                        context.fiscalPeriodId(),
                        context.scope().affectedSourceIds())
                .map(results -> results.stream()
                        .map(result -> new SyirkahCorrectionImpact(
                                SyirkahImpactType.FINANCIAL_RESULT,
                                result.calculationId(),
                                "SYIRKAH_RESULT_CALCULATION",
                                result.calculationId(),
                                "Affected financial source",
                                true,
                                true
                        ))
                        .toList());
    }
}
```

The important part is that this provider works from **source lineage**, not merely dates.

---

# 6. Partner allocation propagation

If the result changes:

```text
old net result != new net result
```

then the allocation must be recalculated.

```java
public final class PartnerAllocationImpactProvider
        implements SyirkahCorrectionImpactProvider {

    private final SyirkahAllocationRepository allocationRepository;

    @Override
    public SyirkahImpactType supportedType() {
        return SyirkahImpactType.PARTNER_ALLOCATION;
    }

    @Override
    public Uni<List<SyirkahCorrectionImpact>> findImpacts(
            SyirkahCorrectionContext context) {

        return allocationRepository
                .findByAffectedCalculations(
                        context.tenantId(),
                        context.ledgerId(),
                        context.affectedSourceIds())
                .map(allocations -> allocations.stream()
                        .map(allocation ->
                                new SyirkahCorrectionImpact(
                                        SyirkahImpactType.PARTNER_ALLOCATION,
                                        allocation.allocationId(),
                                        "SYIRKAH_ALLOCATION",
                                        allocation.allocationId(),
                                        "Allocation depends on corrected result",
                                        true,
                                        true
                                ))
                        .toList());
    }
}
```

This is where the existing profit-sharing and loss-allocation policies remain authoritative.

The correction engine should **never recalculate nisbah itself**.

It asks the existing policy engine to recalculate.

---

# 7. Distribution propagation

Distribution is downstream from allocation.

```java
public final class DistributionImpactProvider
        implements SyirkahCorrectionImpactProvider {

    private final SyirkahDistributionRepository repository;

    @Override
    public SyirkahImpactType supportedType() {
        return SyirkahImpactType.DISTRIBUTION_OBLIGATION;
    }

    @Override
    public Uni<List<SyirkahCorrectionImpact>> findImpacts(
            SyirkahCorrectionContext context) {

        return repository.findByAffectedAllocations(
                        context.tenantId(),
                        context.ledgerId(),
                        context.affectedSourceIds())
                .map(obligations -> obligations.stream()
                        .map(obligation ->
                                new SyirkahCorrectionImpact(
                                        SyirkahImpactType.DISTRIBUTION_OBLIGATION,
                                        obligation.obligationId(),
                                        "SYIRKAH_DISTRIBUTION",
                                        obligation.obligationId(),
                                        "Distribution depends on corrected allocation",
                                        true,
                                        true
                                ))
                        .toList());
    }
}
```

---

# 8. Settlement propagation must be conditional

This is important.

A changed distribution does not always mean the settlement amount changes.

Example:

```text
Old obligation = 10,000
Old settlement = 10,000

Correction:
New obligation = 8,000
```

Now:

```text
existing settlement = 10,000
new obligation      = 8,000
```

This is not simply a new settlement.

It requires a **settlement correction/recovery decision**.

So introduce:

```java
public enum SyirkahSettlementImpact {

    NO_IMPACT,

    OUTSTANDING_AMOUNT_CHANGED,

    OVERSETTLED,

    UNDERSETTLED,

    SETTLED_AMOUNT_INVALID,

    PAYMENT_REFERENCE_INVALID
}
```

And let the settlement policy determine what happens.

```java
public interface SyirkahSettlementCorrectionPolicy {

    SyirkahSettlementCorrectionDecision evaluate(
            SyirkahSettlementObligation existing,
            SyirkahSettlementObligation recalculated);
}
```

Do not hardcode “refund” or “recover” into the correction engine.

---

# 9. Accounting journals are consequences, not the source

This is one of the most important safeguards.

Suppose:

```text
Distribution D-100
    ↓
Journal JE-500
```

and D-100 is corrected.

The correction engine should **not edit JE-500**.

It should generate:

```text
JE-500 original
    ↓
reversal
    ↓
new corrected distribution
    ↓
new corrected journal
```

The framework already has `JournalEntryReversalService` and `JournalEntry.createStornoReversal(...)`; the latter creates inverse lines and preserves the original tenant/ledger context. syirkah-framework(1)

However, the existing reversal service currently performs balance mutation and persistence directly, so for financial corrections I would put it behind the stronger transaction/idempotency mechanism we already identified rather than calling it as an unguarded standalone operation.

---

# 10. Correction journal identity

Each correction operation needs its own stable source identity.

For example:

```text
SYIRKAH_CORRECTION_REVERSAL
sourceId = CORRECTION-001:JE-500
```

and:

```text
SYIRKAH_CORRECTION_ADJUSTMENT
sourceId = CORRECTION-001:DISTRIBUTION-100
```

Then retries cannot create duplicate journals.

The existing accounting command API already supports `sourceType`, `sourceId`, and `postedBy`, so the correction layer can use that mechanism rather than inventing another accounting integration API. syirkah-framework(1)

---

# 11. Snapshot impact

A correction to a locked period must never mutate the old snapshot.

Instead:

```text
Snapshot V1
    ACTIVE
       │
       │ correction
       ▼
Snapshot V1
  SUPERSEDED

Snapshot V2
    ACTIVE
```

The impact engine therefore creates:

```java
new SyirkahCorrectionImpact(
        SyirkahImpactType.CLOSE_SNAPSHOT,
        originalSnapshotId,
        "SYIRKAH_CLOSE_SNAPSHOT",
        originalSnapshotId,
        "Locked snapshot contains corrected downstream artifacts",
        false,
        true
);
```

The `false` for `requiresReversal` is intentional.

A snapshot is not a journal.

It is superseded, not reversed.

---

# 12. Plan before execution

The next important abstraction is a **correction plan**.

```java
public record SyirkahCorrectionPlan(
        String correctionId,
        SyirkahCorrectionImpactPlan impactPlan,
        List<SyirkahCorrectionAction> actions,
        List<String> errors
) {

    public boolean executable() {
        return errors.isEmpty()
                && actions.stream()
                    .allMatch(SyirkahCorrectionAction::isExecutable);
    }
}
```

Actions:

```java
public enum SyirkahCorrectionActionType {

    REVERSE_JOURNAL,

    RECALCULATE_RESULT,

    RECALCULATE_ALLOCATION,

    RECALCULATE_RESPONSIBILITY,

    REBUILD_DISTRIBUTION,

    RECALCULATE_SETTLEMENT,

    REBUILD_PARTNER_POSITION,

    SUPERSEDE_SNAPSHOT,

    REBUILD_REPORTING
}
```

```java
public record SyirkahCorrectionAction(
        String actionId,
        SyirkahCorrectionActionType type,
        String targetId,
        List<String> dependsOn,
        boolean reversible
) {

    public boolean isExecutable() {
        return targetId != null
                && !targetId.isBlank();
    }
}
```

Now the correction is no longer:

```text
"please recalculate March"
```

It becomes:

```text
CORRECTION-001

1. Reverse JE-500
2. Recalculate result CALC-100
3. Recalculate allocations ALLOC-100..103
4. Reverse affected distributions
5. Recalculate distributions
6. Recalculate settlement obligations
7. Rebuild partner positions
8. Supersede snapshot V1
9. Create snapshot V2
10. Reconcile
11. Reclose
```

---

# 13. Action ordering

Actions must be topologically ordered.

```text
Accounting reversal
       ↓
Financial result
       ↓
Partner allocation
       ↓
Distribution
       ↓
Settlement
       ↓
Partner position
       ↓
Snapshot
       ↓
Reporting
```

Never allow:

```text
snapshot
   ↓
allocation
```

or:

```text
settlement
   ↓
distribution
```

The planner should reject cyclic dependencies.

---

# 14. Correction execution state

I would add:

```java
public enum SyirkahCorrectionExecutionStatus {

    PLANNED,

    EXECUTING,

    BLOCKED,

    FAILED,

    READY_TO_RECLOSE,

    COMPLETED
}
```

And each action:

```java
public enum SyirkahCorrectionActionStatus {

    PENDING,

    IN_PROGRESS,

    COMPLETED,

    FAILED,

    SKIPPED
}
```

This gives us resumability.

If execution fails after:

```text
1. JE reversal       COMPLETED
2. result            COMPLETED
3. allocation        COMPLETED
4. distribution      FAILED
```

we do not restart from zero.

---

# 15. Critical idempotency rule

Every action needs a stable idempotency key:

```text
correctionId + actionType + targetId
```

For example:

```text
CORR-001|REVERSE_JOURNAL|JE-500
CORR-001|RECALCULATE_RESULT|CALC-100
CORR-001|REBUILD_DISTRIBUTION|DIST-100
```

Database:

```sql
CREATE UNIQUE INDEX ux_syirkah_correction_action
ON syirkah_correction_action (
    tenant_id,
    ledger_id,
    correction_id,
    action_type,
    target_id
);
```

This is particularly important because the existing accounting reversal implementation is not sufficient by itself as the financial idempotency boundary.

---

# 16. Reconciliation after correction

The correction is not finished when recalculation succeeds.

We need:

```java
public record SyirkahCorrectionReconciliation(
        boolean financialResultBalanced,
        boolean allocationBalanced,
        boolean distributionBalanced,
        boolean settlementBalanced,
        boolean partnerPositionBalanced,
        boolean accountingBalanced,
        boolean snapshotBalanced,
        List<String> errors
) {

    public boolean balanced() {
        return financialResultBalanced
                && allocationBalanced
                && distributionBalanced
                && settlementBalanced
                && partnerPositionBalanced
                && accountingBalanced
                && snapshotBalanced
                && errors.isEmpty();
    }
}
```

The final gate becomes:

```text
correction execution complete
        +
all reconciliations balanced
        +
new snapshot frozen
        +
no pending accounting movements
        +
no unresolved settlement discrepancy
        ↓
READY_TO_RECLOSE
```

Only then:

```text
READY_TO_RECLOSE
       ↓
final close transaction
       ↓
new snapshot ACTIVE
       ↓
fiscal period LOCKED
       ↓
RECLOSED
```

---

# 17. One important distinction: correction vs reopening

The impact engine should **not** itself reopen the fiscal period.

Keep the boundaries:

```text
Correction Request
        │
        ▼
Impact Analysis
        │
        ▼
Correction Plan
        │
        ▼
Approval
        │
        ▼
Authorized Reopen
        │
        ▼
Correction Execution
        │
        ▼
Reconciliation
        │
        ▼
New Snapshot
        │
        ▼
Reclose
```

This preserves the maker/checker and audit requirements already established.

---

## The resulting Syirkah correction architecture

```text
                         ┌─────────────────────┐
                         │ Correction Request  │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │ Impact Engine       │
                         └──────────┬──────────┘
                                    │
                    ┌───────────────┼────────────────┐
                    ▼               ▼                ▼
               Result          Allocation       Responsibility
                    │               │                │
                    └───────┬───────┴────────┬───────┘
                            ▼                ▼
                     Distribution       Recovery
                            │                │
                            ▼                │
                       Settlement ◄─────────┘
                            │
                            ▼
                     Partner Position
                            │
                            ▼
                      Close Snapshot
                            │
                            ▼
                         Reporting
```

And separately:

```text
Business artifact
       │
       ▼
Accounting source
       │
       ▼
Journal
       │
       ▼
Correction reversal
       │
       ▼
Corrected journal
```

### The next implementation unit

I would now implement these **in this order**:

1. `SyirkahCorrectionImpact`
2. `SyirkahCorrectionImpactProvider`
3. `SyirkahCorrectionImpactEngine`
4. `SyirkahCorrectionAction`
5. `SyirkahCorrectionPlan`
6. action persistence/idempotency
7. financial-result impact provider
8. allocation impact provider
9. distribution impact provider
10. settlement impact provider
11. partner-position impact provider
12. snapshot impact provider
13. topological action planner
14. correction executor
15. correction reconciliation
16. integration with the existing `FiscalPeriodCorrectionTransaction`
17. tests for partial failure/retry and concurrent correction requests

That gives Syirkah a controlled **fact → dependency → reversal/recalculation → reconciliation → new snapshot → reclose** pipeline instead of turning `tutup buku` correction into a dangerous global recalculation.


///////////

The next step is to turn the impact model into an **executable correction planner and action ledger**. This is where the design becomes operational rather than just descriptive.

## 18. Persist the correction plan

The correction plan should be immutable once approved.

```java
public final class SyirkahCorrectionPlan {

    private final String planId;
    private final String correctionId;
    private final String tenantId;
    private final String ledgerId;
    private final String fiscalPeriodId;
    private final String originalSnapshotId;
    private final List<SyirkahCorrectionAction> actions;
    private final SyirkahCorrectionReconciliation expectedReconciliation;
    private final Instant createdAt;
    private final String createdBy;

    // factory + read-only accessors
}
```

The important distinction is:

```text
SyirkahPeriodCorrection
    = authorization/lifecycle

SyirkahCorrectionImpactPlan
    = analysis

SyirkahCorrectionPlan
    = executable plan

SyirkahCorrectionAction
    = individual execution unit
```

Do not merge those four concepts.

---

# 19. Add an action ledger

Every action needs persistent execution state.

```java
public final class SyirkahCorrectionActionRecord {

    private final String actionId;
    private final String correctionId;
    private final String planId;

    private final String tenantId;
    private final String ledgerId;

    private final SyirkahCorrectionActionType type;
    private final String targetId;

    private SyirkahCorrectionActionStatus status;

    private final List<String> dependsOn;

    private String resultReference;
    private String failureCode;
    private String failureMessage;

    private Instant startedAt;
    private Instant completedAt;

    private int attemptCount;
}
```

Repository:

```java
public interface SyirkahCorrectionActionRepository {

    Uni<Optional<SyirkahCorrectionActionRecord>> findById(
            String tenantId,
            String ledgerId,
            String actionId);

    Uni<Optional<SyirkahCorrectionActionRecord>> findByIdempotencyKey(
            String tenantId,
            String ledgerId,
            String correctionId,
            SyirkahCorrectionActionType type,
            String targetId);

    Uni<List<SyirkahCorrectionActionRecord>> findByPlan(
            String tenantId,
            String ledgerId,
            String planId);

    Uni<Void> save(SyirkahCorrectionActionRecord action);
}
```

Database uniqueness:

```sql
CREATE UNIQUE INDEX ux_syirkah_correction_action_key
ON syirkah_correction_action (
    tenant_id,
    ledger_id,
    correction_id,
    action_type,
    target_id
);
```

This is the actual retry boundary.

---

# 20. Make action dependencies explicit

The planner should not depend on list ordering.

For example:

```java
new SyirkahCorrectionAction(
    "ACTION-ALLOCATION-001",
    SyirkahCorrectionActionType.RECALCULATE_ALLOCATION,
    "ALLOC-001",
    List.of("ACTION-RESULT-001"),
    true
);
```

Then:

```text
ACTION-RESULT-001
        │
        ▼
ACTION-ALLOCATION-001
        │
        ▼
ACTION-DISTRIBUTION-001
        │
        ▼
ACTION-SETTLEMENT-001
```

This also allows independent actions to execute concurrently.

For example:

```text
                 ┌── Allocation A
Result ──────────┼── Allocation B
                 └── Allocation C
```

provided they do not mutate the same accounting source concurrently.

---

# 21. Topological planner

Create:

```java
public interface SyirkahCorrectionActionPlanner {

    Uni<List<SyirkahCorrectionAction>> plan(
            SyirkahCorrectionImpactPlan impactPlan);
}
```

The planner should:

1. create one action per required impact;
2. resolve dependencies;
3. remove duplicate actions;
4. detect cycles;
5. verify every dependency exists;
6. produce deterministic ordering.

A cycle must be a hard failure:

```text
A → B → C → A
```

must never be executable.

---

# 22. Separate reversal from recalculation

This is a subtle but important improvement.

An affected artifact can require:

```text
REVERSE
RECALCULATE
POST
```

rather than one giant action.

For example, distribution correction:

```text
DIST-001
   │
   ├── reverse accounting journal
   │
   ├── invalidate old obligation
   │
   ├── calculate new obligation
   │
   └── post corrected obligation
```

So I would expand the action types:

```java
public enum SyirkahCorrectionActionType {

    REVERSE_JOURNAL,

    RECALCULATE_RESULT,

    RECALCULATE_ALLOCATION,

    RECALCULATE_RESPONSIBILITY,

    CANCEL_DISTRIBUTION,

    CREATE_CORRECTED_DISTRIBUTION,

    RECALCULATE_SETTLEMENT,

    CORRECT_PARTNER_POSITION,

    SUPERSEDE_SNAPSHOT,

    CREATE_CORRECTED_SNAPSHOT,

    REBUILD_REPORTING
}
```

This gives much better auditability.

---

# 23. Never delete historical artifacts

For example, do **not** do this:

```text
Distribution D1
DELETE
Distribution D2
```

Instead:

```text
D1
│
└── supersededBy → D2
```

Likewise:

```text
Allocation V1
    SUPERSEDED
        ↓
Allocation V2
    ACTIVE
```

And:

```text
Snapshot V1
    SUPERSEDED
        ↓
Snapshot V2
    ACTIVE
```

This gives us a complete reconstruction history.

---

# 24. Add artifact lineage

I would introduce one generic lineage record:

```java
public record SyirkahCorrectionLineage(
        String lineageId,
        String tenantId,
        String ledgerId,
        String correctionId,

        String artifactType,
        String artifactId,

        String previousArtifactType,
        String previousArtifactId,

        String relationship,

        Instant createdAt,
        String createdBy
) {
}
```

Examples:

```text
CORRECTION-001
    Distribution D2
        supersedes
    Distribution D1
```

```text
CORRECTION-001
    Snapshot V2
        supersedes
    Snapshot V1
```

```text
CORRECTION-001
    Journal JE-501
        corrects
    Journal JE-500
```

This becomes extremely valuable for audit reconstruction.

---

# 25. Result recalculation must use the original business inputs

When recalculating the result, do not calculate from the old result.

Bad:

```text
oldResult - oldRevenue + newRevenue
```

Prefer:

```text
corrected source facts
        ↓
financial result provider
        ↓
new result calculation
```

So:

```java
public interface SyirkahFinancialResultRecalculationService {

    Uni<SyirkahResultCalculation> recalculate(
            SyirkahCorrectionContext context,
            String originalCalculationId);
}
```

The existing financial-result calculation remains the domain authority.

---

# 26. Allocation recalculation

Likewise:

```java
public interface SyirkahAllocationRecalculationService {

    Uni<SyirkahAllocationResult> recalculate(
            String calculationId,
            String contractId,
            int contractVersion,
            String correctionId);
}
```

The service should:

1. resolve the contract version;
2. resolve the frozen/effective policy;
3. calculate profit/loss;
4. apply the existing allocation policy;
5. compare against the original allocation;
6. produce the corrected allocation;
7. record lineage.

It must not silently use today's contract or today's policy.

That is especially important for historical Syirkah periods.

---

# 27. Policy snapshot is mandatory

For a correction of a historical period:

```text
Current policy
       X
```

must not automatically replace:

```text
Original policy snapshot
       ✓
```

The correction engine should retrieve:

```java
SyirkahPolicySnapshot originalPolicySnapshot;
```

from the original calculation/snapshot.

If the correction explicitly changes the policy basis, that should be a separate authorized business event, not an accidental side effect of recalculation.

---

# 28. Compare old and new allocations

After recalculation:

```java
public record SyirkahAllocationDelta(
        String partyId,
        BigDecimal originalAmount,
        BigDecimal correctedAmount,
        BigDecimal delta
) {
}
```

Then:

```text
Partner A   60,000 → 54,000   -6,000
Partner B   40,000 → 36,000   -4,000
```

This delta becomes the basis for downstream distribution correction.

It also provides a very useful approval view:

```text
Original profit allocation: 100,000
Corrected profit allocation: 90,000
Difference: -10,000
```

---

# 29. Distribution correction should operate on deltas

Suppose:

```text
Original distribution = 100,000
Corrected distribution = 90,000
Already settled = 100,000
```

The system must not simply create a new 90,000 settlement.

It should produce:

```text
Original obligation
100,000
     │
     └── corrected obligation
         90,000

Existing settlement
100,000

Difference
10,000 over-settlement
```

Then the settlement policy decides whether that becomes:

```text
refund receivable
future offset
recovery obligation
waiver
manual review
```

The correction engine identifies the discrepancy; **policy decides its treatment**.

---

# 30. Partner position correction

Partner position is a projection of immutable movements, so this should not directly mutate historical movements.

Instead:

```text
Original movements
       ↓
Corrected business artifacts
       ↓
Corrected movement set
       ↓
Partner position rebuild
```

If an original movement was generated incorrectly, create a compensating/corrective movement.

For example:

```text
Original:
PROFIT_ALLOCATION +10,000

Correction:
PROFIT_ALLOCATION -1,000
```

rather than editing the original movement.

The movement ledger remains append-only.

---

# 31. Accounting reconciliation

The correction engine must compare business deltas with accounting deltas.

For example:

```text
Business distribution delta       -10,000
Accounting distribution delta     -10,000
-------------------------------------------
Difference                           0
```

If:

```text
Business delta       -10,000
Accounting delta      -8,000
```

then:

```text
accountingBalanced = false
```

and correction cannot proceed to `READY_TO_RECLOSE`.

---

# 32. Snapshot rebuilding

The new snapshot should be built from the corrected state, not copied and edited from the old snapshot.

```text
Old snapshot
     │
     ├── historical evidence
     │
     └── superseded

Corrected calculation
Corrected allocation
Corrected movements
Corrected responsibilities
Corrected settlements
     │
     ▼
New snapshot
```

The new snapshot gets:

```java
supersedesSnapshotId = originalSnapshotId;
snapshotVersion = originalVersion + 1;
status = ACTIVE;
```

And the old snapshot becomes:

```java
status = SUPERSEDED;
```

No physical deletion.

---

# 33. Correction executor

Now the execution boundary:

```java
public interface SyirkahCorrectionExecutor {

    Uni<SyirkahCorrectionExecutionResult> execute(
            String correctionId,
            String planId,
            String operator);
}
```

Execution algorithm:

```text
load correction
    ↓
verify APPROVED + REOPENED
    ↓
load plan
    ↓
lock correction
    ↓
for each executable action:
    verify dependencies
    execute
    persist action result
    commit
    ↓
reconciliation
    ↓
READY_TO_RECLOSE
```

I would **not** put the entire correction into one enormous database transaction.

The reason is that the process may involve multiple independent actions, journal posting, external settlement systems, and retries.

Instead:

```text
action-level transaction
+
persistent action ledger
+
final reconciliation transaction
```

is safer.

---

# 34. But final snapshot + reclose remains atomic

There is one exception.

The final transition:

```text
corrected snapshot ACTIVE
+
fiscal period LOCKED
+
correction RECLOSED
```

must remain one transaction.

So:

```text
Correction actions
       ↓
all completed
       ↓
all reconciled
       ↓
FINAL CLOSE TRANSACTION
       ├── supersede old snapshot
       ├── persist new snapshot
       ├── lock fiscal period
       └── mark correction RECLOSED
```

This preserves the invariant:

```text
LOCKED => ACTIVE CLOSE SNAPSHOT EXISTS
```

---

# 35. Recovery after executor crash

Suppose the process dies here:

```text
Action 1 ✓
Action 2 ✓
Action 3 IN_PROGRESS
```

On restart:

```java
findByIdempotencyKey(...)
```

determines whether Action 3 already produced its financial effect.

Then:

```text
already completed → mark COMPLETED
not completed      → retry
ambiguous          → reconcile before retry
```

For accounting, the source uniqueness constraint is the final protection.

This is why the earlier `sourceType/sourceId` hardening is not optional.

---

# 36. Final correction state machine

The complete state becomes:

```text
REQUESTED
    │
    ▼
APPROVED
    │
    ▼
REOPENED
    │
    ▼
CORRECTING
    │
    ├──────────────► FAILED
    │                  │
    │                  └── resume/retry
    │
    ▼
READY_TO_RECLOSE
    │
    ▼
RECLOSED
```

And cancellation:

```text
REQUESTED ──► CANCELLED
APPROVED  ──► CANCELLED
```

but:

```text
REOPENED
CORRECTING
```

should require stronger cancellation rules because financial artifacts may already have been reversed or recreated.

---

# 37. What the finished correction engine gives us

The resulting behavior is:

```text
Correct invoice
      │
      ▼
Impact analysis
      │
      ├── financial result
      ├── partner allocation
      ├── distribution
      ├── settlement
      ├── partner position
      ├── accounting
      └── snapshot
              │
              ▼
        Correction plan
              │
              ▼
        Maker/checker
              │
              ▼
       Authorized reopen
              │
              ▼
      Execute dependency graph
              │
              ▼
         Reconcile
              │
              ▼
       New snapshot V2
              │
              ▼
           Reclose
              │
              ▼
         Period LOCKED
```

The historical chain remains:

```text
Snapshot V1
   ↓ superseded by
Snapshot V2

Journal V1
   ↓ reversed by
Journal reversal
   ↓ corrected by
Journal V2

Allocation V1
   ↓ superseded by
Allocation V2

Distribution V1
   ↓ superseded/corrected by
Distribution V2
```

Nothing important disappears.

That is the right foundation for **audit-grade Syirkah `tutup buku` correction**.

### Next

The next layer should be the **actual Java package/module placement and concrete interfaces/adapters** for this engine, followed by the database schema and tests. That will connect this design to the existing Maven structure instead of leaving it as architecture-only.

/////////////

Great. Let’s move from the architecture into the **concrete implementation layer**.

I would make the correction engine a first-class extension of the existing Syirkah accounting capability, not a replacement for your existing accounting implementation.

## 1. Maven module placement

I recommend adding these modules under the existing accounting/Syirkah area:

```text
finance/
└── accounting/
    ├── syirkah-accounting-domain/
    │   └── src/main/java/
    │       └── tech/kayys/syirkah/accounting/domain/
    │           ├── close/
    │           ├── correction/
    │           ├── snapshot/
    │           └── syirkah/
    │
    ├── syirkah-accounting-application/
    │   └── src/main/java/
    │       └── tech/kayys/syirkah/accounting/application/
    │           ├── correction/
    │           ├── close/
    │           └── service/
    │
    ├── syirkah-accounting-spi/
    │   └── src/main/java/
    │       └── tech/kayys/syirkah/accounting/spi/
    │           ├── correction/
    │           ├── accounting/
    │           └── persistence/
    │
    └── syirkah-accounting-adapter/
        └── src/main/java/
            └── tech/kayys/syirkah/accounting/adapter/
                ├── persistence/
                ├── accounting/
                └── rest/
```

The important dependency direction is:

```text
domain
   ↑
application
   ↑
spi
   ↑
adapter
```

The correction domain must **not** depend on PostgreSQL, Quarkus, REST, Hibernate, or a particular accounting adapter.

---

# 2. Correction domain

I would first formalize the aggregate.

```java
package tech.kayys.syirkah.accounting.domain.correction;

public enum SyirkahPeriodCorrectionStatus {

    REQUESTED,
    APPROVED,
    REOPENED,
    CORRECTING,
    READY_TO_RECLOSE,
    RECLOSED,
    CANCELLED
}
```

```java
package tech.kayys.syirkah.accounting.domain.correction;

public enum SyirkahCorrectionActionStatus {

    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SKIPPED
}
```

```java
package tech.kayys.syirkah.accounting.domain.correction;

public enum SyirkahCorrectionExecutionStatus {

    PLANNED,
    EXECUTING,
    BLOCKED,
    FAILED,
    READY_TO_RECLOSE,
    COMPLETED
}
```

Action types:

```java
package tech.kayys.syirkah.accounting.domain.correction;

public enum SyirkahCorrectionActionType {

    REVERSE_JOURNAL,

    RECALCULATE_RESULT,

    RECALCULATE_ALLOCATION,

    RECALCULATE_RESPONSIBILITY,

    CANCEL_DISTRIBUTION,

    CREATE_CORRECTED_DISTRIBUTION,

    RECALCULATE_SETTLEMENT,

    CORRECT_PARTNER_POSITION,

    SUPERSEDE_SNAPSHOT,

    CREATE_CORRECTED_SNAPSHOT,

    REBUILD_REPORTING
}
```

---

# 3. Correction aggregate

```java
package tech.kayys.syirkah.accounting.domain.correction;

import java.time.Instant;
import java.util.Objects;

public final class SyirkahPeriodCorrection {

    private final SyirkahPeriodCorrectionId correctionId;
    private final String tenantId;
    private final String ledgerId;
    private final String fiscalPeriodId;
    private final String closeCycleId;
    private final String originalSnapshotId;
    private final String reason;

    private final String requestedBy;
    private final Instant requestedAt;

    private SyirkahPeriodCorrectionStatus status;

    private String approvedBy;
    private Instant approvedAt;

    private String newSnapshotId;
    private Instant completedAt;

    private SyirkahPeriodCorrection(
            SyirkahPeriodCorrectionId correctionId,
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String closeCycleId,
            String originalSnapshotId,
            String reason,
            String requestedBy,
            Instant requestedAt
    ) {
        this.correctionId = Objects.requireNonNull(correctionId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.ledgerId = Objects.requireNonNull(ledgerId);
        this.fiscalPeriodId = Objects.requireNonNull(fiscalPeriodId);
        this.closeCycleId = Objects.requireNonNull(closeCycleId);
        this.originalSnapshotId = Objects.requireNonNull(originalSnapshotId);
        this.reason = Objects.requireNonNull(reason);
        this.requestedBy = Objects.requireNonNull(requestedBy);
        this.requestedAt = Objects.requireNonNull(requestedAt);

        this.status = SyirkahPeriodCorrectionStatus.REQUESTED;
    }

    public static SyirkahPeriodCorrection request(
            SyirkahPeriodCorrectionId correctionId,
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String closeCycleId,
            String originalSnapshotId,
            String reason,
            String requestedBy,
            Instant requestedAt
    ) {
        return new SyirkahPeriodCorrection(
                correctionId,
                tenantId,
                ledgerId,
                fiscalPeriodId,
                closeCycleId,
                originalSnapshotId,
                reason,
                requestedBy,
                requestedAt
        );
    }

    public void approve(String approver, Instant approvedAt) {
        requireStatus(SyirkahPeriodCorrectionStatus.REQUESTED);

        if (Objects.equals(requestedBy, approver)) {
            throw new IllegalStateException(
                    "Correction requester cannot approve the same correction"
            );
        }

        this.approvedBy = Objects.requireNonNull(approver);
        this.approvedAt = Objects.requireNonNull(approvedAt);
        this.status = SyirkahPeriodCorrectionStatus.APPROVED;
    }

    public void markReopened() {
        requireStatus(SyirkahPeriodCorrectionStatus.APPROVED);
        this.status = SyirkahPeriodCorrectionStatus.REOPENED;
    }

    public void beginCorrection() {
        requireStatus(SyirkahPeriodCorrectionStatus.REOPENED);
        this.status = SyirkahPeriodCorrectionStatus.CORRECTING;
    }

    public void readyToReclose() {
        requireStatus(SyirkahPeriodCorrectionStatus.CORRECTING);
        this.status = SyirkahPeriodCorrectionStatus.READY_TO_RECLOSE;
    }

    public void markReclosed(
            String newSnapshotId,
            Instant completedAt
    ) {
        requireStatus(SyirkahPeriodCorrectionStatus.READY_TO_RECLOSE);

        this.newSnapshotId = Objects.requireNonNull(newSnapshotId);
        this.completedAt = Objects.requireNonNull(completedAt);
        this.status = SyirkahPeriodCorrectionStatus.RECLOSED;
    }

    public void cancel() {
        if (status == SyirkahPeriodCorrectionStatus.RECLOSED) {
            throw new IllegalStateException(
                    "A reclosed correction cannot be cancelled"
            );
        }

        this.status = SyirkahPeriodCorrectionStatus.CANCELLED;
    }

    private void requireStatus(SyirkahPeriodCorrectionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Expected correction status " + expected +
                    " but was " + status
            );
        }
    }

    public SyirkahPeriodCorrectionId getCorrectionId() {
        return correctionId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getLedgerId() {
        return ledgerId;
    }

    public String getFiscalPeriodId() {
        return fiscalPeriodId;
    }

    public String getCloseCycleId() {
        return closeCycleId;
    }

    public String getOriginalSnapshotId() {
        return originalSnapshotId;
    }

    public SyirkahPeriodCorrectionStatus getStatus() {
        return status;
    }

    public String getNewSnapshotId() {
        return newSnapshotId;
    }
}
```

This keeps the lifecycle rules inside the domain instead of allowing application services to mutate status arbitrarily.

---

# 4. Correction scope

The scope becomes the first major boundary for preventing unnecessary recalculation.

```java
package tech.kayys.syirkah.accounting.domain.correction;

import java.util.List;

public record SyirkahCorrectionScope(
        boolean financialResultAffected,
        boolean partnerAllocationAffected,
        boolean lossResponsibilityAffected,
        boolean distributionAffected,
        boolean settlementAffected,
        boolean contractPositionAffected,
        List<String> affectedSourceIds
) {

    public SyirkahCorrectionScope {
        affectedSourceIds = affectedSourceIds == null
                ? List.of()
                : List.copyOf(affectedSourceIds);
    }

    public boolean affectsFinancialChain() {
        return financialResultAffected
                || partnerAllocationAffected
                || lossResponsibilityAffected
                || distributionAffected
                || settlementAffected
                || contractPositionAffected;
    }
}
```

I intentionally separated `lossResponsibilityAffected` from `partnerAllocationAffected`.

That distinction matters for cases such as:

```text
financial result unchanged
        ↓
loss cause changed
        ↓
loss responsibility changed
        ↓
partner obligation changed
```

The entire result calculation should not necessarily be rerun.

---

# 5. Impact provider SPI

Now the application layer can discover affected artifacts without knowing how every artifact is stored.

```java
package tech.kayys.syirkah.accounting.spi.correction;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.correction.SyirkahCorrectionContext;
import tech.kayys.syirkah.accounting.domain.correction.SyirkahCorrectionImpact;
import tech.kayys.syirkah.accounting.domain.correction.SyirkahImpactType;

import java.util.List;

public interface SyirkahCorrectionImpactProvider {

    SyirkahImpactType supportedType();

    Uni<List<SyirkahCorrectionImpact>> findImpacts(
            SyirkahCorrectionContext context
    );
}
```

Context:

```java
package tech.kayys.syirkah.accounting.domain.correction;

public record SyirkahCorrectionContext(
        String correctionId,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        String closeCycleId,
        String originalSnapshotId,
        SyirkahCorrectionScope scope
) {
}
```

Impact types:

```java
package tech.kayys.syirkah.accounting.domain.correction;

public enum SyirkahImpactType {

    FINANCIAL_RESULT,
    PARTNER_ALLOCATION,
    LOSS_RESPONSIBILITY,
    DISTRIBUTION_OBLIGATION,
    SETTLEMENT_OBLIGATION,
    PARTNER_POSITION,
    ACCOUNTING_JOURNAL,
    CLOSE_SNAPSHOT,
    REPORTING_READ_MODEL
}
```

---

# 6. Impact plan

```java
package tech.kayys.syirkah.accounting.domain.correction;

import java.util.List;

public final class SyirkahCorrectionImpactPlan {

    private final String correctionId;
    private final String tenantId;
    private final String ledgerId;
    private final String fiscalPeriodId;
    private final String originalSnapshotId;

    private final List<SyirkahCorrectionImpact> impacts;
    private final List<String> errors;

    public SyirkahCorrectionImpactPlan(
            String correctionId,
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String originalSnapshotId,
            List<SyirkahCorrectionImpact> impacts,
            List<String> errors
    ) {
        this.correctionId = correctionId;
        this.tenantId = tenantId;
        this.ledgerId = ledgerId;
        this.fiscalPeriodId = fiscalPeriodId;
        this.originalSnapshotId = originalSnapshotId;
        this.impacts = List.copyOf(impacts);
        this.errors = List.copyOf(errors);
    }

    public boolean isSafe() {
        return errors.isEmpty();
    }

    public List<SyirkahCorrectionImpact> impacts() {
        return impacts;
    }

    public List<String> errors() {
        return errors;
    }
}
```

The important architectural decision here is:

> **Planning must not mutate financial state.**

The planner may inspect everything, but it cannot reverse journals, cancel distributions, or alter partner positions.

---

# 7. Dependency graph

We should now make the dependency graph executable rather than merely documenting it.

```java
package tech.kayys.syirkah.accounting.domain.correction;

public enum SyirkahImpactDependency {

    RESULT_TO_ALLOCATION,

    RESULT_TO_RESPONSIBILITY,

    ALLOCATION_TO_DISTRIBUTION,

    ALLOCATION_TO_PARTNER_POSITION,

    RESPONSIBILITY_TO_RECOVERY,

    DISTRIBUTION_TO_SETTLEMENT,

    SETTLEMENT_TO_PARTNER_POSITION,

    ACCOUNTING_TO_ARTIFACT,

    ARTIFACT_TO_SNAPSHOT,

    SNAPSHOT_TO_REPORTING
}
```

And the action model:

```java
package tech.kayys.syirkah.accounting.domain.correction;

import java.util.List;
import java.util.Objects;

public record SyirkahCorrectionAction(
        String actionId,
        SyirkahCorrectionActionType type,
        String targetId,
        List<String> dependsOn,
        boolean reversible
) {

    public SyirkahCorrectionAction {
        Objects.requireNonNull(actionId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(targetId);

        dependsOn = dependsOn == null
                ? List.of()
                : List.copyOf(dependsOn);
    }
}
```

---

# 8. Persistent action ledger

This is particularly important.

Do **not** rely on an in-memory executor to remember what happened.

```java
package tech.kayys.syirkah.accounting.spi.correction;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.correction.*;

import java.util.Optional;

public interface SyirkahCorrectionActionRepository {

    Uni<Optional<SyirkahCorrectionActionRecord>> findById(
            String tenantId,
            String ledgerId,
            String actionId
    );

    Uni<Optional<SyirkahCorrectionActionRecord>> findByIdempotencyKey(
            String tenantId,
            String ledgerId,
            String correctionId,
            SyirkahCorrectionActionType actionType,
            String targetId
    );

    Uni<Void> save(SyirkahCorrectionActionRecord action);

    Uni<Void> markInProgress(
            String actionId,
            String operator
    );

    Uni<Void> markCompleted(
            String actionId,
            String resultReference
    );

    Uni<Void> markFailed(
            String actionId,
            String error
    );
}
```

Record:

```java
package tech.kayys.syirkah.accounting.domain.correction;

import java.time.Instant;

public record SyirkahCorrectionActionRecord(
        String actionId,
        String correctionId,
        String tenantId,
        String ledgerId,
        SyirkahCorrectionActionType actionType,
        String targetId,
        SyirkahCorrectionActionStatus status,
        String resultReference,
        String error,
        Instant startedAt,
        Instant completedAt
) {
}
```

---

# 9. Database structure

The core correction table:

```sql
CREATE TABLE syirkah_period_correction (
    correction_id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    ledger_id VARCHAR(128) NOT NULL,
    fiscal_period_id VARCHAR(128) NOT NULL,
    close_cycle_id VARCHAR(128) NOT NULL,
    original_snapshot_id VARCHAR(128) NOT NULL,

    reason TEXT NOT NULL,

    requested_by VARCHAR(128) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,

    status VARCHAR(32) NOT NULL,

    approved_by VARCHAR(128),
    approved_at TIMESTAMP WITH TIME ZONE,

    new_snapshot_id VARCHAR(128),
    completed_at TIMESTAMP WITH TIME ZONE
);
```

Only one active correction may exist:

```sql
CREATE UNIQUE INDEX ux_syirkah_active_period_correction
ON syirkah_period_correction (
    tenant_id,
    ledger_id,
    fiscal_period_id
)
WHERE status NOT IN ('RECLOSED', 'CANCELLED');
```

This database constraint is important because:

```text
Request A ─────┐
               ├──> same locked period
Request B ─────┘
```

must never produce two simultaneous correction workflows.

---

# 10. Correction plan table

```sql
CREATE TABLE syirkah_correction_plan (
    plan_id UUID PRIMARY KEY,

    correction_id UUID NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    ledger_id VARCHAR(128) NOT NULL,

    execution_status VARCHAR(32) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(128) NOT NULL,

    expected_snapshot_id VARCHAR(128) NOT NULL,

    error_message TEXT,

    CONSTRAINT fk_correction_plan
        FOREIGN KEY (correction_id)
        REFERENCES syirkah_period_correction(correction_id)
);
```

---

# 11. Action table

```sql
CREATE TABLE syirkah_correction_action (
    action_id UUID PRIMARY KEY,

    correction_id UUID NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    ledger_id VARCHAR(128) NOT NULL,

    action_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(256) NOT NULL,

    status VARCHAR(32) NOT NULL,

    result_reference VARCHAR(256),
    error_message TEXT,

    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

Critical idempotency constraint:

```sql
CREATE UNIQUE INDEX ux_syirkah_correction_action_key
ON syirkah_correction_action (
    tenant_id,
    ledger_id,
    correction_id,
    action_type,
    target_id
);
```

This protects against:

```text
worker 1
   │
   ├── execute REVERSE_JOURNAL
   │
worker 2
   │
   └── execute REVERSE_JOURNAL
```

The database prevents both from creating independent actions for the same correction target.

---

# 12. Lineage table

Historical traceability needs its own structure.

```sql
CREATE TABLE syirkah_correction_lineage (
    lineage_id UUID PRIMARY KEY,

    correction_id UUID NOT NULL,

    tenant_id VARCHAR(128) NOT NULL,
    ledger_id VARCHAR(128) NOT NULL,

    artifact_type VARCHAR(64) NOT NULL,
    artifact_id VARCHAR(256) NOT NULL,

    previous_artifact_type VARCHAR(64),
    previous_artifact_id VARCHAR(256),

    relationship VARCHAR(64) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(128) NOT NULL
);
```

Examples:

```text
CORRECTION
    │
    ├── supersedes ──> SNAPSHOT-2026-09
    │
    ├── reverses ────> JOURNAL-123
    │
    ├── creates ─────> JOURNAL-456
    │
    ├── replaces ───> ALLOCATION-22
    │
    └── creates ─────> SNAPSHOT-2026-09-V2
```

This gives us an audit graph instead of simply a collection of status flags.

---

# 13. Journal correction adapter

The correction engine should **not** directly manipulate `Account`.

Instead:

```java
package tech.kayys.syirkah.accounting.spi.accounting;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;

public interface SyirkahJournalCorrectionPort {

    Uni<JournalEntryId> reverse(
            String tenantId,
            String ledgerId,
            JournalEntryId journalEntryId,
            String correctionId,
            String reason,
            String operator
    );

    Uni<JournalEntryId> postAdjustment(
            String tenantId,
            String ledgerId,
            String correctionId,
            String targetArtifactId,
            String description,
            java.util.List<JournalLine> lines,
            String operator
    );
}
```

```java
package tech.kayys.syirkah.accounting.spi.accounting;

import java.math.BigDecimal;
import java.util.UUID;

public record JournalLine(
        UUID accountId,
        String type,
        BigDecimal amount,
        String currencyCode,
        String description
) {
}
```

The implementation can then delegate to the existing accounting machinery.

The existing framework already has `JournalEntryReversalService`, so this is an **adapter around the existing implementation**, not a new reversal system. The current implementation should subsequently be hardened so reversal + balance mutation + journal persistence occur transactionally. syirkah-framework(1)

Likewise, the existing `PostJournalEntryCommand` already has `sourceType` and `sourceId`, which is exactly what we need for correction lineage and idempotency. syirkah-framework(1)

---

# 14. Stable correction journal sources

Every correction-generated accounting entry should have deterministic source identity.

```java
package tech.kayys.syirkah.accounting.domain.correction;

public final class SyirkahCorrectionSourceTypes {

    public static final String REVERSAL =
            "SYIRKAH_CORRECTION_REVERSAL";

    public static final String ADJUSTMENT =
            "SYIRKAH_CORRECTION_ADJUSTMENT";

    private SyirkahCorrectionSourceTypes() {
    }
}
```

Source IDs:

```text
SYIRKAH_CORRECTION_REVERSAL:{correctionId}:{originalJournalId}

SYIRKAH_CORRECTION_ADJUSTMENT:{correctionId}:{artifactId}
```

That gives us deterministic replay behavior.

---

# 15. Result recalculation port

The correction engine should not duplicate the existing calculation algorithm.

```java
package tech.kayys.syirkah.accounting.spi.correction;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.correction.SyirkahCorrectionContext;

public interface SyirkahResultCorrectionPort {

    Uni<SyirkahCorrectedResult> recalculate(
            SyirkahCorrectionContext context,
            String originalResultId
    );
}
```

```java
package tech.kayys.syirkah.accounting.spi.correction;

import java.math.BigDecimal;

public record SyirkahCorrectedResult(
        String resultId,
        String previousResultId,
        BigDecimal amount,
        String currency,
        String policySnapshotId,
        String policySnapshotHash
) {
}
```

Critical rule:

```text
old result
   X
   │
   └── do not "modify"

original business inputs
        +
original frozen policy snapshot
        ↓
new result
```

The historical policy snapshot is therefore an input to correction, not the currently effective policy.

---

# 16. Allocation correction

```java
package tech.kayys.syirkah.accounting.spi.correction;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.correction.SyirkahCorrectionContext;

public interface SyirkahAllocationCorrectionPort {

    Uni<SyirkahCorrectedAllocation> recalculate(
            SyirkahCorrectionContext context,
            String previousAllocationId,
            String correctedResultId
    );
}
```

The result should contain deltas rather than merely replacing amounts:

```java
package tech.kayys.syirkah.accounting.spi.correction;

import java.math.BigDecimal;
import java.util.List;

public record SyirkahCorrectedAllocation(
        String allocationId,
        String previousAllocationId,
        List<SyirkahAllocationDelta> deltas
) {
}
```

```java
package tech.kayys.syirkah.accounting.spi.correction;

import java.math.BigDecimal;

public record SyirkahAllocationDelta(
        String partnerId,
        BigDecimal previousAmount,
        BigDecimal correctedAmount,
        BigDecimal delta
) {
}
```

This becomes especially useful when some partners have already been distributed or settled.

---

# 17. Settlement correction must be policy-driven

I would not make the correction engine decide:

```text
difference > 0 → pay
difference < 0 → recover
```

That is too simplistic.

Instead:

```java
package tech.kayys.syirkah.accounting.spi.correction;

import io.smallrye.mutiny.Uni;

public interface SyirkahSettlementCorrectionPolicy {

    Uni<SyirkahSettlementCorrectionDecision> evaluate(
            SyirkahSettlementCorrectionContext context
    );
}
```

Possible decision:

```java
package tech.kayys.syirkah.accounting.domain.correction;

public enum SyirkahSettlementCorrectionTreatment {

    NO_ACTION,

    OFFSET_NEXT_DISTRIBUTION,

    CREATE_REFUND_OBLIGATION,

    CREATE_RECOVERY_OBLIGATION,

    WAIVE_DIFFERENCE,

    MANUAL_REVIEW
}
```

The engine therefore distinguishes:

```text
calculation changed
        ↓
distribution changed
        ↓
settlement exists?
       / \
     no   yes
          ↓
   already settled?
      /       \
    no         yes
                ↓
       correction policy
```

---

# 18. Partner position correction

Never edit historical partner movements.

Instead:

```java
package tech.kayys.syirkah.accounting.spi.correction;

import io.smallrye.mutiny.Uni;

public interface SyirkahPartnerPositionCorrectionPort {

    Uni<SyirkahPartnerPositionCorrectionResult> correct(
            String tenantId,
            String ledgerId,
            String correctionId,
            String partnerId
    );
}
```

The implementation should create compensating movements:

```text
old movement
    │
    └── remains POSTED

correction
    │
    └── compensating movement
              │
              └── corrected movement
```

This preserves the append-only financial history.

---

# 19. Snapshot correction

Snapshot correction is deliberately last.

```java
package tech.kayys.syirkah.accounting.spi.correction;

import io.smallrye.mutiny.Uni;

public interface SyirkahSnapshotCorrectionPort {

    Uni<Void> supersede(
            String tenantId,
            String ledgerId,
            String originalSnapshotId,
            String correctionId
    );

    Uni<String> createCorrectedSnapshot(
            SyirkahCorrectedSnapshotCommand command
    );
}
```

The corrected snapshot must contain:

```text
previousSnapshotId
correctionId
snapshotVersion
policySnapshotId
policySnapshotHash
snapshotHash
```

The old snapshot remains immutable.

---

# 20. Action executor registry

Now the actual executor can remain generic.

```java
package tech.kayys.syirkah.accounting.application.correction;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.correction.*;

public interface SyirkahCorrectionActionExecutor {

    SyirkahCorrectionActionType supportedType();

    Uni<String> execute(
            SyirkahCorrectionAction action,
            SyirkahCorrectionExecutionContext context
    );
}
```

Then implementations:

```text
ReverseJournalActionExecutor
RecalculateResultActionExecutor
RecalculateAllocationActionExecutor
RecalculateResponsibilityActionExecutor
CancelDistributionActionExecutor
CreateCorrectedDistributionActionExecutor
RecalculateSettlementActionExecutor
CorrectPartnerPositionActionExecutor
SupersedeSnapshotActionExecutor
CreateCorrectedSnapshotActionExecutor
RebuildReportingActionExecutor
```

This gives us an extensible registry:

```text
ActionType
     │
     ├── executor A
     ├── executor B
     ├── executor C
     └── ...
```

Adding a new Syirkah correction behavior doesn't require modifying one enormous service.

---

# 21. Execution algorithm

The executor should behave approximately like this:

```java
public Uni<Void> execute(
        String correctionId,
        String planId,
        String operator
) {
    return loadPlan(planId)
            .chain(plan -> markExecuting(plan))
            .chain(plan -> executeActionsInDependencyOrder(
                    plan,
                    operator
            ))
            .chain(() -> validateCorrectionReconciliation(correctionId))
            .chain(() -> markReadyToReclose(correctionId));
}
```

But the crucial part is:

```java
executeActionsInDependencyOrder(...)
```

must use a topological sort.

For example:

```text
REVERSE_JOURNAL
       ↓
RECALCULATE_RESULT
       ↓
RECALCULATE_ALLOCATION
       ↓
RECALCULATE_RESPONSIBILITY
       ↓
CREATE_CORRECTED_DISTRIBUTION
       ↓
RECALCULATE_SETTLEMENT
       ↓
CORRECT_PARTNER_POSITION
       ↓
CREATE_CORRECTED_SNAPSHOT
       ↓
REBUILD_REPORTING
```

Not every correction executes every node.

For example:

### Settlement-only correction

```text
RECALCULATE_SETTLEMENT
       ↓
CORRECT_PARTNER_POSITION
       ↓
CREATE_CORRECTED_SNAPSHOT
       ↓
REBUILD_REPORTING
```

No financial-result calculation.

---

# 22. Idempotent execution

Every action should first check:

```java
return actionRepository.findByIdempotencyKey(
        tenantId,
        ledgerId,
        correctionId,
        action.type(),
        action.targetId()
)
```

Then:

```text
COMPLETED
    → return previous result

IN_PROGRESS
    → determine whether resumable

FAILED
    → retry according to policy

PENDING
    → execute

SKIPPED
    → continue
```

This is essential because a correction could fail halfway through:

```text
result recalculation       ✓
allocation recalculation  ✓
distribution cancellation ✓
settlement correction     ✗
```

The system must resume from the failed action rather than restarting the entire correction.

---

# 23. The most important transaction boundary

Do **not** make the entire correction one giant database transaction.

Instead:

```text
Action 1 transaction
        ↓
Action 2 transaction
        ↓
Action 3 transaction
        ↓
Action 4 transaction
        ↓
...
        ↓
Final reclose transaction
```

Each action has durable state.

But the final operation is special:

```text
BEGIN TRANSACTION

lock correction
lock fiscal period
lock original snapshot/current corrected state

verify:
    all actions COMPLETED
    reconciliation passes
    no pending settlement inconsistency
    corrected snapshot exists
    corrected snapshot is valid

activate corrected snapshot
supersede original snapshot
lock fiscal period
mark correction RECLOSED

COMMIT
```

That final boundary gives us the invariant:

```text
RECLOSED
    ⇔
corrected snapshot ACTIVE
    AND
fiscal period LOCKED
    AND
all correction reconciliation gates passed
```

---

# 24. Test matrix

I would now make these tests mandatory before calling the feature complete.

### Result propagation

```text
business input corrected
        ↓
result changes
        ↓
allocation changes
        ↓
distribution changes
        ↓
settlement changes
        ↓
partner position changes
        ↓
snapshot changes
```

### Result-independent correction

```text
settlement reference corrected

result unchanged
allocation unchanged
distribution unchanged
settlement corrected
snapshot corrected
```

### Already settled

```text
allocation changed
        ↓
distribution already settled
        ↓
SettlementCorrectionPolicy
        ↓
RECOVERY / REFUND / OFFSET / MANUAL_REVIEW
```

### Idempotency

Execute the same correction twice:

```text
execution #1 → creates reversal

execution #2 → finds completed action

result:
exactly one reversal
```

### Concurrent request

Two users request correction simultaneously:

```text
request A → active correction created
request B → database unique constraint / domain conflict
```

### Crash recovery

```text
action 1 ✓
action 2 ✓
action 3 ✗
process dies
```

Restart:

```text
action 1 → skipped
action 2 → skipped
action 3 → resumed
```

### Historical policy

```text
old close
policy v3
   ↓
correction occurs months later
current policy = v7

recalculation must still use v3
```

### Snapshot immutability

```text
snapshot V1
   ↓
correction
   ↓
snapshot V2

V1 remains byte-for-byte immutable
V2 supersedes V1
```

### Journal integrity

Every correction-generated journal must satisfy:

```text
total debit = total credit
```

and:

```text
original POSTED journal
        +
reversal
        +
corrected journal
```

must reconcile to the corrected business state.

---

## One architectural adjustment I recommend before implementing the adapters

There is one important weakness in the existing accounting implementation that the correction feature exposes.

The framework's current `GeneralLedgerService` mutates account balances and then persists the journal, while the existing reversal service similarly mutates balances and saves the reversal afterward. The source also shows `PostJournalEntryHandler` currently constructing entries with `TenantId.defaultTenant()` and `LedgerId.primary()`. syirkah-framework(1) syirkah-framework(1)

For normal accounting this is already something we should tighten; for **locked-period correction**, it is unacceptable to leave it that way.

So the next implementation step should be:

```text
                 ┌──────────────────────────┐
                 │ JournalPostingTransaction│
                 └────────────┬─────────────┘
                              │
             ┌────────────────┴────────────────┐
             │                                 │
      normal posting                    correction posting
             │                                 │
       PostJournalEntry                 reversal + adjustment
             │                                 │
             └──────────────┬──────────────────┘
                            ↓
                 ONE ACCOUNTING TRANSACTION
                            ↓
              accounts + journal + outbox
```

That gives Syirkah correction a safe foundation without replacing the accounting system you've already built.

The next slice should therefore be **the concrete transactional accounting boundary + `DefaultSyirkahCorrectionImpactEngine` + topological planner + first three action executors (`REVERSE_JOURNAL`, `RECALCULATE_RESULT`, `RECALCULATE_ALLOCATION`)**, followed by the distribution/settlement chain.

///////////////

Yes. Let’s implement the next slice: **transactional accounting boundary + impact planner + first correction executors**.

The goal is to make the correction engine executable without disturbing your existing accounting architecture.

## 1. Transactional accounting boundary

Introduce this in:

```text
finance/accounting/syirkah-accounting-application/
└── src/main/java/
    └── tech/kayys/syirkah/accounting/application/spi/
        └── JournalPostingTransaction.java
```

```java
package tech.kayys.syirkah.accounting.application.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.api.command.PostJournalEntryCommand;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;

public interface JournalPostingTransaction {

    Uni<JournalEntryId> post(
            PostJournalEntryCommand command
    );
}
```

This is deliberately small.

The implementation owns the transaction boundary:

```text
BEGIN
    validate period
    validate journal
    validate source idempotency
    mutate accounts
    persist journal
    persist accounting event
    persist outbox
COMMIT
```

No correction code should directly call:

```java
accountRepository.save(...)
journalEntryRepository.save(...)
```

---

# 2. Transactional reversal boundary

Add:

```java
package tech.kayys.syirkah.accounting.application.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;

public interface JournalReversalTransaction {

    Uni<JournalEntryId> reverse(
            JournalEntryId originalEntryId,
            String tenantId,
            String ledgerId,
            String correctionId,
            String reason,
            String reversedBy
    );
}
```

Notice that `correctionId` is mandatory.

We do not want a reversal that exists financially but cannot later answer:

> "Why was this journal reversed?"

---

# 3. Source idempotency

Extend the journal repository contract:

```java
package tech.kayys.syirkah.accounting.domain.repository;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.entity.JournalEntry;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;

import java.util.Optional;

public interface JournalEntryRepository {

    Uni<Optional<JournalEntry>> findById(
            JournalEntryId id
    );

    Uni<Optional<JournalEntry>> findBySource(
            String tenantId,
            String ledgerId,
            String sourceType,
            String sourceId
    );

    Uni<Void> save(
            JournalEntry journalEntry
    );
}
```

Then the correction reversal implementation follows:

```text
find existing source
       │
       ├── posted → return existing journal
       │
       ├── non-posted → fail safely
       │
       └── absent → create reversal
```

That is stronger than an application-level `IdempotencyGuard`.

---

# 4. Source uniqueness

At database level:

```sql
CREATE UNIQUE INDEX ux_journal_entry_source
ON journal_entry (
    tenant_id,
    ledger_id,
    source_type,
    source_id
)
WHERE source_type IS NOT NULL
  AND source_id IS NOT NULL;
```

This is the final authority for duplicate prevention.

---

# 5. Correction impact engine

Now implement the previously designed provider aggregation.

```java
package tech.kayys.syirkah.accounting.application.correction;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.correction.*;
import tech.kayys.syirkah.accounting.spi.correction.SyirkahCorrectionImpactProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DefaultSyirkahCorrectionImpactEngine
        implements SyirkahCorrectionImpactEngine {

    private final List<SyirkahCorrectionImpactProvider> providers;

    public DefaultSyirkahCorrectionImpactEngine(
            List<SyirkahCorrectionImpactProvider> providers
    ) {
        this.providers = List.copyOf(
                Objects.requireNonNull(providers)
        );
    }

    @Override
    public Uni<SyirkahCorrectionImpactPlan> buildPlan(
            SyirkahCorrectionContext context
    ) {
        List<Uni<List<SyirkahCorrectionImpact>>> operations =
                providers.stream()
                        .map(provider -> provider.findImpacts(context))
                        .toList();

        return Uni.combine()
                .all()
                .unis(operations)
                .combinedWith(results -> {
                    List<SyirkahCorrectionImpact> impacts =
                            new ArrayList<>();

                    for (Object result : results) {
                        @SuppressWarnings("unchecked")
                        List<SyirkahCorrectionImpact> providerImpacts =
                                (List<SyirkahCorrectionImpact>) result;

                        impacts.addAll(providerImpacts);
                    }

                    return new SyirkahCorrectionImpactPlan(
                            context.correctionId(),
                            context.tenantId(),
                            context.ledgerId(),
                            context.fiscalPeriodId(),
                            context.originalSnapshotId(),
                            impacts,
                            List.of()
                    );
                });
    }
}
```

Interface:

```java
package tech.kayys.syirkah.accounting.application.correction;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.correction.*;

public interface SyirkahCorrectionImpactEngine {

    Uni<SyirkahCorrectionImpactPlan> buildPlan(
            SyirkahCorrectionContext context
    );
}
```

---

# 6. But impact discovery must be lineage-based

This is important enough to enforce in the provider contracts.

A result provider should not simply do:

```sql
WHERE period_start = ?
AND period_end = ?
```

because a period may contain multiple calculations, corrections, transitions, or successor contracts.

Instead:

```text
correction
    ↓
original snapshot
    ↓
calculationId
    ↓
result
    ↓
allocation
    ↓
distribution
```

Therefore the snapshot is an important anchor.

For example:

```java
public interface SyirkahFinancialResultImpactProvider
        extends SyirkahCorrectionImpactProvider {

    @Override
    default SyirkahImpactType supportedType() {
        return SyirkahImpactType.FINANCIAL_RESULT;
    }
}
```

And its implementation resolves:

```text
originalSnapshotId
        ↓
calculationId
        ↓
financial result
```

rather than guessing based on dates.

---

# 7. Topological planner

Now transform impacts into executable actions.

```java
package tech.kayys.syirkah.accounting.application.correction;

import tech.kayys.syirkah.accounting.domain.correction.*;

import java.util.*;

public final class SyirkahCorrectionActionPlanner {

    public List<SyirkahCorrectionAction> plan(
            SyirkahCorrectionImpactPlan impactPlan
    ) {
        Map<SyirkahImpactType, List<SyirkahCorrectionImpact>> grouped =
                new EnumMap<>(SyirkahImpactType.class);

        for (SyirkahCorrectionImpact impact : impactPlan.impacts()) {
            grouped
                    .computeIfAbsent(
                            impact.type(),
                            ignored -> new ArrayList<>()
                    )
                    .add(impact);
        }

        List<SyirkahCorrectionAction> actions = new ArrayList<>();

        addJournalActions(grouped, actions);
        addResultActions(grouped, actions);
        addAllocationActions(grouped, actions);
        addResponsibilityActions(grouped, actions);
        addDistributionActions(grouped, actions);
        addSettlementActions(grouped, actions);
        addPositionActions(grouped, actions);
        addSnapshotActions(grouped, actions);
        addReportingActions(grouped, actions);

        return topologicalSort(actions);
    }
}
```

The key point is that **impact discovery and action planning remain separate**.

That allows us to answer:

```text
What is affected?
```

before deciding:

```text
What must be executed?
```

---

# 8. Journal action

For every affected posted journal:

```java
private void addJournalActions(
        Map<SyirkahImpactType, List<SyirkahCorrectionImpact>> grouped,
        List<SyirkahCorrectionAction> actions
) {
    for (SyirkahCorrectionImpact impact :
            grouped.getOrDefault(
                    SyirkahImpactType.ACCOUNTING_JOURNAL,
                    List.of()
            )) {

        if (!impact.requiresReversal()) {
            continue;
        }

        actions.add(
                new SyirkahCorrectionAction(
                        UUID.randomUUID().toString(),
                        SyirkahCorrectionActionType.REVERSE_JOURNAL,
                        impact.artifactId(),
                        List.of(),
                        true
                )
        );
    }
}
```

---

# 9. Result action

```java
private void addResultActions(
        Map<SyirkahImpactType, List<SyirkahCorrectionImpact>> grouped,
        List<SyirkahCorrectionAction> actions
) {
    for (SyirkahCorrectionImpact impact :
            grouped.getOrDefault(
                    SyirkahImpactType.FINANCIAL_RESULT,
                    List.of()
            )) {

        if (!impact.requiresRecalculation()) {
            continue;
        }

        actions.add(
                new SyirkahCorrectionAction(
                        UUID.randomUUID().toString(),
                        SyirkahCorrectionActionType.RECALCULATE_RESULT,
                        impact.artifactId(),
                        List.of(),
                        true
                )
        );
    }
}
```

---

# 10. Allocation dependencies

Allocation must depend on the result.

```java
private void addAllocationActions(
        Map<SyirkahImpactType, List<SyirkahCorrectionImpact>> grouped,
        List<SyirkahCorrectionAction> actions
) {
    List<SyirkahCorrectionAction> resultActions =
            actions.stream()
                    .filter(a ->
                            a.type() ==
                            SyirkahCorrectionActionType.RECALCULATE_RESULT
                    )
                    .toList();

    for (SyirkahCorrectionImpact impact :
            grouped.getOrDefault(
                    SyirkahImpactType.PARTNER_ALLOCATION,
                    List.of()
            )) {

        List<String> dependencies =
                resultActions.stream()
                        .map(SyirkahCorrectionAction::actionId)
                        .toList();

        actions.add(
                new SyirkahCorrectionAction(
                        UUID.randomUUID().toString(),
                        SyirkahCorrectionActionType.RECALCULATE_ALLOCATION,
                        impact.artifactId(),
                        dependencies,
                        true
                )
        );
    }
}
```

This creates:

```text
RESULT
  ↓
ALLOCATION
```

rather than allowing arbitrary execution order.

---

# 11. Responsibility

```text
RESULT
  │
  └──────> LOSS RESPONSIBILITY
                 │
                 └──────> RECOVERY
```

Allocation may also influence responsibility depending on the particular correction.

So the planner should explicitly encode dependencies rather than assume all actions are sequential.

---

# 12. Topological sort

Use Kahn's algorithm:

```java
private List<SyirkahCorrectionAction> topologicalSort(
        List<SyirkahCorrectionAction> actions
) {
    Map<String, SyirkahCorrectionAction> byId =
            new HashMap<>();

    Map<String, Integer> indegree =
            new HashMap<>();

    Map<String, List<String>> outgoing =
            new HashMap<>();

    for (SyirkahCorrectionAction action : actions) {
        byId.put(action.actionId(), action);
        indegree.put(action.actionId(), action.dependsOn().size());

        for (String dependency : action.dependsOn()) {
            outgoing
                    .computeIfAbsent(
                            dependency,
                            ignored -> new ArrayList<>()
                    )
                    .add(action.actionId());
        }
    }

    Deque<String> queue = new ArrayDeque<>();

    for (Map.Entry<String, Integer> entry :
            indegree.entrySet()) {

        if (entry.getValue() == 0) {
            queue.add(entry.getKey());
        }
    }

    List<SyirkahCorrectionAction> ordered =
            new ArrayList<>();

    while (!queue.isEmpty()) {
        String id = queue.removeFirst();

        ordered.add(byId.get(id));

        for (String dependent :
                outgoing.getOrDefault(id, List.of())) {

            int newDegree =
                    indegree.merge(
                            dependent,
                            -1,
                            Integer::sum
                    );

            if (newDegree == 0) {
                queue.addLast(dependent);
            }
        }
    }

    if (ordered.size() != actions.size()) {
        throw new IllegalStateException(
                "Correction action dependency graph contains a cycle"
        );
    }

    return ordered;
}
```

That cycle check is essential.

A bad dependency definition should fail during planning, **before any financial mutation occurs**.

---

# 13. First executor: reverse journal

```java
package tech.kayys.syirkah.accounting.application.correction.executor;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.correction.*;
import tech.kayys.syirkah.accounting.application.spi.JournalReversalTransaction;
import tech.kayys.syirkah.accounting.domain.correction.*;

import java.util.Objects;

public final class ReverseJournalActionExecutor
        implements SyirkahCorrectionActionExecutor {

    private final JournalReversalTransaction reversalTransaction;

    public ReverseJournalActionExecutor(
            JournalReversalTransaction reversalTransaction
    ) {
        this.reversalTransaction =
                Objects.requireNonNull(reversalTransaction);
    }

    @Override
    public SyirkahCorrectionActionType supportedType() {
        return SyirkahCorrectionActionType.REVERSE_JOURNAL;
    }

    @Override
    public Uni<String> execute(
            SyirkahCorrectionAction action,
            SyirkahCorrectionExecutionContext context
    ) {
        return reversalTransaction.reverse(
                context.tenantId(),
                context.ledgerId(),
                context.toJournalEntryId(action.targetId()),
                context.correctionId(),
                context.reason(),
                context.operator()
        ).map(Object::toString);
    }
}
```

---

# 14. Result executor

```java
package tech.kayys.syirkah.accounting.application.correction.executor;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.correction.*;
import tech.kayys.syirkah.accounting.domain.correction.*;
import tech.kayys.syirkah.accounting.spi.correction.*;

public final class RecalculateResultActionExecutor
        implements SyirkahCorrectionActionExecutor {

    private final SyirkahResultCorrectionPort resultPort;

    public RecalculateResultActionExecutor(
            SyirkahResultCorrectionPort resultPort
    ) {
        this.resultPort = resultPort;
    }

    @Override
    public SyirkahCorrectionActionType supportedType() {
        return SyirkahCorrectionActionType.RECALCULATE_RESULT;
    }

    @Override
    public Uni<String> execute(
            SyirkahCorrectionAction action,
            SyirkahCorrectionExecutionContext context
    ) {
        return resultPort.recalculate(
                context.correctionContext(),
                action.targetId()
        ).map(SyirkahCorrectedResult::resultId);
    }
}
```

---

# 15. Allocation executor

```java
package tech.kayys.syirkah.accounting.application.correction.executor;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.correction.*;
import tech.kayys.syirkah.accounting.domain.correction.*;
import tech.kayys.syirkah.accounting.spi.correction.*;

public final class RecalculateAllocationActionExecutor
        implements SyirkahCorrectionActionExecutor {

    private final SyirkahAllocationCorrectionPort allocationPort;

    public RecalculateAllocationActionExecutor(
            SyirkahAllocationCorrectionPort allocationPort
    ) {
        this.allocationPort = allocationPort;
    }

    @Override
    public SyirkahCorrectionActionType supportedType() {
        return SyirkahCorrectionActionType.RECALCULATE_ALLOCATION;
    }

    @Override
    public Uni<String> execute(
            SyirkahCorrectionAction action,
            SyirkahCorrectionExecutionContext context
    ) {
        String correctedResultId =
                context.resultReferenceFor(action);

        return allocationPort.recalculate(
                context.correctionContext(),
                action.targetId(),
                correctedResultId
        ).map(SyirkahCorrectedAllocation::allocationId);
    }
}
```

The execution context should maintain references generated by previous actions.

---

# 16. Execution context

```java
package tech.kayys.syirkah.accounting.application.correction;

import tech.kayys.syirkah.accounting.domain.correction.SyirkahCorrectionContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SyirkahCorrectionExecutionContext {

    private final SyirkahCorrectionContext correctionContext;
    private final String operator;
    private final String reason;

    private final Map<String, String> actionReferences =
            new ConcurrentHashMap<>();

    public SyirkahCorrectionExecutionContext(
            SyirkahCorrectionContext correctionContext,
            String operator,
            String reason
    ) {
        this.correctionContext = correctionContext;
        this.operator = operator;
        this.reason = reason;
    }

    public String correctionId() {
        return correctionContext.correctionId();
    }

    public String tenantId() {
        return correctionContext.tenantId();
    }

    public String ledgerId() {
        return correctionContext.ledgerId();
    }

    public String operator() {
        return operator;
    }

    public String reason() {
        return reason;
    }

    public SyirkahCorrectionContext correctionContext() {
        return correctionContext;
    }

    public void registerReference(
            String actionId,
            String reference
    ) {
        actionReferences.put(actionId, reference);
    }

    public String referenceFor(String actionId) {
        return actionReferences.get(actionId);
    }

    public String resultReferenceFor(
            tech.kayys.syirkah.accounting.domain.correction.SyirkahCorrectionAction action
    ) {
        return action.dependsOn().stream()
                .map(actionReferences::get)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Corrected result reference is missing"
                        )
                );
    }

    public tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId
    toJournalEntryId(String value) {
        return tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId.of(
                java.util.UUID.fromString(value)
        );
    }
}
```

---

# 17. Generic executor

Now combine the persistent action ledger and executor registry.

```java
public final class DefaultSyirkahCorrectionExecutor
        implements SyirkahCorrectionExecutor {

    private final SyirkahCorrectionActionRepository actionRepository;
    private final java.util.Map<
            SyirkahCorrectionActionType,
            SyirkahCorrectionActionExecutor
            > executors;

    public DefaultSyirkahCorrectionExecutor(
            SyirkahCorrectionActionRepository actionRepository,
            java.util.List<SyirkahCorrectionActionExecutor> executors
    ) {
        this.actionRepository = actionRepository;

        this.executors = executors.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        SyirkahCorrectionActionExecutor::supportedType,
                        e -> e
                ));
    }
}
```

The execution loop becomes:

```text
load action
    ↓
check idempotency
    ↓
PENDING?
    ↓
mark IN_PROGRESS
    ↓
execute adapter
    ↓
persist resultReference
    ↓
mark COMPLETED
```

---

# 18. Important failure behavior

Suppose:

```text
REVERSE_JOURNAL       COMPLETED
RECALCULATE_RESULT    COMPLETED
RECALCULATE_ALLOCATION FAILED
```

The correction stays:

```text
CORRECTING
```

It must **not** become `READY_TO_RECLOSE`.

After retry:

```text
RECALCULATE_ALLOCATION COMPLETED
```

the engine continues downstream.

No rollback of already completed historical correction actions is attempted unless the specific action itself requires compensation.

---

# 19. One subtle but important distinction

We should distinguish:

```text
action completed
```

from:

```text
correction reconciled
```

For example:

```text
journal reversal      ✓
new journal           ✓
allocation            ✓
distribution          ✓
settlement            ✓
```

doesn't automatically mean the correction is valid.

We still need a reconciliation gate.

```java
public interface SyirkahCorrectionReconciliationService {

    Uni<SyirkahCorrectionReconciliationResult> reconcile(
            String correctionId
    );
}
```

Result:

```java
public record SyirkahCorrectionReconciliationResult(
        boolean balanced,
        boolean resultBalanced,
        boolean allocationBalanced,
        boolean distributionBalanced,
        boolean settlementBalanced,
        boolean partnerPositionBalanced,
        boolean accountingBalanced,
        java.util.List<String> errors
) {
}
```

Only:

```text
all true
+
errors empty
```

allows:

```text
CORRECTING
      ↓
READY_TO_RECLOSE
```

---

# 20. The full correction pipeline is now

```text
                 REQUEST
                    │
                    ▼
              APPROVE
                    │
                    ▼
              REOPEN PERIOD
                    │
                    ▼
             BUILD IMPACT PLAN
                    │
                    ▼
             BUILD ACTION PLAN
                    │
                    ▼
              BEGIN CORRECTION
                    │
        ┌───────────┴───────────┐
        ▼                       ▼
 accounting                business artifacts
        │                       │
 reversal                result recalculation
        │                       │
 adjustment               allocation
        │                       │
        └───────────┬───────────┘
                    ▼
             responsibility
                    │
                    ▼
               distribution
                    │
                    ▼
                settlement
                    │
                    ▼
             partner position
                    │
                    ▼
              corrected snapshot
                    │
                    ▼
             reconciliation
                    │
                    ▼
             READY_TO_RECLOSE
                    │
                    ▼
             ATOMIC FINAL CLOSE
                    │
                    ├── snapshot ACTIVE
                    ├── period LOCKED
                    └── correction RECLOSED
```

That is the core engine.

## Next slice

The next thing I would implement is the **actual distribution + settlement correction chain**, because that's where Syirkah-specific semantics become much more important than generic accounting:

```text
AllocationDelta
      ↓
DistributionDelta
      ↓
already settled?
   ┌──┴───┐
   no     yes
   │       │
   │       └── settlement correction policy
   │              ├── offset
   │              ├── refund
   │              ├── recovery
   │              ├── waiver
   │              └── manual review
   │
   └── corrected obligation
              ↓
        partner position
              ↓
        corrected snapshot
```

That is the next boundary I would lock down before implementing the final re-close transaction.

/////////////

Continue with the **distribution → settlement → partner-position correction chain**.

The key rule remains: **never mutate or delete the original distribution/settlement history**. Corrections create compensating/corrected artifacts and preserve lineage.

### 1. Distribution correction model

```java
package tech.kayys.syirkah.accounting.domain.correction.distribution;

import java.math.BigDecimal;

public record SyirkahDistributionDelta(
        String distributionId,
        BigDecimal originalAmount,
        BigDecimal correctedAmount,
        BigDecimal deltaAmount,
        String currency
) {

    public boolean increased() {
        return deltaAmount.signum() > 0;
    }

    public boolean decreased() {
        return deltaAmount.signum() < 0;
    }

    public boolean unchanged() {
        return deltaAmount.signum() == 0;
    }
}
```

```java
package tech.kayys.syirkah.accounting.domain.correction.distribution;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SyirkahCorrectedDistribution(
        String correctionId,
        String originalDistributionId,
        String correctedDistributionId,
        String tenantId,
        String ledgerId,
        String contractId,
        String partyId,
        BigDecimal amount,
        String currency,
        LocalDate effectiveDate,
        String sourceType,
        String sourceId
) {
}
```

The port should deliberately expose **business correction operations**, rather than allowing arbitrary repository mutation:

```java
package tech.kayys.syirkah.accounting.application.correction.distribution;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.correction.distribution.SyirkahCorrectedDistribution;
import tech.kayys.syirkah.accounting.domain.correction.distribution.SyirkahDistributionDelta;

import java.util.Optional;

public interface SyirkahDistributionCorrectionPort {

    Uni<Optional<SyirkahDistributionDelta>> calculateDelta(
            String tenantId,
            String ledgerId,
            String distributionId,
            String correctionId
    );

    Uni<Void> cancelDistribution(
            String tenantId,
            String ledgerId,
            String distributionId,
            String correctionId,
            String reason,
            String cancelledBy
    );

    Uni<SyirkahCorrectedDistribution> createCorrectedDistribution(
            String tenantId,
            String ledgerId,
            String distributionId,
            String correctionId,
            String createdBy
    );
}
```

### 2. Settlement correction policy

Settlement must not be treated as merely another distribution.

```java
package tech.kayys.syirkah.accounting.domain.correction.settlement;

public enum SyirkahSettlementCorrectionOutcome {
    NO_IMPACT,
    OFFSET_NEXT_DISTRIBUTION,
    REFUND,
    RECOVERY,
    MANUAL_REVIEW
}
```

```java
package tech.kayys.syirkah.accounting.domain.correction.settlement;

import java.math.BigDecimal;

public record SyirkahSettlementCorrectionContext(
        String tenantId,
        String ledgerId,
        String correctionId,
        String distributionId,
        String settlementId,
        BigDecimal originalDistributionAmount,
        BigDecimal correctedDistributionAmount,
        BigDecimal settledAmount,
        String currency,
        boolean paymentReferenceValid
) {

    public BigDecimal delta() {
        return correctedDistributionAmount
                .subtract(originalDistributionAmount);
    }

    public BigDecimal settlementDifference() {
        return correctedDistributionAmount
                .subtract(settledAmount);
    }

    public boolean fullySettled() {
        return settledAmount.compareTo(correctedDistributionAmount) == 0;
    }

    public boolean overSettled() {
        return settledAmount.compareTo(correctedDistributionAmount) > 0;
    }

    public boolean underSettled() {
        return settledAmount.compareTo(correctedDistributionAmount) < 0;
    }
}
```

```java
package tech.kayys.syirkah.accounting.domain.correction.settlement;

public record SyirkahSettlementCorrectionDecision(
        SyirkahSettlementCorrectionOutcome outcome,
        String reason,
        boolean requiresAccountingAdjustment,
        boolean requiresPartnerPositionAdjustment
) {
}
```

The policy is explicit and injectable:

```java
package tech.kayys.syirkah.accounting.domain.correction.settlement;

public interface SyirkahSettlementCorrectionPolicy {

    SyirkahSettlementCorrectionDecision evaluate(
            SyirkahSettlementCorrectionContext context
    );
}
```

A default policy can be conservative:

```java
package tech.kayys.syirkah.accounting.domain.correction.settlement;

public final class DefaultSyirkahSettlementCorrectionPolicy
        implements SyirkahSettlementCorrectionPolicy {

    @Override
    public SyirkahSettlementCorrectionDecision evaluate(
            SyirkahSettlementCorrectionContext context) {

        if (!context.paymentReferenceValid()) {
            return new SyirkahSettlementCorrectionDecision(
                    SyirkahSettlementCorrectionOutcome.MANUAL_REVIEW,
                    "Settlement payment reference is invalid.",
                    false,
                    false
            );
        }

        if (context.settledAmount().signum() < 0) {
            return new SyirkahSettlementCorrectionDecision(
                    SyirkahSettlementCorrectionOutcome.MANUAL_REVIEW,
                    "Settled amount cannot be negative.",
                    false,
                    false
            );
        }

        if (context.fullySettled()) {
            return new SyirkahSettlementCorrectionDecision(
                    SyirkahSettlementCorrectionOutcome.NO_IMPACT,
                    "Corrected distribution is fully settled.",
                    false,
                    false
            );
        }

        if (context.overSettled()) {
            return new SyirkahSettlementCorrectionDecision(
                    SyirkahSettlementCorrectionOutcome.REFUND,
                    "Settlement exceeds corrected distribution obligation.",
                    true,
                    true
            );
        }

        if (context.underSettled()) {
            return new SyirkahSettlementCorrectionDecision(
                    SyirkahSettlementCorrectionOutcome.RECOVERY,
                    "Settlement is lower than corrected distribution obligation.",
                    true,
                    true
            );
        }

        return new SyirkahSettlementCorrectionDecision(
                SyirkahSettlementCorrectionOutcome.MANUAL_REVIEW,
                "Settlement state requires explicit review.",
                false,
                false
        );
    }
}
```

The important part is that the policy is **not hard-coded into the correction executor**. A particular Syirkah product or tenant can provide another policy.

---

## 3. Distribution cancellation action

```java
package tech.kayys.syirkah.accounting.application.correction.action;

public enum SyirkahCorrectionActionType {
    REVERSE_JOURNAL,
    RECALCULATE_RESULT,
    RECALCULATE_ALLOCATION,
    RECALCULATE_RESPONSIBILITY,
    CANCEL_DISTRIBUTION,
    CREATE_CORRECTED_DISTRIBUTION,
    RECALCULATE_SETTLEMENT,
    CORRECT_PARTNER_POSITION,
    SUPERSEDE_SNAPSHOT,
    CREATE_CORRECTED_SNAPSHOT,
    REBUILD_REPORTING
}
```

```java
package tech.kayys.syirkah.accounting.application.correction.action;

import io.smallrye.mutiny.Uni;

public interface SyirkahCorrectionActionExecutor {

    SyirkahCorrectionActionType supportedType();

    Uni<SyirkahCorrectionActionResult> execute(
            SyirkahCorrectionExecutionContext context,
            SyirkahCorrectionAction action
    );
}
```

```java
package tech.kayys.syirkah.accounting.application.correction.action;

public record SyirkahCorrectionActionResult(
        String actionId,
        String targetId,
        boolean changed,
        String resultingArtifactId,
        String message
) {
}
```

Cancellation executor:

```java
package tech.kayys.syirkah.accounting.application.correction.action;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.correction.distribution.SyirkahDistributionCorrectionPort;

public final class CancelDistributionActionExecutor
        implements SyirkahCorrectionActionExecutor {

    private final SyirkahDistributionCorrectionPort distributionPort;

    public CancelDistributionActionExecutor(
            SyirkahDistributionCorrectionPort distributionPort) {
        this.distributionPort = distributionPort;
    }

    @Override
    public SyirkahCorrectionActionType supportedType() {
        return SyirkahCorrectionActionType.CANCEL_DISTRIBUTION;
    }

    @Override
    public Uni<SyirkahCorrectionActionResult> execute(
            SyirkahCorrectionExecutionContext context,
            SyirkahCorrectionAction action) {

        return distributionPort.cancelDistribution(
                context.tenantId(),
                context.ledgerId(),
                action.targetId(),
                context.correctionId(),
                "Syirkah period correction " + context.correctionId(),
                context.operator()
        ).replaceWith(
                new SyirkahCorrectionActionResult(
                        action.actionId(),
                        action.targetId(),
                        true,
                        action.targetId(),
                        "Distribution cancelled through correction lineage."
                )
        );
    }
}
```

---

## 4. Corrected distribution executor

This must use the **correction ID as part of the idempotency/source identity**.

```java
package tech.kayys.syirkah.accounting.application.correction.action;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.correction.distribution.SyirkahDistributionCorrectionPort;
import tech.kayys.syirkah.accounting.domain.correction.distribution.SyirkahCorrectedDistribution;

public final class CreateCorrectedDistributionActionExecutor
        implements SyirkahCorrectionActionExecutor {

    private final SyirkahDistributionCorrectionPort distributionPort;

    public CreateCorrectedDistributionActionExecutor(
            SyirkahDistributionCorrectionPort distributionPort) {
        this.distributionPort = distributionPort;
    }

    @Override
    public SyirkahCorrectionActionType supportedType() {
        return SyirkahCorrectionActionType.CREATE_CORRECTED_DISTRIBUTION;
    }

    @Override
    public Uni<SyirkahCorrectionActionResult> execute(
            SyirkahCorrectionExecutionContext context,
            SyirkahCorrectionAction action) {

        return distributionPort.createCorrectedDistribution(
                context.tenantId(),
                context.ledgerId(),
                action.targetId(),
                context.correctionId(),
                context.operator()
        ).map(this::result);
    }

    private SyirkahCorrectionActionResult result(
            SyirkahCorrectedDistribution distribution) {

        return new SyirkahCorrectionActionResult(
                distribution.correctionId(),
                distribution.originalDistributionId(),
                true,
                distribution.correctedDistributionId(),
                "Corrected distribution created."
        );
    }
}
```

---

# 5. Settlement correction port

Settlement correction should also be domain-specific:

```java
package tech.kayys.syirkah.accounting.application.correction.settlement;

import io.smallrye.mutiny.Uni;

public interface SyirkahSettlementCorrectionPort {

    Uni<SyirkahSettlementCorrectionContext> loadContext(
            String tenantId,
            String ledgerId,
            String distributionId,
            String correctionId
    );

    Uni<Void> createRefundObligation(
            SyirkahSettlementCorrectionContext context,
            String operator
    );

    Uni<Void> createRecoveryObligation(
            SyirkahSettlementCorrectionContext context,
            String operator
    );

    Uni<Void> createOffsetObligation(
            SyirkahSettlementCorrectionContext context,
            String operator
    );

    Uni<Void> markManualReview(
            SyirkahSettlementCorrectionContext context,
            String reason,
            String operator
    );
}
```

Then the action executor:

```java
package tech.kayys.syirkah.accounting.application.correction.action;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.correction.settlement.SyirkahSettlementCorrectionPort;
import tech.kayys.syirkah.accounting.domain.correction.settlement.*;

public final class RecalculateSettlementActionExecutor
        implements SyirkahCorrectionActionExecutor {

    private final SyirkahSettlementCorrectionPort settlementPort;
    private final SyirkahSettlementCorrectionPolicy policy;

    public RecalculateSettlementActionExecutor(
            SyirkahSettlementCorrectionPort settlementPort,
            SyirkahSettlementCorrectionPolicy policy) {

        this.settlementPort = settlementPort;
        this.policy = policy;
    }

    @Override
    public SyirkahCorrectionActionType supportedType() {
        return SyirkahCorrectionActionType.RECALCULATE_SETTLEMENT;
    }

    @Override
    public Uni<SyirkahCorrectionActionResult> execute(
            SyirkahCorrectionExecutionContext context,
            SyirkahCorrectionAction action) {

        return settlementPort.loadContext(
                context.tenantId(),
                context.ledgerId(),
                action.targetId(),
                context.correctionId()
        ).chain(settlementContext -> {

            SyirkahSettlementCorrectionDecision decision =
                    policy.evaluate(settlementContext);

            return switch (decision.outcome()) {
                case NO_IMPACT ->
                        Uni.createFrom().item(
                                new SyirkahCorrectionActionResult(
                                        action.actionId(),
                                        action.targetId(),
                                        false,
                                        settlementContext.settlementId(),
                                        decision.reason()
                                )
                        );

                case REFUND ->
                        settlementPort.createRefundObligation(
                                settlementContext,
                                context.operator()
                        ).replaceWith(
                                result(action, settlementContext, decision)
                        );

                case RECOVERY ->
                        settlementPort.createRecoveryObligation(
                                settlementContext,
                                context.operator()
                        ).replaceWith(
                                result(action, settlementContext, decision)
                        );

                case OFFSET_NEXT_DISTRIBUTION ->
                        settlementPort.createOffsetObligation(
                                settlementContext,
                                context.operator()
                        ).replaceWith(
                                result(action, settlementContext, decision)
                        );

                case MANUAL_REVIEW ->
                        settlementPort.markManualReview(
                                settlementContext,
                                decision.reason(),
                                context.operator()
                        ).replaceWith(
                                result(action, settlementContext, decision)
                        );
            };
        });
    }

    private SyirkahCorrectionActionResult result(
            SyirkahCorrectionAction action,
            SyirkahSettlementCorrectionContext context,
            SyirkahSettlementCorrectionDecision decision) {

        return new SyirkahCorrectionActionResult(
                action.actionId(),
                action.targetId(),
                true,
                context.settlementId(),
                decision.outcome().name()
        );
    }
}
```

---

# 6. Partner-position correction

The settlement result then feeds partner-position correction.

Do **not** do:

```java
partnerAccount.setBalance(correctedBalance);
```

Instead:

```java
package tech.kayys.syirkah.accounting.application.correction.position;

import io.smallrye.mutiny.Uni;

public interface SyirkahPartnerPositionCorrectionPort {

    Uni<String> createCompensatingMovement(
            String tenantId,
            String ledgerId,
            String correctionId,
            String originalMovementId,
            String operator
    );

    Uni<String> createCorrectionMovement(
            String tenantId,
            String ledgerId,
            String correctionId,
            String affectedArtifactId,
            String operator
    );
}
```

The resulting history is therefore:

```text
original movement
       │
       ▼
correction movement
       │
       ▼
new partner position
```

rather than:

```text
original movement
       X
mutated balance
```

That distinction is important for auditability.

---

# 7. Impact dependency graph

The correction planner should now produce this graph:

```text
FINANCIAL_RESULT
       │
       ▼
PARTNER_ALLOCATION
       │
       ├──────────────► LOSS_RESPONSIBILITY
       │
       ▼
DISTRIBUTION_OBLIGATION
       │
       ▼
SETTLEMENT_OBLIGATION
       │
       ▼
PARTNER_POSITION
       │
       ▼
CLOSE_SNAPSHOT
       │
       ▼
REPORTING_READ_MODEL
```

Accounting journals remain consequences:

```text
FINANCIAL_RESULT ───────► ACCOUNTING_JOURNAL
PARTNER_ALLOCATION ─────► ACCOUNTING_JOURNAL
DISTRIBUTION ───────────► ACCOUNTING_JOURNAL
SETTLEMENT ─────────────► ACCOUNTING_JOURNAL
PARTNER_POSITION ───────► ACCOUNTING_JOURNAL
```

So the planner must enforce:

```text
RECALCULATE_RESULT
        ↓
RECALCULATE_ALLOCATION
        ↓
RECALCULATE_RESPONSIBILITY
        ↓
CANCEL_DISTRIBUTION
        ↓
CREATE_CORRECTED_DISTRIBUTION
        ↓
RECALCULATE_SETTLEMENT
        ↓
CORRECT_PARTNER_POSITION
        ↓
SUPERSEDE_SNAPSHOT
        ↓
CREATE_CORRECTED_SNAPSHOT
        ↓
REBUILD_REPORTING
```

Not all nodes are mandatory. For example, if allocation does not change, distribution may also remain unchanged.

---

# 8. Delta reconciliation

The correction engine should calculate a global delta before allowing recclose:

```java
package tech.kayys.syirkah.accounting.domain.correction.reconciliation;

import java.math.BigDecimal;

public record SyirkahCorrectionReconciliation(
        BigDecimal originalResult,
        BigDecimal correctedResult,
        BigDecimal originalAllocation,
        BigDecimal correctedAllocation,
        BigDecimal originalDistribution,
        BigDecimal correctedDistribution,
        BigDecimal originalSettlement,
        BigDecimal correctedSettlement,
        BigDecimal originalPartnerPosition,
        BigDecimal correctedPartnerPosition
) {

    public boolean resultBalanced() {
        return correctedResult
                .subtract(originalResult)
                .compareTo(
                        correctedAllocation.subtract(originalAllocation)
                ) == 0;
    }

    public boolean distributionBalanced() {
        return correctedDistribution
                .subtract(originalDistribution)
                .compareTo(
                        correctedSettlement.subtract(originalSettlement)
                ) >= 0;
    }

    public boolean partnerPositionBalanced() {
        return correctedPartnerPosition
                .subtract(originalPartnerPosition)
                .compareTo(
                        correctedDistribution.subtract(originalDistribution)
                ) == 0;
    }

    public boolean isBalanced() {
        return resultBalanced()
                && distributionBalanced()
                && partnerPositionBalanced();
    }
}
```

I would actually keep the exact accounting equation implementation behind a domain reconciliation policy rather than hard-code these equations universally, because different settlement states can legitimately produce obligations such as refund/recovery.

So the production form should become:

```java
public interface SyirkahCorrectionReconciliationPolicy {

    SyirkahCorrectionReconciliationResult reconcile(
            SyirkahCorrectionReconciliationContext context
    );
}
```

with:

```java
public record SyirkahCorrectionReconciliationResult(
        boolean balanced,
        boolean requiresManualReview,
        java.util.List<String> errors
) {
}
```

---

# 9. Critical idempotency rule

Every correction-generated accounting artifact should have a deterministic source identity.

For example:

```text
SYIRKAH_CORRECTION_ADJUSTMENT
correctionId/actionId/artifactId
```

and:

```text
SYIRKAH_CORRECTION_REVERSAL
correctionId/originalJournalEntryId
```

Therefore a retry cannot create a second correction journal.

At the database level:

```sql
CREATE UNIQUE INDEX ux_syirkah_correction_artifact
ON syirkah_correction_artifact (
    tenant_id,
    ledger_id,
    correction_id,
    artifact_type,
    original_artifact_id
);
```

And for settlement obligations:

```sql
CREATE UNIQUE INDEX ux_syirkah_correction_settlement
ON syirkah_settlement_obligation (
    tenant_id,
    ledger_id,
    correction_id,
    original_settlement_id
);
```

The database uniqueness remains the final protection against duplicate financial effects.

---

# 10. Tests that should be added now

### Unsettled distribution

```text
Original allocation = 100
Original distribution = 100
Settled = 0

Corrected allocation = 120
```

Expected:

```text
distribution delta = +20
settlement outcome = RECOVERY
new obligation = 120
```

### Already settled, corrected amount lower

```text
Original distribution = 100
Settled = 100
Corrected distribution = 80
```

Expected:

```text
settled = 100
corrected = 80
over-settled = 20
outcome = REFUND
```

### Already settled, corrected amount higher

```text
Original distribution = 100
Settled = 100
Corrected distribution = 120
```

Expected:

```text
under-settled = 20
outcome = RECOVERY
```

### Policy-driven offset

```text
corrected distribution = 80
settled amount = 100
policy = OFFSET_NEXT_DISTRIBUTION
```

Expected:

```text
no historical settlement mutation
offset obligation = 20
```

### Manual review

```text
paymentReferenceValid = false
```

Expected:

```text
MANUAL_REVIEW
no automatic accounting mutation
correction cannot become READY_TO_RECLOSE
```

### Retry

Execute:

```text
correction C1
action CREATE_CORRECTED_DISTRIBUTION
target D1
```

twice.

Expected:

```text
one corrected distribution
one accounting effect
second execution returns existing action result
```

---

## Resulting correction chain

At this point the correction subsystem becomes:

```text
                CLOSED PERIOD
                     │
                     ▼
             CORRECTION REQUEST
                     │
                     ▼
              IMPACT ANALYSIS
                     │
                     ▼
              ACTION PLAN
                     │
                     ▼
        ┌────────────┴────────────┐
        ▼                         ▼
  ACCOUNTING REVERSAL       BUSINESS CORRECTION
                                  │
                                  ▼
                         FINANCIAL RESULT
                                  │
                                  ▼
                         PARTNER ALLOCATION
                                  │
                    ┌─────────────┴─────────────┐
                    ▼                           ▼
             LOSS RESPONSIBILITY          DISTRIBUTION
                                                │
                                                ▼
                                           SETTLEMENT
                                                │
                                                ▼
                                        PARTNER POSITION
                                                │
                                                ▼
                                         RECONCILIATION
                                                │
                                                ▼
                                      CORRECTED SNAPSHOT
                                                │
                                                ▼
                                        REPORTING REBUILD
                                                │
                                                ▼
                                          READY_TO_RECLOSE
                                                │
                                                ▼
                                             LOCKED
```

The next implementation slice should therefore be the **atomic finalization/reclose boundary**: persist the corrected snapshot as the active version, supersede the old snapshot, lock the fiscal period, mark the correction `RECLOSED`, and emit the audit/outbox events in one transaction, with recovery/resume behavior if execution fails before that boundary.

////////////

Next we close the loop with the **atomic finalization / reclose boundary**. This is the point where correction execution stops being merely a set of successful actions and becomes a new authoritative locked-period state.

## 1. Finalization contract

The finalization transaction must atomically establish:

```text
corrected snapshot = ACTIVE
old snapshot       = SUPERSEDED
fiscal period      = LOCKED
correction         = RECLOSED
audit evidence     = recorded
outbox events      = recorded
```

If any part fails, the transaction rolls back.

```java id="c7m41"
package tech.kayys.syirkah.accounting.application.correction;

import io.smallrye.mutiny.Uni;

public interface SyirkahCorrectionFinalizationService {

    Uni<Void> finalizeCorrection(
            String tenantId,
            String ledgerId,
            String correctionId,
            String operator
    );
}
```

The service should **not** itself decide whether the correction is ready. That decision comes from the reconciliation/execution state.

---

## 2. Finalization guard

```java id="r84q2"
package tech.kayys.syirkah.accounting.domain.correction;

public final class SyirkahCorrectionFinalizationGuard {

    public void validate(
            SyirkahPeriodCorrection correction,
            SyirkahCorrectionExecution execution,
            SyirkahCorrectionReconciliationResult reconciliation) {

        if (correction.getStatus()
                != SyirkahPeriodCorrection.CorrectionStatus.READY_TO_RECLOSE) {
            throw new IllegalStateException(
                    "Correction is not ready to reclose."
            );
        }

        if (execution.status()
                != SyirkahCorrectionExecutionStatus.READY_TO_RECLOSE) {
            throw new IllegalStateException(
                    "Correction execution is not ready to reclose."
            );
        }

        if (!reconciliation.balanced()) {
            throw new IllegalStateException(
                    "Correction reconciliation is not balanced."
            );
        }

        if (reconciliation.requiresManualReview()) {
            throw new IllegalStateException(
                    "Correction requires manual review."
            );
        }
    }
}
```

This gives us an important invariant:

```text
READY_TO_RECLOSE
    !=
"all actions happened"

READY_TO_RECLOSE
    =
"all actions happened + reconciliation passed + no unresolved review"
```

---

# 3. Corrected snapshot creation

The corrected snapshot must explicitly retain lineage.

```java id="y1f6k"
package tech.kayys.syirkah.accounting.domain.snapshot;

public record SyirkahCorrectedSnapshotMetadata(
        String snapshotId,
        String originalSnapshotId,
        String correctionId,
        int snapshotVersion,
        String policySnapshotId,
        String policySnapshotHash
) {
}
```

The snapshot builder should receive the **corrected calculation state**, rather than re-reading arbitrary live balances.

```java id="2j9qv"
package tech.kayys.syirkah.accounting.application.correction.snapshot;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.snapshot.SyirkahCloseSnapshot;

public interface SyirkahCorrectedSnapshotFactory {

    Uni<SyirkahCloseSnapshot> create(
            SyirkahCorrectionExecutionContext context,
            String originalSnapshotId,
            int nextVersion
    );
}
```

The factory must preserve:

```text
original snapshot
      │
      └── superseded by ──► corrected snapshot
```

rather than creating a completely unrelated snapshot.

---

# 4. Snapshot repository operations

The repository needs explicit lifecycle operations.

```java id="p0v9d"
package tech.kayys.syirkah.accounting.application.snapshot;

import io.smallrye.mutiny.Uni;

public interface SyirkahCloseSnapshotLifecycleRepository {

    Uni<Void> supersede(
            String tenantId,
            String ledgerId,
            String snapshotId,
            String supersededBySnapshotId
    );

    Uni<Void> activate(
            String tenantId,
            String ledgerId,
            String snapshotId
    );

    Uni<Void> verifyActiveSnapshot(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String snapshotId
    );
}
```

The database remains the final authority.

```sql id="z5c0u"
CREATE UNIQUE INDEX ux_syirkah_active_snapshot
ON syirkah_close_snapshot (
    tenant_id,
    ledger_id,
    fiscal_period_id
)
WHERE status = 'ACTIVE';
```

This prevents two active historical snapshots from existing for the same period.

---

# 5. Correction finalization transaction

Use a transaction boundary that locks the relevant records in deterministic order.

```java id="d3m8x"
package tech.kayys.syirkah.accounting.application.correction;

import io.smallrye.mutiny.Uni;

public interface SyirkahCorrectionFinalizationTransaction {

    Uni<Void> finalizeCorrection(
            String tenantId,
            String ledgerId,
            String correctionId,
            String operator
    );
}
```

Implementation shape:

```java id="w6c4p"
public final class DefaultSyirkahCorrectionFinalizationTransaction
        implements SyirkahCorrectionFinalizationTransaction {

    private final FiscalPeriodRepository fiscalPeriodRepository;
    private final SyirkahPeriodCorrectionRepository correctionRepository;
    private final SyirkahCorrectionExecutionRepository executionRepository;
    private final SyirkahCloseSnapshotLifecycleRepository snapshotRepository;
    private final SyirkahAuditEvidenceRepository auditRepository;
    private final OutboxRepository outboxRepository;

    @Override
    public Uni<Void> finalizeCorrection(
            String tenantId,
            String ledgerId,
            String correctionId,
            String operator) {

        return Uni.createFrom().voidItem()
                .chain(() -> lockFiscalPeriod(
                        tenantId,
                        ledgerId,
                        correctionId))
                .chain(() -> validateFinalState(
                        tenantId,
                        ledgerId,
                        correctionId))
                .chain(() -> activateCorrectedSnapshot(
                        tenantId,
                        ledgerId,
                        correctionId))
                .chain(() -> lockPeriod(
                        tenantId,
                        ledgerId,
                        correctionId))
                .chain(() -> markCorrectionReclosed(
                        tenantId,
                        ledgerId,
                        correctionId))
                .chain(() -> writeAudit(
                        tenantId,
                        ledgerId,
                        correctionId,
                        operator))
                .chain(() -> writeOutbox(
                        tenantId,
                        ledgerId,
                        correctionId));
    }

    // transactional implementation omitted here
}
```

In production, all these operations must execute inside the **same database transaction**, not as independent committed `Uni` chains.

---

# 6. Lock ordering

There is a subtle concurrency issue here.

Do not allow:

```text
Transaction A:
  correction → period

Transaction B:
  period → correction
```

because that creates a deadlock possibility.

Use one deterministic order:

```text
1. fiscal_period
2. correction
3. correction_execution
4. original_snapshot
5. corrected_snapshot
```

So:

```java id="k2f8a"
public interface SyirkahCorrectionLockManager {

    Uni<Void> lockFinalizationState(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String correctionId,
            String originalSnapshotId
    );
}
```

The exact SQL should use row-level locks such as:

```sql id="7h6d2q"
SELECT *
FROM fiscal_period
WHERE tenant_id = ?
  AND ledger_id = ?
  AND fiscal_period_id = ?
FOR UPDATE;
```

Then correction:

```sql id="6x3z1"
SELECT *
FROM syirkah_period_correction
WHERE tenant_id = ?
  AND ledger_id = ?
  AND correction_id = ?
FOR UPDATE;
```

This is particularly important because the normal close process and correction finalization must not be able to race.

---

# 7. Period state transition

We should keep the existing domain lifecycle strict.

```java id="a1p9s"
public void lockAfterCorrection(String lockedBy) {
    if (status != FiscalPeriodStatus.OPEN) {
        throw new IllegalStateException(
                "Only an open fiscal period can be locked."
        );
    }

    this.status = FiscalPeriodStatus.LOCKED;
    this.lockedBy = lockedBy;
    this.lockedAt = Instant.now();
}
```

Do **not** add something like:

```java
forceLock()
```

or:

```java
setStatus(LOCKED)
```

to bypass the aggregate.

---

# 8. Correction lifecycle completion

The correction itself moves only after snapshot activation and period locking succeed.

```java id="e6n2r"
public void markReclosed(
        String newSnapshotId,
        String operator) {

    requireStatus(CorrectionStatus.READY_TO_RECLOSE);

    if (newSnapshotId == null || newSnapshotId.isBlank()) {
        throw new IllegalArgumentException(
                "Corrected snapshot ID is required."
        );
    }

    this.newSnapshotId = newSnapshotId;
    this.status = CorrectionStatus.RECLOSED;
    this.completedAt = Instant.now();
    this.completedBy = operator;
}
```

The lifecycle is therefore:

```text
REQUESTED
    ↓
APPROVED
    ↓
REOPENED
    ↓
CORRECTING
    ↓
READY_TO_RECLOSE
    ↓
RECLOSED
```

There is no transition:

```text
CORRECTING → RECLOSED
```

without passing through `READY_TO_RECLOSE`.

---

# 9. Atomic snapshot swap

The transaction should conceptually perform:

```text
old snapshot:
    ACTIVE
       ↓
    SUPERSEDED

new snapshot:
    pending
       ↓
    ACTIVE
```

Do this carefully because of the unique active-snapshot index.

A safe implementation is:

```text
BEGIN
  lock period
  lock correction
  lock old snapshot

  verify old snapshot ACTIVE
  verify new snapshot exists
  verify new snapshot is valid

  old snapshot → SUPERSEDED
  new snapshot → ACTIVE

  period → LOCKED
  correction → RECLOSED

  audit evidence
  outbox events

COMMIT
```

The database constraint then protects the final state.

---

# 10. Audit evidence

The finalization should produce a dedicated evidence record.

```java id="t4j7n"
package tech.kayys.syirkah.accounting.domain.audit;

public record SyirkahCorrectionCompletedEvidence(
        String correctionId,
        String fiscalPeriodId,
        String originalSnapshotId,
        String correctedSnapshotId,
        String operator,
        String reconciliationReference
) {
}
```

Evidence type:

```java id="f0r5c"
public enum SyirkahEvidenceType {
    CONTRACT,
    CLOSE,
    DISTRIBUTION,
    LOSS_RESPONSIBILITY,
    LOSS_RECOVERY,
    LIQUIDATION,
    CAPITAL_RETURN,
    SETTLEMENT,
    TRANSITION,
    SNAPSHOT,
    ACTIVATION,
    TERMINATION,
    CORRECTION_REQUEST,
    CORRECTION_EXECUTION,
    CORRECTION_COMPLETION
}
```

The audit chain should contain:

```text
CORRECTION_REQUEST
        ↓
CORRECTION_APPROVAL
        ↓
PERIOD_REOPEN
        ↓
CORRECTION_EXECUTION
        ↓
CORRECTION_RECONCILIATION
        ↓
SNAPSHOT_SUPERSEDED
        ↓
CORRECTED_SNAPSHOT_ACTIVATED
        ↓
PERIOD_RELOCKED
        ↓
CORRECTION_COMPLETION
```

That makes the entire historical correction reconstructable.

---

# 11. Outbox events

The final transaction should emit one or more domain events through the existing outbox mechanism.

```java id="v8s2e"
public record SyirkahCorrectionCompletedEvent(
        String correctionId,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        String originalSnapshotId,
        String correctedSnapshotId
) {
}
```

And:

```java id="r3k5w"
public record SyirkahSnapshotSupersededEvent(
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        String originalSnapshotId,
        String correctedSnapshotId,
        String correctionId
) {
}
```

Both are persisted with the transaction.

The dispatcher only sees them **after commit**.

Therefore:

```text
DB transaction
    │
    ├── corrected snapshot
    ├── period lock
    ├── correction RECLOSED
    ├── audit
    └── outbox
             │
             └── COMMIT
                    │
                    ▼
               dispatcher
```

Never publish the event before the transaction commits.

---

# 12. Reporting invalidation

The reporting layer should consume:

```text
SyirkahCorrectionCompletedEvent
```

and rebuild/invalidate only the affected period.

```java id="c6u1y"
public interface SyirkahReportingCorrectionPort {

    Uni<Void> rebuildLockedPeriod(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String snapshotId
    );
}
```

But there is an important distinction:

**the reporting rebuild is downstream of the authoritative transaction.**

It must not be required for the period to become financially locked.

Therefore:

```text
snapshot + lock + correction
        = authoritative

reporting read model
        = eventually consistent projection
```

If reporting fails, the period remains correctly locked and the projection is retried through the outbox.

---

# 13. Resume after executor failure

Suppose execution reaches:

```text
CORRECTING

Action 1 ✓
Action 2 ✓
Action 3 ✓
Action 4 ✗
```

The correction remains:

```text
CORRECTING
```

not cancelled automatically.

The action ledger contains:

```text
A1 COMPLETED
A2 COMPLETED
A3 COMPLETED
A4 FAILED
A5 PENDING
...
```

Retrying the executor should query the persistent action ledger first.

```java id="q7v2n"
public interface SyirkahCorrectionActionLedger {

    Uni<Boolean> existsCompleted(
            String tenantId,
            String ledgerId,
            String correctionId,
            SyirkahCorrectionActionType actionType,
            String targetId
    );

    Uni<Void> markStarted(
            String actionId,
            String operator
    );

    Uni<Void> markCompleted(
            String actionId,
            String resultingArtifactId
    );

    Uni<Void> markFailed(
            String actionId,
            String reason
    );
}
```

Thus retry becomes:

```text
FAILED action
   ↓
check idempotency key
   ↓
already completed?
   ├── yes → reuse result
   └── no  → execute
```

This is essential because correction execution can be interrupted by application crashes, DB connectivity failures, deployment, or timeout.

---

# 14. Finalization invariant

The strongest invariant we want is:

```java id="n5r1k"
public boolean isAuthoritativeCorrectionState(
        SyirkahPeriodCorrection correction,
        FiscalPeriod period,
        SyirkahCloseSnapshot snapshot) {

    return correction.getStatus()
                == SyirkahPeriodCorrection.CorrectionStatus.RECLOSED
            && period.getStatus()
                == FiscalPeriodStatus.LOCKED
            && snapshot.getStatus()
                == SyirkahCloseSnapshotStatus.ACTIVE
            && snapshot.getCorrectionId()
                .equals(correction.getCorrectionId());
}
```

And the inverse is equally important:

> A correction must never be `RECLOSED` while the fiscal period is still `OPEN` or while the corrected snapshot is not `ACTIVE`.

---

# 15. Recovery/integrity checker

Because this is financial infrastructure, add a periodic integrity checker rather than relying only on transaction correctness.

```java id="m0x6q"
public interface SyirkahCorrectionIntegrityChecker {

    Uni<SyirkahCorrectionIntegrityResult> check(
            String tenantId,
            String ledgerId,
            String correctionId
    );
}
```

Checks:

```text
RECLOSED correction
 ├─ fiscal period LOCKED
 ├─ corrected snapshot ACTIVE
 ├─ original snapshot SUPERSEDED
 ├─ corrected snapshot references correction
 ├─ reconciliation balanced
 ├─ all mandatory actions COMPLETED
 ├─ no unresolved MANUAL_REVIEW
 ├─ journal lineage valid
 ├─ audit chain valid
 └─ outbox event exists
```

If any invariant fails:

```text
INTEGRITY_FAILURE
```

rather than silently repairing financial state.

---

## The complete locked-period correction architecture is now

```text
                    LOCKED PERIOD
                         │
                         ▼
                CORRECTION REQUEST
                         │
                         ▼
                APPROVAL / POLICY
                         │
                         ▼
                   PERIOD REOPEN
                         │
                         ▼
                 IMPACT ANALYSIS
                         │
                         ▼
                   ACTION PLAN
                         │
             ┌───────────┴───────────┐
             │                       │
       ACCOUNTING               BUSINESS
       REVERSALS                CORRECTION
             │                       │
             │                RESULT
             │                  ↓
             │              ALLOCATION
             │              ↓       ↓
             │       RESPONSIBILITY DISTRIBUTION
             │                         ↓
             │                    SETTLEMENT
             │                         ↓
             │                  PARTNER POSITION
             │                         │
             └──────────────┬──────────┘
                            ▼
                     RECONCILIATION
                            │
                     ┌──────┴──────┐
                     │             │
                  FAILED          PASS
                     │             │
                  RESUME           ▼
                              NEW SNAPSHOT
                                   │
                              OLD SUPERSEDED
                                   │
                              PERIOD LOCKED
                                   │
                              RECLOSED
                                   │
                           AUDIT + OUTBOX
                                   │
                                   ▼
                         REPORTING REBUILD
```

The next layer to implement after this is the **full correction execution coordinator** that ties the action planner, persistent action ledger, transaction boundaries, reconciliation gate, snapshot builder, and finalization transaction together. That will turn the pieces we've built into one resumable end-to-end correction workflow.

/////////////

Next, implement the **end-to-end correction coordinator**. This becomes the application-level orchestration layer over the components already designed.

## 1. Correction execution coordinator

```java
package tech.kayys.syirkah.accounting.application.correction;

import io.smallrye.mutiny.Uni;

public interface SyirkahCorrectionCoordinator {

    Uni<Void> execute(
            String tenantId,
            String ledgerId,
            String correctionId,
            String operator
    );
}
```

The coordinator should be deliberately thin. It orchestrates; it does not contain accounting rules.

```java
package tech.kayys.syirkah.accounting.application.correction;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.correction.action.SyirkahCorrectionAction;
import tech.kayys.syirkah.accounting.application.correction.action.SyirkahCorrectionActionExecutor;
import tech.kayys.syirkah.accounting.application.correction.action.SyirkahCorrectionExecutionContext;

import java.util.List;

public final class DefaultSyirkahCorrectionCoordinator
        implements SyirkahCorrectionCoordinator {

    private final SyirkahCorrectionRepository correctionRepository;
    private final SyirkahCorrectionPlanRepository planRepository;
    private final SyirkahCorrectionExecutionRepository executionRepository;
    private final SyirkahCorrectionActionLedger actionLedger;
    private final SyirkahCorrectionActionExecutorRegistry executorRegistry;
    private final SyirkahCorrectionReconciliationService reconciliationService;
    private final SyirkahCorrectionFinalizationService finalizationService;

    public DefaultSyirkahCorrectionCoordinator(
            SyirkahCorrectionRepository correctionRepository,
            SyirkahCorrectionPlanRepository planRepository,
            SyirkahCorrectionExecutionRepository executionRepository,
            SyirkahCorrectionActionLedger actionLedger,
            SyirkahCorrectionActionExecutorRegistry executorRegistry,
            SyirkahCorrectionReconciliationService reconciliationService,
            SyirkahCorrectionFinalizationService finalizationService) {

        this.correctionRepository = correctionRepository;
        this.planRepository = planRepository;
        this.executionRepository = executionRepository;
        this.actionLedger = actionLedger;
        this.executorRegistry = executorRegistry;
        this.reconciliationService = reconciliationService;
        this.finalizationService = finalizationService;
    }

    @Override
    public Uni<Void> execute(
            String tenantId,
            String ledgerId,
            String correctionId,
            String operator) {

        return correctionRepository.findById(
                    tenantId,
                    ledgerId,
                    correctionId
                )
                .onItem()
                .ifNull()
                .failWith(() ->
                        new IllegalStateException(
                                "Syirkah correction was not found."
                        ))
                .chain(correction ->
                        planRepository.findByCorrectionId(
                                tenantId,
                                ledgerId,
                                correctionId
                        ).chain(plan ->
                                executionRepository.findOrCreate(
                                        tenantId,
                                        ledgerId,
                                        correctionId,
                                        plan.planId()
                                ).chain(execution -> {

                                    if (execution.isCompleted()) {
                                        return Uni.createFrom().voidItem();
                                    }

                                    execution.begin();
                                    return executionRepository.save(execution)
                                            .chain(() ->
                                                    executeActions(
                                                            correction,
                                                            plan,
                                                            execution,
                                                            tenantId,
                                                            ledgerId,
                                                            operator
                                                    ))
                                            .chain(() ->
                                                    reconciliationService
                                                            .reconcile(
                                                                    tenantId,
                                                                    ledgerId,
                                                                    correctionId
                                                            ))
                                            .chain(result -> {
                                                if (!result.balanced()) {
                                                    return Uni.createFrom()
                                                            .failure(
                                                                    new IllegalStateException(
                                                                            "Syirkah correction reconciliation failed."
                                                                    )
                                                            );
                                                }

                                                return correctionRepository
                                                        .markReadyToReclose(
                                                                tenantId,
                                                                ledgerId,
                                                                correctionId
                                                        );
                                            })
                                            .chain(() ->
                                                    finalizationService
                                                            .finalizeCorrection(
                                                                    tenantId,
                                                                    ledgerId,
                                                                    correctionId,
                                                                    operator
                                                            ));
                                })
                        ));
    }

    private Uni<Void> executeActions(
            SyirkahPeriodCorrection correction,
            SyirkahCorrectionPlan plan,
            SyirkahCorrectionExecution execution,
            String tenantId,
            String ledgerId,
            String operator) {

        SyirkahCorrectionExecutionContext context =
                new SyirkahCorrectionExecutionContext(
                        correction.getCorrectionId(),
                        tenantId,
                        ledgerId,
                        correction.getFiscalPeriodId(),
                        correction.getCloseCycleId(),
                        correction.getOriginalSnapshotId(),
                        operator
                );

        return executeNext(
                plan.actions(),
                0,
                context,
                execution
        );
    }

    private Uni<Void> executeNext(
            List<SyirkahCorrectionAction> actions,
            int index,
            SyirkahCorrectionExecutionContext context,
            SyirkahCorrectionExecution execution) {

        if (index >= actions.size()) {
            return Uni.createFrom().voidItem();
        }

        SyirkahCorrectionAction action = actions.get(index);

        return actionLedger.existsCompleted(
                    context.tenantId(),
                    context.ledgerId(),
                    context.correctionId(),
                    action.type(),
                    action.targetId()
                )
                .chain(completed -> {

                    if (completed) {
                        return executeNext(
                                actions,
                                index + 1,
                                context,
                                execution
                        );
                    }

                    SyirkahCorrectionActionExecutor executor =
                            executorRegistry.resolve(action.type());

                    return actionLedger.markStarted(
                                action.actionId(),
                                context.operator()
                            )
                            .chain(() ->
                                    executor.execute(
                                            context,
                                            action
                                    ))
                            .chain(result ->
                                    actionLedger.markCompleted(
                                            action.actionId(),
                                            result.resultingArtifactId()
                                    ))
                            .chain(() ->
                                    executeNext(
                                            actions,
                                            index + 1,
                                            context,
                                            execution
                                    ));
                });
    }
}
```

There is one important refinement: **the action ledger write and the financial mutation must share an appropriate transaction/idempotency boundary**. The coordinator itself should not mark an action `COMPLETED` before the underlying operation is durably committed.

---

# 2. Persistent execution state

Use an explicit state machine.

```java
package tech.kayys.syirkah.accounting.domain.correction;

public enum SyirkahCorrectionExecutionStatus {
    PLANNED,
    EXECUTING,
    BLOCKED,
    FAILED,
    READY_TO_RECLOSE,
    COMPLETED
}
```

```java
package tech.kayys.syirkah.accounting.domain.correction;

import java.time.Instant;

public final class SyirkahCorrectionExecution {

    private final String executionId;
    private final String correctionId;
    private final String planId;

    private SyirkahCorrectionExecutionStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private String failureReason;

    public SyirkahCorrectionExecution(
            String executionId,
            String correctionId,
            String planId) {

        this.executionId = executionId;
        this.correctionId = correctionId;
        this.planId = planId;
        this.status = SyirkahCorrectionExecutionStatus.PLANNED;
    }

    public void begin() {
        if (status == SyirkahCorrectionExecutionStatus.EXECUTING) {
            return;
        }

        if (status != SyirkahCorrectionExecutionStatus.PLANNED
                && status != SyirkahCorrectionExecutionStatus.FAILED) {
            throw new IllegalStateException(
                    "Correction execution cannot be started from status "
                            + status
            );
        }

        status = SyirkahCorrectionExecutionStatus.EXECUTING;
        startedAt = Instant.now();
        failureReason = null;
    }

    public void fail(String reason) {
        if (status != SyirkahCorrectionExecutionStatus.EXECUTING) {
            throw new IllegalStateException(
                    "Only executing corrections can fail."
            );
        }

        status = SyirkahCorrectionExecutionStatus.FAILED;
        failureReason = reason;
    }

    public void readyToReclose() {
        if (status != SyirkahCorrectionExecutionStatus.EXECUTING) {
            throw new IllegalStateException(
                    "Correction execution is not executing."
            );
        }

        status = SyirkahCorrectionExecutionStatus.READY_TO_RECLOSE;
    }

    public void complete() {
        if (status != SyirkahCorrectionExecutionStatus.READY_TO_RECLOSE) {
            throw new IllegalStateException(
                    "Correction execution is not ready to complete."
            );
        }

        status = SyirkahCorrectionExecutionStatus.COMPLETED;
        completedAt = Instant.now();
    }

    public boolean isCompleted() {
        return status == SyirkahCorrectionExecutionStatus.COMPLETED;
    }

    public SyirkahCorrectionExecutionStatus status() {
        return status;
    }
}
```

---

# 3. Action dependency validation before execution

The planner already creates a topological order. The executor should nevertheless validate it.

```java
package tech.kayys.syirkah.accounting.application.correction.action;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SyirkahCorrectionActionOrderValidator {

    public void validate(List<SyirkahCorrectionAction> actions) {

        Set<String> completed = new HashSet<>();

        for (SyirkahCorrectionAction action : actions) {
            for (String dependency : action.dependsOn()) {
                if (!completed.contains(dependency)) {
                    throw new IllegalStateException(
                            "Correction action dependency has not been completed: "
                                    + dependency
                    );
                }
            }

            completed.add(action.actionId());
        }
    }
}
```

This protects against corrupted or manually altered plans.

---

# 4. The action ledger needs an idempotency key

Do not depend only on `actionId`.

The stable financial identity should be:

```java
package tech.kayys.syirkah.accounting.application.correction.action;

public record SyirkahCorrectionActionKey(
        String tenantId,
        String ledgerId,
        String correctionId,
        SyirkahCorrectionActionType actionType,
        String targetId
) {
}
```

Database:

```sql
CREATE UNIQUE INDEX ux_syirkah_correction_action_key
ON syirkah_correction_action (
    tenant_id,
    ledger_id,
    correction_id,
    action_type,
    target_id
);
```

This protects against two workers accidentally executing the same correction action concurrently.

---

# 5. Concurrency protection

The coordinator should acquire a correction execution lease.

```java
package tech.kayys.syirkah.accounting.application.correction;

import io.smallrye.mutiny.Uni;

import java.time.Duration;

public interface SyirkahCorrectionExecutionLease {

    Uni<Boolean> acquire(
            String tenantId,
            String ledgerId,
            String correctionId,
            String owner,
            Duration duration
    );

    Uni<Void> release(
            String tenantId,
            String ledgerId,
            String correctionId,
            String owner
    );
}
```

This gives:

```text
Worker A
   │
   ├── acquire correction C1 ✓
   │
   └── execute

Worker B
   │
   └── acquire correction C1 ✗
```

The database action-key uniqueness remains the second line of defense.

For financial corrections, I would use both.

---

# 6. Correction request must be immutable after approval

Once execution starts, the request scope must not be changed underneath it.

Add a version:

```java
public final class SyirkahPeriodCorrection {

    private long version;

    public long version() {
        return version;
    }

    public void incrementVersion() {
        version++;
    }
}
```

Plan stores:

```java
public record SyirkahCorrectionPlan(
        String planId,
        String correctionId,
        long correctionVersion,
        SyirkahCorrectionImpactPlan impactPlan,
        java.util.List<SyirkahCorrectionAction> actions
) {
}
```

Before execution:

```java
if (correction.version() != plan.correctionVersion()) {
    throw new IllegalStateException(
            "Correction plan is stale and must be rebuilt."
    );
}
```

This prevents:

```text
Plan built for scope A
       ↓
scope changed to B
       ↓
old plan executes against B
```

---

# 7. Plan invalidation

A correction plan becomes invalid when any authoritative input changes.

```java
package tech.kayys.syirkah.accounting.application.correction;

public interface SyirkahCorrectionPlanValidator {

    void validate(
            SyirkahPeriodCorrection correction,
            SyirkahCorrectionPlan plan
    );
}
```

Inputs include:

```text
correction version
original snapshot ID
policy snapshot ID
policy snapshot hash
calculation ID
impact-plan hash
```

I would add a plan hash:

```java
public record SyirkahCorrectionPlanFingerprint(
        String correctionVersion,
        String originalSnapshotId,
        String policySnapshotHash,
        String impactPlanHash
) {
}
```

This gives deterministic replay protection.

---

# 8. Execution should use frozen policy

This is especially important for Syirkah.

The correction must **not** call:

```java
currentPolicyResolver.resolve(...)
```

during correction execution.

Instead:

```text
original close
     │
     ▼
policy snapshot P1
     │
     ▼
original calculation
     │
     ▼
correction
     │
     ▼
recalculate using P1
```

Never:

```text
original close → P1
correction → current P2
```

Otherwise historical correction becomes dependent on today's policy configuration.

---

# 9. Business correction versus accounting correction

The coordinator should maintain two separate action classes.

### Business artifacts

```text
RESULT
ALLOCATION
RESPONSIBILITY
DISTRIBUTION
SETTLEMENT
PARTNER POSITION
SNAPSHOT
```

### Accounting artifacts

```text
JOURNAL REVERSAL
JOURNAL ADJUSTMENT
```

A business correction should produce its accounting consequences.

For example:

```text
Allocation changed
       │
       ├── allocation correction
       │
       └── accounting adjustment
```

Not:

```text
journal changed
   ↓
pretend allocation changed
```

This maintains the domain as the source of business truth.

---

# 10. Correction event stream

Introduce explicit correction events:

```java
public enum SyirkahCorrectionEventType {
    REQUESTED,
    APPROVED,
    REOPENED,
    EXECUTION_STARTED,
    ACTION_COMPLETED,
    ACTION_FAILED,
    RECONCILIATION_PASSED,
    RECONCILIATION_FAILED,
    SNAPSHOT_SUPERSEDED,
    SNAPSHOT_ACTIVATED,
    RECLOSED,
    CANCELLED
}
```

Event envelope:

```java
public record SyirkahCorrectionEvent(
        String eventId,
        String tenantId,
        String ledgerId,
        String correctionId,
        SyirkahCorrectionEventType type,
        String artifactId,
        String operator,
        java.time.Instant occurredAt
) {
}
```

These are excellent for audit reconstruction and operational observability.

---

# 11. Failure semantics

Do not automatically cancel on execution failure.

```text
REQUESTED ──► APPROVED ──► REOPENED ──► CORRECTING
                                         │
                                         ├── failure
                                         │     ↓
                                         │   FAILED
                                         │     │
                                         │     └── retry
                                         │
                                         └── success
                                               ↓
                                        READY_TO_RECLOSE
                                               ↓
                                            RECLOSED
```

A failed correction remains recoverable.

Cancellation should be permitted only before irreversible financial correction work has begun, according to the correction policy.

---

# 12. Strong finalization transaction

The final service should ultimately be this small:

```java
package tech.kayys.syirkah.accounting.application.correction;

import io.smallrye.mutiny.Uni;

public final class DefaultSyirkahCorrectionFinalizationService
        implements SyirkahCorrectionFinalizationService {

    private final SyirkahCorrectionFinalizationTransaction transaction;

    public DefaultSyirkahCorrectionFinalizationService(
            SyirkahCorrectionFinalizationTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public Uni<Void> finalizeCorrection(
            String tenantId,
            String ledgerId,
            String correctionId,
            String operator) {

        return transaction.finalizeCorrection(
                tenantId,
                ledgerId,
                correctionId,
                operator
        );
    }
}
```

The transaction implementation owns all atomicity.

That separation is useful because the application service remains easy to test, while the infrastructure transaction can use the project's actual reactive transaction mechanism.

---

# 13. Complete state machine

At this point the correction subsystem has a clean state model:

```text
                         ┌──────────────┐
                         │   REQUESTED  │
                         └──────┬───────┘
                                │ approve
                                ▼
                         ┌──────────────┐
                         │   APPROVED   │
                         └──────┬───────┘
                                │ reopen
                                ▼
                         ┌──────────────┐
                         │   REOPENED   │
                         └──────┬───────┘
                                │ begin
                                ▼
                         ┌──────────────┐
                    ┌───►│  CORRECTING  │◄─────┐
                    │    └──────┬───────┘      │
                    │           │               │
                 retry        success         failure
                    │           │               │
                    │           ▼               ▼
                    │    ┌──────────────┐   ┌────────┐
                    └────│ READY_TO_    │   │ FAILED │
                         │ RECLOSE      │   └────────┘
                         └──────┬───────┘
                                │ atomic finalize
                                ▼
                         ┌──────────────┐
                         │   RECLOSED   │
                         └──────────────┘
```

And the financial authority is:

```text
RECLOSED
   ⇔
ACTIVE corrected snapshot
+
LOCKED fiscal period
+
balanced reconciliation
+
completed correction actions
+
valid audit/outbox state
```

---

## 14. One more important boundary: normal close vs correction close

We should now make the normal close processor and correction finalizer converge on the same **close invariant**, rather than creating two unrelated locking implementations.

Introduce:

```java
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

public interface AccountingPeriodLockService {

    Uni<Void> lockNormalClose(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String operator
    );

    Uni<Void> lockAfterCorrection(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String correctionId,
            String operator
    );
}
```

Both paths eventually enforce:

```text
period LOCKED
    +
required close artifacts present
    +
snapshot integrity valid
    +
journal reconciliation valid
```

The difference is only **how the period reached that state**.

This avoids having:

```text
normal close rules
      ≠
correction reclose rules
```

which would eventually become a source of inconsistencies.

---

### Next slice

The architecture is now ready for the **close/reclose common invariant layer**. The next implementation should unify:

1. `CloseValidationGate`
2. `JournalReconciliationGate`
3. `SyirkahSnapshotIntegrityGate`
4. `PartnerPositionReconciliationGate`
5. `DistributionSettlementReconciliationGate`
6. `CorrectionReconciliationGate`
7. `AccountingPeriodLockService`

into a single **period finalization pipeline**, so both initial `tutup buku` and corrected `tutup buku` use the same financial safety gates while retaining their distinct workflows.

////////////

Next, let’s implement the **common period-finalization gate layer** so normal `tutup buku` and correction/reclose use the same safety model.

## 1. Common finalization gate

```java id="q8m2x"
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

public interface CloseValidationGate {

    Uni<CloseGateResult> validate(CloseValidationContext context);
}
```

```java id="c4v7n"
package tech.kayys.syirkah.accounting.application.close;

import java.util.List;

public record CloseGateResult(
        boolean passed,
        List<String> errors
) {

    public static CloseGateResult passed() {
        return new CloseGateResult(true, List.of());
    }

    public static CloseGateResult failed(List<String> errors) {
        return new CloseGateResult(false, List.copyOf(errors));
    }
}
```

Context:

```java id="w9k31"
package tech.kayys.syirkah.accounting.application.close;

public record CloseValidationContext(
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        String closeCycleId,
        String snapshotId,
        String correctionId
) {

    public boolean isCorrection() {
        return correctionId != null && !correctionId.isBlank();
    }
}
```

This keeps the gates independent of whether the period is being closed for the first time or reclosed after correction.

---

# 2. Journal reconciliation gate

```java id="n4p7d"
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

public interface JournalReconciliationGate {

    Uni<CloseGateResult> validate(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId
    );
}
```

The implementation should verify at least:

```text
debits = credits
posted journal entries are valid
no orphan journal lines
source lineage is valid
required correction journals exist
reversal relationships are valid
```

Importantly, this gate should **not** calculate Syirkah allocations. That belongs to the Syirkah-specific gates.

---

# 3. Partner-position reconciliation gate

```java id="u2w6s"
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

public interface PartnerPositionReconciliationGate {

    Uni<CloseGateResult> validate(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String snapshotId
    );
}
```

Checks:

```text
opening position
 + contributions
 + profit allocations
 - loss allocations
 - returns
 - impairments
 - withdrawals
 = closing position
```

And at contract level:

```text
sum partner closing capital
    =
contract closing capital
```

No mutable `PartnerAccount` balance should be treated as the authoritative source for this gate.

---

# 4. Distribution and settlement gate

```java id="k0t9s"
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

public interface DistributionSettlementReconciliationGate {

    Uni<CloseGateResult> validate(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String snapshotId
    );
}
```

It verifies:

```text
allocation
   ↓
distribution obligation
   ↓
settlement
```

For every affected obligation:

```text
calculated amount
=
approved amount
=
posted amount
+
valid outstanding settlement
```

subject to the obligation's actual lifecycle.

For corrections, it must additionally detect:

```text
refund obligation still open
recovery obligation still open
offset obligation still open
manual review unresolved
```

---

# 5. Snapshot integrity gate

This one is particularly important for locked historical periods.

```java id="a6f4q"
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

public interface SyirkahSnapshotIntegrityGate {

    Uni<CloseGateResult> validate(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String snapshotId
    );
}
```

Checks:

```text
snapshot exists
snapshot status = ACTIVE
snapshot belongs to period
snapshot belongs to ledger
snapshot contract/version exists
calculation exists
policy snapshot exists
policy hash matches
snapshot hash matches
partner positions reconcile
allocation totals reconcile
result type matches result
```

For a corrected period:

```text
snapshot.correctionId = correction.correctionId
```

must also hold.

---

# 6. Syirkah-specific result gate

```java id="c2e7p"
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

public interface SyirkahResultReconciliationGate {

    Uni<CloseGateResult> validate(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String snapshotId
    );
}
```

Checks:

```text
revenue
- direct costs
- operating costs
+/- other result components
= net result
```

Then:

```text
PROFIT
    → profit allocation exists

LOSS
    → loss allocation exists
    → loss responsibility exists where required

BREAK_EVEN
    → zero-result handling is valid
```

The actual calculation policy remains in the existing Syirkah result engine; this is a validation gate.

---

# 7. Gate pipeline

Now compose the gates.

```java id="r3k6u"
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

import java.util.List;

public final class SyirkahCloseValidationPipeline {

    private final List<CloseValidationGate> gates;

    public SyirkahCloseValidationPipeline(
            List<CloseValidationGate> gates) {
        this.gates = List.copyOf(gates);
    }

    public Uni<CloseGateResult> validate(
            CloseValidationContext context) {

        return validateNext(context, 0, List.of());
    }

    private Uni<CloseGateResult> validateNext(
            CloseValidationContext context,
            int index,
            List<String> errors) {

        if (index >= gates.size()) {
            return Uni.createFrom().item(
                    errors.isEmpty()
                            ? CloseGateResult.passed()
                            : CloseGateResult.failed(errors)
            );
        }

        return gates.get(index)
                .validate(context)
                .chain(result -> {

                    List<String> combined = new java.util.ArrayList<>(
                            errors
                    );

                    combined.addAll(result.errors());

                    return validateNext(
                            context,
                            index + 1,
                            combined
                    );
                });
    }
}
```

I would deliberately **run all gates**, rather than stop at the first failure. Operators need one complete reconciliation report.

---

# 8. Standard gate ordering

Use a deterministic order:

```text id="2s6b4"
1. CLOSE_TASK_GATE
2. JOURNAL_RECONCILIATION_GATE
3. SYIRKAH_RESULT_GATE
4. PARTNER_POSITION_GATE
5. DISTRIBUTION_SETTLEMENT_GATE
6. SNAPSHOT_INTEGRITY_GATE
7. CORRECTION_GATE
```

Why this order?

Because the dependency direction is:

```text
accounting
    ↓
result
    ↓
allocation / position
    ↓
distribution
    ↓
settlement
    ↓
snapshot
    ↓
period finalization
```

---

# 9. Correction-specific gate

Normal close doesn't need to validate correction lineage.

```java id="z8p4k"
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

public final class CorrectionReconciliationGate
        implements CloseValidationGate {

    private final SyirkahCorrectionReconciliationService service;

    public CorrectionReconciliationGate(
            SyirkahCorrectionReconciliationService service) {
        this.service = service;
    }

    @Override
    public Uni<CloseGateResult> validate(
            CloseValidationContext context) {

        if (!context.isCorrection()) {
            return Uni.createFrom().item(
                    CloseGateResult.passed()
            );
        }

        return service.reconcile(
                context.tenantId(),
                context.ledgerId(),
                context.correctionId()
        ).map(result -> {

            if (result.balanced()
                    && !result.requiresManualReview()) {

                return CloseGateResult.passed();
            }

            java.util.List<String> errors =
                    new java.util.ArrayList<>(
                            result.errors()
                    );

            if (!result.balanced()) {
                errors.add(
                        "Correction reconciliation is not balanced."
                );
            }

            if (result.requiresManualReview()) {
                errors.add(
                        "Correction contains unresolved manual review."
                );
            }

            return CloseGateResult.failed(errors);
        });
    }
}
```

---

# 10. Unified finalization service

Now both workflows can use the same finalization pipeline.

```java id="s6k8q"
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

public interface AccountingPeriodFinalizationService {

    Uni<Void> finalize(
            CloseValidationContext context,
            String operator
    );
}
```

Implementation:

```java id="p2z4h"
public final class DefaultAccountingPeriodFinalizationService
        implements AccountingPeriodFinalizationService {

    private final SyirkahCloseValidationPipeline validationPipeline;
    private final AccountingPeriodLockService lockService;

    public DefaultAccountingPeriodFinalizationService(
            SyirkahCloseValidationPipeline validationPipeline,
            AccountingPeriodLockService lockService) {

        this.validationPipeline = validationPipeline;
        this.lockService = lockService;
    }

    @Override
    public Uni<Void> finalize(
            CloseValidationContext context,
            String operator) {

        return validationPipeline
                .validate(context)
                .chain(result -> {

                    if (!result.passed()) {
                        return Uni.createFrom().failure(
                                new CloseValidationException(
                                        result.errors()
                                )
                        );
                    }

                    if (context.isCorrection()) {
                        return lockService.lockAfterCorrection(
                                context.tenantId(),
                                context.ledgerId(),
                                context.fiscalPeriodId(),
                                context.correctionId(),
                                operator
                        );
                    }

                    return lockService.lockNormalClose(
                            context.tenantId(),
                            context.ledgerId(),
                            context.fiscalPeriodId(),
                            operator
                    );
                });
    }
}
```

Exception:

```java id="e4f1a"
package tech.kayys.syirkah.accounting.application.close;

import java.util.List;

public final class CloseValidationException
        extends RuntimeException {

    private final List<String> errors;

    public CloseValidationException(List<String> errors) {
        super("Accounting period finalization validation failed.");
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
```

---

# 11. Normal close now becomes simpler

Existing `CloseCycle` remains the aggregate controlling task lifecycle.

The close application service becomes:

```text id="2z9m5v"
complete mandatory tasks
        ↓
build/freeze snapshot
        ↓
run common validation pipeline
        ↓
lock period
        ↓
publish close event
```

The existing Syirkah mandatory tasks remain:

```text
SUBLEDGER_CUTOFF
ACCRUALS
DEPRECIATION
FX_REVALUATION
RECONCILIATION
SYIRKAH_RESULT
SYIRKAH_ALLOCATION
SYIRKAH_DISTRIBUTION
LOCK
```

The new gates don't replace those tasks. They validate the **result of the tasks** before the final lock.

That distinction is important.

---

# 12. Correction reclose becomes the same pipeline

The correction workflow becomes:

```text id="d8r4n"
CORRECTION
   ↓
REOPEN
   ↓
CORRECTING
   ↓
execute correction actions
   ↓
build corrected snapshot
   ↓
READY_TO_RECLOSE
   ↓
common finalization pipeline
   ↓
LOCK
   ↓
RECLOSED
```

So there is no special unsafe "correction lock".

---

# 13. Finalization transaction boundary

One subtle issue remains: validation and locking can race.

This is unsafe:

```text id="7u3x2d"
validate
   ↓
wait
   ↓
lock
```

because another transaction could alter relevant state between validation and lock.

Therefore:

### Read-only validation

Can happen before the final transaction for efficiency.

### Final validation

Must happen again inside the final transaction under the appropriate locks.

```text id="f2v9rj"
pre-validation
     ↓
action execution
     ↓
reconciliation
     ↓
BEGIN FINAL TRANSACTION
     │
     ├── lock fiscal period
     ├── lock correction
     ├── lock snapshot
     ├── FINAL validation
     ├── activate snapshot
     ├── supersede old snapshot
     ├── LOCK period
     ├── RECLOSED correction
     ├── audit
     └── outbox
     │
    COMMIT
```

This is the actual financial safety boundary.

---

# 14. Final transaction interface

```java id="q7n3x"
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;

public interface AccountingPeriodFinalizationTransaction {

    Uni<Void> finalize(
            CloseValidationContext context,
            String operator
    );
}
```

Then:

```java id="a3w6t"
public final class DefaultAccountingPeriodLockService
        implements AccountingPeriodLockService {

    private final AccountingPeriodFinalizationTransaction transaction;

    public DefaultAccountingPeriodLockService(
            AccountingPeriodFinalizationTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public Uni<Void> lockNormalClose(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String operator) {

        return transaction.finalize(
                new CloseValidationContext(
                        tenantId,
                        ledgerId,
                        fiscalPeriodId,
                        null,
                        null,
                        null
                ),
                operator
        );
    }

    @Override
    public Uni<Void> lockAfterCorrection(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String correctionId,
            String operator) {

        return transaction.finalize(
                new CloseValidationContext(
                        tenantId,
                        ledgerId,
                        fiscalPeriodId,
                        null,
                        null,
                        correctionId
                ),
                operator
        );
    }
}
```

The infrastructure implementation is then the only place that knows how the project's reactive DB transaction is actually started/committed.

---

# 15. Important correction: snapshot must exist before final lock

For both workflows:

```text id="n1k4zy"
NO:
period → LOCKED
snapshot → created later
```

Instead:

```text id="2q7s8v"
snapshot frozen
    ↓
snapshot integrity validated
    ↓
period locked
```

For correction:

```text id="p4m6x"
old ACTIVE snapshot
       ↓
corrected snapshot prepared
       ↓
corrected snapshot validated
       ↓
atomic ACTIVE/SUPERSEDED swap
       ↓
period LOCKED
```

This guarantees a locked period always has a valid authoritative snapshot.

---

# 16. Final invariant table

| State | Authoritative reporting source | Can mutate business facts? | Can reopen? |
|---|---|---:|---:|
| OPEN | live/read model | Yes | — |
| CLOSING | live + closing artifacts | Controlled | Yes |
| LOCKED | active close snapshot | No | Only authorized correction |
| REOPENED | live/correction state | Controlled | Yes |
| CORRECTING | correction artifacts | Controlled | Yes |
| READY_TO_RECLOSE | corrected artifacts | No new business correction | Yes |
| RECLOSED | corrected active snapshot | No | Only a new authorized correction |

The critical invariant is:

```text
LOCKED
    → exactly one ACTIVE snapshot

RECLOSED
    → exactly one ACTIVE corrected snapshot
    → old snapshot SUPERSEDED
```

---

## 17. What this gives Syirkah

The architecture now has a clean distinction between:

```text
DOMAIN RULES
    SyirkahResultCalculator
    ProfitSharingEngine
    LossAllocationPolicy
    DistributionPolicy
    SettlementPolicy

ACCOUNTING CONSEQUENCES
    JournalPostingTransaction
    JournalReversalTransaction

HISTORICAL STATE
    SyirkahCloseSnapshot
    SyirkahCorrectionLineage

VALIDATION
    ResultGate
    PositionGate
    DistributionSettlementGate
    SnapshotIntegrityGate
    JournalReconciliationGate
    CorrectionReconciliationGate

WORKFLOW
    CloseCycle
    SyirkahPeriodCorrection
    SyirkahCorrectionExecution

FINAL AUTHORITY
    AccountingPeriodFinalizationTransaction
```

That separation is important because **tutup buku is not just a UI action or a status flag**. It is the point where the system asserts that a particular financial state has passed all required accounting, Syirkah, settlement, historical, and audit invariants.

The next logical slice is to implement the **actual `AccountingPeriodFinalizationTransaction` infrastructure against the existing framework**, including the transactional repository methods and SQL locking/update sequence, then add the integration tests that prove normal close and correction reclose cannot race or produce two active snapshots.

////
The next layer is the **Sharia policy administration application service**. This makes the policy registry operational: draft → validate → approve → effective → retire, while preserving immutable historical versions.

### 1. Administration lifecycle

```text
CREATE DRAFT
    ↓
VALIDATE
    ↓
SUBMIT / APPROVE
    ↓
EFFECTIVE
    ↓
RETIRE
```

Only one direction is allowed; an effective policy must never be edited in place.

```java
public enum ShariaPolicyVersionStatus {
    DRAFT,
    APPROVED,
    EFFECTIVE,
    RETIRED,
    CANCELLED
}
```

The existing lifecycle methods should remain the aggregate's authority:

```java
public void approve(String approver) {
    if (status != ShariaPolicyVersionStatus.DRAFT) {
        throw new IllegalStateException(
                "Only draft policy versions can be approved");
    }

    if (approver == null || approver.isBlank()) {
        throw new IllegalArgumentException(
                "Approver cannot be blank");
    }

    status = ShariaPolicyVersionStatus.APPROVED;
    approvedBy = approver;
    approvedAt = Instant.now();
}

public void makeEffective() {
    if (status != ShariaPolicyVersionStatus.APPROVED) {
        throw new IllegalStateException(
                "Only approved policy versions can become effective");
    }

    status = ShariaPolicyVersionStatus.EFFECTIVE;
}
```

But the application service must enforce the **cross-record** rules.

---

## 2. Application service

Add:

```java
public interface ShariaPolicyAdministrationService {

    Uni<ShariaPolicyVersion> createDraft(
            CreateShariaPolicyVersionCommand command);

    Uni<ShariaPolicyVersion> approve(
            ApproveShariaPolicyVersionCommand command);

    Uni<ShariaPolicyVersion> makeEffective(
            EffectiveShariaPolicyVersionCommand command);

    Uni<ShariaPolicyVersion> retire(
            RetireShariaPolicyVersionCommand command);

    Uni<ShariaPolicyValidationReport> validate(
            ValidateShariaPolicyVersionCommand command);
}
```

The service should **not contain the actual Syirkah accounting rules**.

Its responsibility is governance.

```text
Administration Service
        ↓
Policy Aggregate
        ↓
Policy Validator
        ↓
Repository
        ↓
Audit / Outbox
```

---

## 3. Create draft

The command should use the established types:

```java
public record CreateShariaPolicyVersionCommand(
        TenantId tenantId,
        LedgerId ledgerId,
        ShariaPolicyId policyId,
        ShariaPolicyType policyType,
        String name,
        String description,
        ShariaPolicyScopeReference scope,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil,
        String implementationKey,
        Map<String, String> ruleConfiguration,
        String sourceReference,
        String requestedBy
) {
}
```

The service determines the next version.

Do **not** allow the caller to freely choose:

```text
version = 999
```

unless there is a specific migration/import use case.

Normal creation should be:

```text
existing versions: 1, 2, 3
                     ↓
new draft:           4
```

and protected by a database uniqueness constraint:

```sql
UNIQUE (
    tenant_id,
    ledger_id,
    policy_id,
    version
)
```

---

## 4. Validation must happen before approval

The validator should combine structural and implementation validation:

```java
public interface ShariaPolicyVersionValidator {

    ShariaPolicyValidationReport validate(
            ShariaPolicyVersion version);
}
```

The report:

```java
public record ShariaPolicyValidationReport(
        boolean valid,
        List<ShariaPolicyValidationFinding> findings
) {
}
```

with:

```java
public record ShariaPolicyValidationFinding(
        String code,
        String message,
        Severity severity
) {
    public enum Severity {
        ERROR,
        WARNING
    }
}
```

Examples:

```text
POLICY-001
implementation does not exist

POLICY-002
implementation does not support policy type

POLICY-003
effective interval overlaps another effective policy

POLICY-004
source reference is required

POLICY-005
invalid rule configuration
```

Warnings should not automatically block approval unless governance configuration says they do.

---

## 5. Approval needs maker/checker separation

Do not allow:

```text
createdBy == approvedBy
```

for controlled production policies.

Use the existing approval concept:

```java
public interface ShariaPolicyApprovalPolicy {

    ApprovalDecision evaluate(
            String operation,
            String maker,
            String checker);
}
```

Then:

```text
CREATE
  actor = Alice

APPROVE
  actor = Bob

Alice != Bob
```

The application service should reject:

```text
maker == checker
```

when the configured policy requires separation.

---

## 6. Effective-date conflict protection

This is one of the most important pieces.

Suppose:

```text
Policy V1
2026-01-01 → 2026-06-30

Policy V2
2026-07-01 → open
```

Valid.

But:

```text
V1
2026-01-01 → 2026-12-31

V2
2026-07-01 → open
```

must be rejected for the same policy scope.

The service asks the repository for overlapping versions:

```java
Uni<List<ShariaPolicyVersion>> findOverlappingEffectiveVersions(
        TenantId tenantId,
        LedgerId ledgerId,
        ShariaPolicyId policyId,
        ShariaPolicyScopeReference scope,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil);
```

Then:

```text
0 overlapping effective versions → OK
1+ → reject
```

This must also be protected at the database level where possible.

Application validation alone is insufficient under concurrent administration requests.

---

## 7. Effective transition should be atomic

Do not do:

```text
UPDATE V1 → RETIRED

then

UPDATE V2 → EFFECTIVE
```

as two unrelated transactions.

A crash between them could leave:

```text
no effective policy
```

or another inconsistent state.

Instead:

```text
DATABASE TRANSACTION

lock relevant policy scope
        ↓
verify V2 is approved
        ↓
verify no conflicting effective version
        ↓
retire/close predecessor if required
        ↓
make V2 effective
        ↓
persist audit event
        ↓
persist outbox event

COMMIT
```

This should be implemented behind the repository/application transaction boundary rather than embedded in the aggregate.

---

# 8. Policy scope becomes important here

Recall:

```text
TENANT
LEDGER
CONTRACT
CONTRACT_VERSION
```

For example:

```text
Tenant policy
    ↓
Ledger policy
    ↓
Contract policy
    ↓
Contract-version policy
```

A new contract-specific policy should **not retire the tenant-wide policy**.

It only overrides it for that scope.

So:

```text
TENANT
  MUDHARABAH_LOSS_V1
       │
       ├── Contract A
       │      └── inherits V1
       │
       └── Contract B
              └── CONTRACT policy V2
```

This is why effective-policy uniqueness must include scope information.

---

## 9. Retiring a policy

Retirement should not mean deletion.

```java
public Uni<ShariaPolicyVersion> retire(
        RetireShariaPolicyVersionCommand command);
```

The aggregate moves:

```text
EFFECTIVE → RETIRED
```

but the record remains available forever for historical reconstruction.

This is essential because:

```text
2026 close
    ↓
policy V1
```

must remain explainable after:

```text
2027
    ↓
policy V2
```

becomes active.

---

# 10. Policy lifecycle events

Reuse the existing accounting event/outbox infrastructure rather than creating another event bus.

Add events such as:

```java
ShariaPolicyCreated
ShariaPolicyApproved
ShariaPolicyEffective
ShariaPolicyRetired
```

Each should carry:

```text
tenantId
ledgerId
policyId
policyVersion
policyType
scope
implementationKey
actor
occurredAt
correlationId
causationId
```

The event is written in the same transaction as the policy state change.

```text
POLICY TRANSACTION
 ├── policy state
 ├── audit evidence
 └── outbox event
          ↓
       COMMIT
```

---

# 11. Audit evidence

The policy lifecycle should use the Syirkah audit evidence model we already established.

For example:

```text
SHARIA_POLICY_CREATED
SHARIA_POLICY_APPROVED
SHARIA_POLICY_EFFECTIVE
SHARIA_POLICY_RETIRED
```

That gives the final audit trail:

```text
Who created policy V3?
Who approved it?
When did it become effective?
Which implementation did it select?
What source supported it?
When was it retired?
```

This is more useful than merely keeping `createdBy` on the current database row.

---

# 12. Important: policy activation does not change historical calculations

Once:

```text
Close A
    ↓
Policy Snapshot V3
```

exists, activating:

```text
Policy V4
```

does not alter Close A.

```text
             Policy V3
                ↓
Close A → frozen snapshot

             Policy V4
                ↓
Future Close B
```

This is the core historical guarantee.

---

# 13. Add a policy preview operation

A useful application feature is:

```java
Uni<ShariaPolicyResolutionPreview> preview(
        ShariaPolicyPreviewRequest request);
```

It answers:

```text
For this tenant
+ ledger
+ contract
+ contract version
+ effective date

which policy would apply?
```

Example:

```text
LOSS_ALLOCATION
→ MUDHARABAH_LOSS_V2
→ CONTRACT_VERSION
→ inherited = false

DISTRIBUTION_ACCOUNTING
→ DEFAULT_DISTRIBUTION_V1
→ TENANT
→ inherited = true

SETTLEMENT_COMPLETION
→ OUTSTANDING_ALLOWED_V1
→ LEDGER
→ inherited = true
```

This is valuable for administrators before they activate a new policy.

---

# 14. The policy administration boundary

At this point the architecture should look like:

```text
                    ┌───────────────────────┐
                    │ Policy Administration │
                    └───────────┬───────────┘
                                │
             ┌──────────────────┼──────────────────┐
             ▼                  ▼                  ▼
          Create              Approve           Retire
             │                  │                  │
             └──────────────────┼──────────────────┘
                                ▼
                    ┌───────────────────────┐
                    │ Policy Version        │
                    │ Aggregate             │
                    └───────────┬───────────┘
                                │
                        validate / govern
                                │
                                ▼
                    ┌───────────────────────┐
                    │ Policy Repository     │
                    └───────────┬───────────┘
                                │
                     ┌──────────┴──────────┐
                     ▼                     ▼
                  Audit                  Outbox
```

And runtime:

```text
Syirkah Close
     ↓
Policy Resolver
     ↓
Policy Snapshot
     ↓
Implementation Registry
     ↓
Existing Syirkah Policies
     ↓
Existing Syirkah Accounting Engine
```

So we are **not creating another accounting engine**. We are adding governance around the existing one.

### Next

The next logical piece is **transactional policy resolution + close integration**: make `SyirkahCloseProcessor` resolve/freeze the complete policy set exactly once, persist `policySnapshotHash`, and guarantee that retries/concurrent close workers cannot produce two different policy snapshots or calculations. That is the point where the policy-management layer becomes safely connected to the existing close engine.

////////////

We should now make the **close calculation boundary transactional and deterministic**. One important finding from the framework file: the current uploaded framework does not contain the exact `SyirkahCloseProcessor` / `SyirkahCloseSnapshot` classes under those names, so these remain extensions around the existing accounting close implementation rather than replacements. syirkah-framework(1)

## 1. Introduce a calculation transaction boundary

The key invariant is:

```text
One close cycle
    ↓
One policy snapshot
    ↓
One calculation
    ↓
One immutable result
```

Not:

```text
worker A → policy V3
worker B → policy V4
```

for the same close cycle.

Use an application-level port:

```java
public interface SyirkahCloseCalculationTransaction {

    Uni<SyirkahCloseCalculationResult> calculate(
            CloseRequest request,
            SyirkahPolicyContext policyContext);
}
```

The infrastructure implementation owns the database transaction.

---

## 2. Policy snapshot is created first

The flow becomes:

```text
CloseRequest
    ↓
Load existing policy snapshot
    │
    ├── exists → reuse
    │
    └── absent
          ↓
      Resolve policies
          ↓
      Validate implementations
          ↓
      Build immutable snapshot
          ↓
      Persist snapshot
```

Then and only then:

```text
Policy Snapshot
      ↓
SyirkahResultCalculator
      ↓
CloseCalculation
```

The calculator never asks the current policy registry again.

---

## 3. Snapshot must identify the exact context

The snapshot should contain at least:

```java
public record SyirkahPolicySnapshot(
        TenantId tenantId,
        LedgerId ledgerId,
        String closeCycleId,
        String calculationId,
        String contractId,
        int contractVersion,
        LocalDate effectiveDate,

        List<PolicySnapshotEntry> policies,

        String snapshotHash
) {
}
```

Each entry:

```java
public record PolicySnapshotEntry(
        ShariaPolicyType policyType,
        ShariaPolicyId policyId,
        int version,
        ShariaPolicyScope scope,
        String implementationKey
) {
}
```

The snapshot therefore records both:

```text
which policy version
```

and:

```text
which executable implementation
```

---

# 4. Never store only the hash

A tempting design is:

```text
policySnapshotHash = "abc123"
```

That is insufficient.

The hash is for integrity verification.

The snapshot must retain the actual policy references:

```text
LOSS_ALLOCATION
    policyId = MUDHARABAH_LOSS
    version = 3
    implementation = MUDHARABAH_LOSS_V2

DISTRIBUTION_ACCOUNTING
    policyId = DISTRIBUTION
    version = 4
    implementation = DISTRIBUTION_ACCOUNTING_V1
```

Then:

```text
snapshotHash
```

proves that those references were not silently modified.

---

# 5. Deterministic snapshot hashing

Canonicalize the entries first.

For example:

```text
tenant
ledger
closeCycle
contract
contractVersion
effectiveDate
policyType
policyId
version
scope
implementationKey
```

sorted by:

```text
policyType
```

Then:

```text
canonical representation
        ↓
SHA-256
        ↓
snapshotHash
```

Never hash a Java `Map.toString()` or an unordered collection.

The hash must be reproducible across JVM instances.

---

# 6. Idempotency belongs in the database

The critical constraint:

```sql
CREATE UNIQUE INDEX ux_syirkah_policy_snapshot_close
ON syirkah_policy_snapshot (
    tenant_id,
    ledger_id,
    close_cycle_id
);
```

And:

```sql
CREATE UNIQUE INDEX ux_syirkah_policy_snapshot_calculation
ON syirkah_policy_snapshot (
    tenant_id,
    ledger_id,
    calculation_id
);
```

Therefore:

```text
Worker A
   ↓
INSERT snapshot
   ↓
success

Worker B
   ↓
INSERT snapshot
   ↓
unique constraint
   ↓
reload existing snapshot
```

Worker B must not calculate using another policy set.

---

# 7. The same rule applies to the calculation

Add:

```sql
CREATE UNIQUE INDEX ux_syirkah_close_calculation
ON syirkah_close_calculation (
    tenant_id,
    ledger_id,
    close_cycle_id
);
```

The close calculation becomes effectively:

```text
CLOSE CYCLE
     │
     ├── exactly one policy snapshot
     │
     └── exactly one calculation
```

Retries return the existing calculation.

They do not create another one.

---

# 8. Calculation should carry the snapshot hash

The existing planned `CloseCalculation` should include:

```java
String policySnapshotHash;
```

and preferably:

```java
String policySnapshotId;
```

So the historical relationship is explicit:

```text
CloseCalculation
    ↓
PolicySnapshot
    ↓
PolicyVersion 1
PolicyVersion 3
PolicyVersion 7
...
```

Do not rely on the hash alone as the foreign-key-like relationship.

---

# 9. Close calculation service

The application service can now look like:

```java
public Uni<CloseCalculationView> calculate(
        CloseRequest request,
        String operator) {

    validateOperator(operator);

    return closeCalculationRepository
            .findByCloseCycle(
                    request.tenantId(),
                    request.ledgerId(),
                    request.closeCycleId())
            .chain(existing -> {
                if (existing.isPresent()) {
                    return Uni.createFrom()
                            .item(toView(existing.get()));
                }

                return calculationTransaction.calculate(
                        request,
                        createPolicyContext(request));
            });
}
```

But the repository lookup is only an optimization.

**The unique database constraint remains the real concurrency guarantee.**

---

# 10. Transaction implementation

The infrastructure implementation should approximately do:

```text
BEGIN TRANSACTION

1. Lock/read close-cycle calculation identity

2. Find existing calculation
      └─ exists → return it

3. Find existing policy snapshot
      └─ exists → use it

4. Otherwise:
      resolve policy set
      validate policy set
      create snapshot
      persist snapshot

5. Load effective Syirkah contract

6. Calculate financial result

7. Apply resolved profit/loss policy

8. Build CloseCalculation

9. Persist CloseCalculation

COMMIT
```

If anything fails:

```text
ROLLBACK
```

---

# 11. Important: contract version must also be frozen

The policy snapshot alone isn't enough.

The calculation must preserve:

```text
contractId
contractVersion
```

because the contract itself can evolve.

So:

```text
CloseCalculation
    ├── contractId
    ├── contractVersion
    ├── policySnapshotId
    └── policySnapshotHash
```

Historical close reconstruction therefore becomes:

```text
Close
 ↓
Contract Version
 ↓
Policy Snapshot
 ↓
Financial Result
 ↓
Allocation
```

---

# 12. Do not resolve policy during allocation later

This is a subtle but important rule.

Bad:

```text
calculate()
   ↓
resolve policy V3

prepareDistribution()
   ↓
resolve policy again
   ↓
policy V4
```

That can produce:

```text
result calculated under V3
distribution prepared under V4
```

Instead:

```text
calculate()
   ↓
Policy Snapshot V3
   ↓
CloseCalculation
```

Then:

```text
prepareDistribution()
   ↓
load CloseCalculation
   ↓
load PolicySnapshot V3
   ↓
resolve executable implementations from V3
```

No current-policy lookup.

---

# 13. Runtime implementation registry is still allowed

The snapshot says:

```text
implementationKey = MUDHARABAH_LOSS_V2
```

At replay time:

```text
snapshot
   ↓
implementationKey
   ↓
ImplementationRegistry
   ↓
MudharabahLossAllocationPolicy
```

This means the registry itself becomes part of your deployment compatibility contract.

Therefore:

> Never remove or silently change the semantics of an implementation key that is referenced by historical snapshots.

If implementation semantics must change:

```text
MUDHARABAH_LOSS_V2
```

becomes:

```text
MUDHARABAH_LOSS_V3
```

not a modified V2.

This is effectively **semantic versioning for accounting policy implementations**.

---

# 14. Add implementation compatibility

A useful registry method:

```java
public interface SyirkahPolicyImplementationRegistry {

    boolean supports(
            ShariaPolicyType policyType,
            String implementationKey);

    LossAllocationPolicy resolveLossAllocation(
            String implementationKey);

    // ...
}
```

Before snapshot persistence:

```text
policy version
      ↓
implementation exists?
      ↓
supports policy type?
      ↓
YES
      ↓
snapshot
```

This prevents bad configuration from becoming historical data.

---

# 15. Policy snapshot should be immutable

No:

```java
snapshot.changePolicy(...)
```

No:

```java
repository.updateSnapshot(...)
```

Only:

```text
CREATE
READ
```

If the policy was wrong:

```text
wrong snapshot
    ↓
do not mutate
    ↓
void/reverse according to close governance
    ↓
new controlled calculation
```

This is especially important after accounting entries have been posted.

---

# 16. Close task integration

Now connect this to the existing `CloseCycle`.

For Syirkah:

```text
TASK_SYIRKAH_RESULT
        ↓
calculate()
        ↓
policy snapshot frozen
        ↓
CloseCalculation persisted
        ↓
complete task
```

Then:

```text
TASK_SYIRKAH_ALLOCATION
        ↓
load existing calculation
        ↓
load frozen policy snapshot
        ↓
prepare allocation/distribution
```

Then:

```text
TASK_SYIRKAH_DISTRIBUTION
        ↓
approval
        ↓
posting
        ↓
GL journal POSTED
        ↓
complete task
```

The close task itself should never contain the policy logic.

---

# 17. Failed calculation must not complete the task

For example:

```text
Policy missing
    ↓
calculate fails
    ↓
TASK_SYIRKAH_RESULT remains incomplete
```

Likewise:

```text
Policy implementation unavailable
    ↓
calculation fails
    ↓
period cannot proceed to allocation
```

This is preferable to silently using a default.

---

# 18. What happens when policy resolution races with policy retirement?

The calculation transaction should resolve an **approved/effective policy** according to the effective date.

If policy administration retires it concurrently:

```text
Calculation transaction
        +
Policy administration transaction
```

database isolation/locking must ensure one coherent outcome.

The important invariant is:

```text
Either:
    calculation sees old policy and freezes it

or:
    calculation sees new policy

but never:
    partially old + partially new
```

This is another reason to resolve the entire policy set as one unit.

---

# 19. Retry semantics

Suppose the application crashes:

```text
Policy snapshot persisted
Calculation transaction crashes
```

Retry:

```text
find snapshot
    ↓
reuse exact snapshot
    ↓
calculate
```

Suppose:

```text
Snapshot + calculation committed
Application crashes before response
```

Retry:

```text
find calculation
    ↓
return existing calculation
```

No duplicate.

---

# 20. The complete close architecture is now

```text
                    CLOSE REQUEST
                         │
                         ▼
                 ┌───────────────┐
                 │ Close Cycle   │
                 └───────┬───────┘
                         │
                         ▼
              ┌─────────────────────┐
              │ Policy Resolution   │
              └──────────┬──────────┘
                         │
                         ▼
              ┌─────────────────────┐
              │ Frozen Policy       │
              │ Snapshot            │
              └──────────┬──────────┘
                         │
                         ▼
              ┌─────────────────────┐
              │ Financial Result    │
              │ + Allocation        │
              └──────────┬──────────┘
                         │
                         ▼
              ┌─────────────────────┐
              │ Close Calculation   │
              └──────────┬──────────┘
                         │
                         ▼
              SYIRKAH_RESULT COMPLETE
                         │
                         ▼
              Distribution Preparation
                         │
                         ▼
              SYIRKAH_ALLOCATION COMPLETE
                         │
                         ▼
                   Maker / Checker
                         │
                         ▼
                    GL Posting
                         │
                         ▼
              SYIRKAH_DISTRIBUTION COMPLETE
                         │
                         ▼
                    LOCK PERIOD
```

### The critical invariant

```text
                         ┌───────────────┐
                         │ Close Cycle   │
                         └───────┬───────┘
                                 │
                 ┌───────────────┴───────────────┐
                 ▼                               ▼
          Policy Snapshot                  Calculation
                 │                               │
                 └───────────────┬───────────────┘
                                 ▼
                         Immutable historical
                              decision
```

This gives us the foundation for **reproducible Syirkah accounting**: a close performed under one approved Sharia policy remains explainable even after policies, contracts, implementations, or application nodes change.

**Next layer:** build the **close snapshot itself**—the immutable partner-position snapshot that freezes capital, current account, profit/loss allocation, responsibilities, reconciliation, and policy references. That becomes the real foundation for `tutup buku`, partner statements, transition/partner exit, and eventual termination/liquidation.

/////////////


## 1. `SyirkahCloseSnapshot` becomes the historical source for `tutup buku`

The important distinction is:

```text
PartnerAccount
    = current/materialized operational state

SyirkahCloseSnapshot
    = immutable historical state at close
```

So after a period is locked:

```text
2026-03-31
    ↓
Syirkah Close Snapshot
    ↓
frozen forever
```

Later changes to partner accounts must not change the March snapshot.

---

# 2. Snapshot aggregate

I would make the snapshot immutable:

```java
public final class SyirkahCloseSnapshot {

    private final String snapshotId;
    private final TenantId tenantId;
    private final LedgerId ledgerId;

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

    private final Instant frozenAt;
    private final String frozenBy;
}
```

Do **not** expose setters.

---

# 3. Partner position is the core of the snapshot

Use the roll-forward model we established:

```java
public record PartnerClosePositionSnapshot(
        String partyId,

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

        BigDecimal cashSettlements
) {
}
```

The equations must be explicitly validated:

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
+ profitAllocations
- lossAllocations
- withdrawals
```

Cash settlement remains separate.

---

# 4. Do not calculate opening position from the mutable partner account

This is important.

Bad:

```text
PartnerAccount
     ↓
current balance
     ↓
pretend this is opening balance
```

Correct:

```text
Previous locked snapshot
          ↓
Opening position
          ↓
Current period movements
          ↓
New close snapshot
```

For the first period:

```text
Contract opening position
          ↓
Current period movements
          ↓
First close snapshot
```

Therefore:

```java
public interface SyirkahOpeningPositionProvider {

    Uni<Optional<PartnerOpeningPosition>> findOpeningPosition(
            TenantId tenantId,
            LedgerId ledgerId,
            String contractId,
            String partyId);
}
```

---

# 5. Partner movements become the calculation input

The snapshot builder should aggregate immutable movements:

```text
PartnerAccountMovement
        │
        ├── CAPITAL_CONTRIBUTION
        ├── CAPITAL_RETURN
        ├── CAPITAL_IMPAIRMENT
        ├── PROFIT_ALLOCATION
        ├── LOSS_ALLOCATION
        ├── PARTNER_WITHDRAWAL
        └── CASH_SETTLEMENT
```

into:

```text
PartnerClosePositionSnapshot
```

This preserves the distinction between business movements and accounting journals.

---

# 6. Profit/loss allocation must come from the frozen calculation

Do not independently recalculate the profit allocation while building the snapshot.

Use:

```text
CloseCalculation
    ↓
result
    ↓
allocation
```

Then reconcile that against movements:

```text
CloseCalculation
       │
       ▼
Expected allocations
       │
       ▼
PartnerAccountMovement
       │
       ▼
Actual posted movements
```

If they disagree:

```text
SNAPSHOT CREATION FAILS
```

rather than silently correcting the difference.

---

# 7. Allocation snapshot

Use an explicit model:

```java
public record PartnerAllocationSnapshot(
        String partyId,
        AllocationType type,
        BigDecimal amount,
        String sourceCalculationId
) {
    public enum AllocationType {
        PROFIT,
        LOSS
    }
}
```

This prevents the historical snapshot from depending on the current implementation of `ProfitSharingResult`.

---

# 8. Loss responsibility must be separate

For loss cases:

```text
Business loss
    │
    ├── normal business loss
    │       ↓
    │   partner loss allocation
    │
    └── responsibility
            ↓
       responsible party
            ↓
       recovery obligation
```

The snapshot should preserve the determination:

```java
public record LossResponsibilitySnapshot(
        String responsibilityId,
        String partyId,
        LossCause cause,
        BigDecimal amount,
        LocalDate determinationDate,
        String determinedBy,
        String evidenceReference
) {
}
```

This is important for historical `tutup buku`: a later recovery payment must not rewrite what was determined at closing.

---

# 9. Reconciliation is mandatory

The snapshot cannot simply say:

```text
balanced = true
```

without showing what was reconciled.

Use:

```java
public record SyirkahPositionReconciliation(
        boolean balanced,

        BigDecimal totalProfitAllocated,
        BigDecimal totalLossAllocated,

        BigDecimal totalCapitalContributed,
        BigDecimal totalCapitalReturned,
        BigDecimal totalCapitalImpaired,

        BigDecimal capitalRollForwardDifference,
        BigDecimal currentAccountRollForwardDifference,

        List<String> errors
) {
}
```

At minimum:

```text
sum(partner profit allocations)
    ==
close calculation profit allocation
```

```text
sum(partner loss allocations)
    ==
close calculation loss allocation
```

and:

```text
closing capital
    ==
opening capital
    + contributions
    - returns
    - impairments
```

---

# 10. Snapshot creation service

Introduce a dedicated application service:

```java
public interface SyirkahCloseSnapshotService {

    Uni<SyirkahCloseSnapshot> create(
            CloseRequest request,
            String calculationId,
            String operator);

    Uni<SyirkahCloseSnapshot> findByCloseCycle(
            TenantId tenantId,
            LedgerId ledgerId,
            String closeCycleId);
}
```

The implementation should follow:

```text
load existing snapshot
       │
       ├── exists → return it
       │
       └── absent
             ↓
       load close calculation
             ↓
       load contract/version
             ↓
       load policy snapshot
             ↓
       load opening positions
             ↓
       load period movements
             ↓
       aggregate partner positions
             ↓
       reconcile
             ↓
       freeze snapshot
             ↓
       persist
```

---

# 11. Snapshot must be created only after calculation

Therefore:

```text
TASK_SYIRKAH_RESULT
        ↓
CloseCalculation
        ↓
Snapshot
```

Not:

```text
Snapshot
   ↓
calculate result
```

because the snapshot is supposed to freeze the **determined result**, not merely inputs.

---

# 12. Freeze operation

The aggregate should have one transition:

```java
public void freeze(String operator) {
    if (operator == null || operator.isBlank()) {
        throw new IllegalArgumentException(
                "Operator cannot be blank");
    }

    if (!reconciliation.balanced()) {
        throw new IllegalStateException(
                "Cannot freeze an unreconciled Syirkah close snapshot");
    }

    this.frozenAt = Instant.now();
    this.frozenBy = operator;
}
```

But preferably, construct the snapshot as an immutable frozen object after validation rather than maintaining a mutable aggregate with a boolean.

Conceptually:

```text
CALCULATING
     ↓
RECONCILED
     ↓
FROZEN
```

Once frozen:

```text
no update
no delete
no recalculation in place
```

---

# 13. Database structure

A practical split is:

```text
syirkah_close_snapshot
syirkah_close_partner_position
syirkah_close_allocation
syirkah_close_loss_responsibility
```

with:

```sql
UNIQUE (
    tenant_id,
    ledger_id,
    close_cycle_id
)
```

and:

```sql
UNIQUE (
    tenant_id,
    ledger_id,
    calculation_id
)
```

The parent snapshot is immutable.

Child rows should also be immutable.

---

# 14. Snapshot hash

The snapshot should receive its own hash, separate from the policy snapshot hash.

```text
Policy snapshot hash
    = integrity of policy references

Close snapshot hash
    = integrity of frozen financial position
```

So:

```text
SyirkahCloseSnapshot
    ├── policySnapshotHash
    └── snapshotHash
```

Canonical snapshot hash should include:

```text
tenant
ledger
closeCycle
calculation
contract/version
period
financial result
partner positions
allocations
responsibilities
reconciliation
policySnapshotHash
```

This creates a strong historical fingerprint.

---

# 15. `tutup buku` now has a real frozen state

The close lifecycle becomes:

```text
OPEN
  ↓
CALCULATE
  ↓
RESULT DETERMINED
  ↓
ALLOCATION DETERMINED
  ↓
SNAPSHOT RECONCILED
  ↓
SNAPSHOT FROZEN
  ↓
DISTRIBUTION APPROVED
  ↓
DISTRIBUTION POSTED
  ↓
CLOSE APPROVED
  ↓
PERIOD LOCKED
```

The snapshot should be frozen **before** the period becomes locked.

---

# 16. Why snapshot before distribution posting?

Because the snapshot answers:

> "What was the determined financial position at closing?"

while distribution answers:

> "What obligations were subsequently created and posted?"

Those are different facts.

For example:

```text
Net profit                 100m
Partner A allocation       60m
Partner B allocation       40m

Close snapshot
    ↓
60m / 40m frozen

Later:
Partner A paid             20m
Partner B paid              0m
```

The snapshot remains:

```text
A allocation = 60m
B allocation = 40m
```

Settlement status is separate.

---

# 17. This gives us clean historical reporting

For a locked period:

```text
Report request
      ↓
Locked snapshot
      ↓
Historical statement
```

For an open period:

```text
Report request
      ↓
Live read models
```

Therefore:

```text
OPEN PERIOD
    = dynamic

LOCKED PERIOD
    = snapshot
```

This is much safer than rebuilding an old period from today's mutable balances.

---

# 18. Partner transition now becomes straightforward

When a partner exits:

```text
Old Contract
      ↓
Close Cycle
      ↓
Frozen Close Snapshot
      ↓
Partner position known
      ↓
Settlement / capital return
      ↓
Old contract terminated
```

The exit position comes from:

```text
Frozen Snapshot
```

not from a mutable current balance that may have changed after the close.

---

# 19. Full termination uses the same foundation

For termination:

```text
Last locked close snapshot
          ↓
Opening liquidation position
          ↓
Asset realization
          ↓
Liability settlement
          ↓
Final liquidation result
```

So the snapshot becomes the bridge between ordinary `tutup buku` and final liquidation.

---

# 20. Final architecture

We now have three distinct historical layers:

```text
                 SHARIA POLICY
                       │
                       ▼
              Policy Snapshot
                       │
                       ▼
                Close Calculation
                       │
                       ▼
              Close Position
                 Snapshot
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
     Distribution   Settlement   Transition
          │                         │
          │                         ▼
          │                    Next Contract
          │
          ▼
     Accounting
```

And the historical chain is:

```text
Tenant / Ledger
      ↓
Contract Version
      ↓
Policy Snapshot
      ↓
Financial Result
      ↓
Partner Allocation
      ↓
Partner Position Snapshot
      ↓
Distribution Obligation
      ↓
Journal Entry
      ↓
Cash Settlement
```

That chain is what ultimately makes Syirkah `tutup buku` auditable and reproducible.

### Next

The next piece should be **the actual `SyirkahCloseSnapshotBuilder` and movement aggregation boundary**: define exactly how opening positions + `PartnerAccountMovement` records + `CloseCalculation` become each partner's frozen closing position, including handling missing movements, duplicate movements, pending accounting movements, rounding, and reconciliation failures.


/////////

Next is the **`SyirkahCloseSnapshotBuilder` and movement aggregation boundary**.

The key design is to make the snapshot a deterministic function of immutable inputs:

```text
Opening Position
       +
Period Partner Movements
       +
Frozen Close Calculation
       +
Frozen Policy Snapshot
       ↓
SyirkahCloseSnapshot
```

## 1. Introduce an explicit builder port

```java
public interface SyirkahCloseSnapshotBuilder {

    Uni<SyirkahCloseSnapshot> build(
            SyirkahCloseSnapshotBuildRequest request);
}
```

Request:

```java
public record SyirkahCloseSnapshotBuildRequest(
        TenantId tenantId,
        LedgerId ledgerId,
        String closeCycleId,
        String calculationId,
        String contractId,
        int contractVersion,
        LocalDate periodStart,
        LocalDate periodEnd,
        SyirkahPolicySnapshot policySnapshot,
        CloseCalculation calculation,
        String requestedBy
) {
    public SyirkahCloseSnapshotBuildRequest {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(ledgerId);
        Objects.requireNonNull(policySnapshot);
        Objects.requireNonNull(calculation);
        Objects.requireNonNull(periodStart);
        Objects.requireNonNull(periodEnd);

        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException(
                    "Period end cannot be before period start");
        }
    }
}
```

The builder does **not** resolve policies or recalculate the financial result.

Those have already been frozen.

---

# 2. Separate movement loading from aggregation

Use a dedicated provider:

```java
public interface SyirkahPartnerMovementProvider {

    Uni<List<PartnerAccountMovement>> findMovements(
            TenantId tenantId,
            LedgerId ledgerId,
            String contractId,
            LocalDate periodStart,
            LocalDate periodEnd);
}
```

And opening position:

```java
public interface SyirkahOpeningPositionProvider {

    Uni<Optional<PartnerOpeningPosition>> findOpeningPosition(
            TenantId tenantId,
            LedgerId ledgerId,
            String contractId,
            String partyId);
}
```

This separation matters because the builder should not know whether movements came from:

- PostgreSQL
- another database
- an event projection
- a future microservice
- a reconstructed ledger

---

# 3. First validate movement integrity

Before aggregation, validate:

```text
tenant
ledger
contract
party
movement type
amount
currency
effective date
source
```

A movement belonging to another contract must never silently enter the snapshot.

For example:

```java
if (!request.contractId().equals(movement.contractId())) {
    throw new IllegalStateException(
            "Partner movement belongs to another Syirkah contract");
}
```

Likewise for tenant and ledger.

---

# 4. Period boundary must be explicit

Use:

```text
periodStart <= movement.effectiveDate <= periodEnd
```

for a closed accounting period.

Do not use timestamps from journal posting as a substitute for the Syirkah effective date.

The domain movement already has:

```text
effectiveDate
```

and that is what the Syirkah position aggregation should use.

---

# 5. Pending accounting movements must not enter a frozen position

We previously introduced:

```java
public enum PartnerMovementStatus {
    PENDING_ACCOUNTING,
    POSTED,
    CANCELLED
}
```

The snapshot should aggregate only:

```text
POSTED
```

Therefore:

```text
PENDING_ACCOUNTING
        ↓
not included
        ↓
but reported as blocking error
```

Do **not** simply ignore pending movements.

For example:

```text
Capital contribution = 100m
Movement = PENDING_ACCOUNTING
```

If we ignore it and close successfully, the snapshot could falsely claim:

```text
capital contribution = 0
```

Instead:

```text
PENDING_ACCOUNTING movement detected
        ↓
snapshot cannot be frozen
```

---

# 6. Cancelled movements are excluded

Cancelled movements are historical operational records, but they don't contribute to the position:

```text
POSTED       → include
PENDING      → block
CANCELLED    → exclude
```

This gives us a clean rule.

---

# 7. Aggregate by party

The builder can internally use:

```java
Map<String, PartnerPositionAccumulator>
```

where:

```java
final class PartnerPositionAccumulator {

    BigDecimal capitalContributions = ZERO;
    BigDecimal capitalReturns = ZERO;
    BigDecimal capitalImpairments = ZERO;

    BigDecimal profitAllocations = ZERO;
    BigDecimal lossAllocations = ZERO;
    BigDecimal withdrawals = ZERO;

    BigDecimal cashSettlements = ZERO;
}
```

But this accumulator is **not** a domain aggregate.

It is merely a calculation helper.

---

# 8. Movement semantics

The mapping should be explicit:

```text
CAPITAL_CONTRIBUTION
    → capitalContributions

CAPITAL_RETURN
    → capitalReturns

CAPITAL_IMPAIRMENT
    → capitalImpairments

PROFIT_ALLOCATION
    → profitAllocations

LOSS_ALLOCATION
    → lossAllocations

PARTNER_WITHDRAWAL
    → withdrawals

CASH_SETTLEMENT
    → cashSettlements
```

No generic:

```java
switch (movement.type()) {
    ...
}
```

should silently introduce a new type without forcing the compiler/tests to address it.

---

# 9. Opening position + movement totals

For every partner:

```text
closingCapital =
    openingCapital
    + contributions
    - returns
    - impairments
```

and:

```text
closingCurrentAccount =
    openingCurrentAccount
    + profitAllocations
    - lossAllocations
    - withdrawals
```

Cash settlement remains:

```text
cashSettlements
```

and is **not** added to or subtracted from current account automatically.

---

# 10. Important distinction: allocation versus settlement

Example:

```text
Profit allocation       10,000,000
Cash settlement          4,000,000
Outstanding              6,000,000
```

The snapshot must contain:

```text
profitAllocations = 10,000,000
cashSettlements   = 4,000,000
```

not:

```text
profitAllocations = 4,000,000
```

Otherwise the snapshot would incorrectly represent the contractual/business result.

---

# 11. Reconcile allocation against the close calculation

For profit:

```text
sum(partner profit allocations)
    ==
CloseCalculation.totalProfitAllocated
```

For loss:

```text
sum(partner loss allocations)
    ==
CloseCalculation.totalLossAllocated
```

Use the same monetary precision/rounding policy as the underlying calculation.

Do not introduce an arbitrary:

```java
difference.abs().compareTo(new BigDecimal("0.01"))
```

unless the accounting currency policy explicitly establishes that tolerance.

---

# 12. Every partner in the contract needs a position

The source of partners should be the **effective contract version**, not merely the movement table.

This catches an important problem:

```text
Contract:
    A
    B
    C

Movements:
    A
    B
```

We still need:

```text
C
```

in the close snapshot:

```text
C:
    openingCapital = ...
    contributions = 0
    ...
```

Otherwise the snapshot is not a complete representation of the contract.

---

# 13. Conversely, unknown parties must fail

If movement says:

```text
partyId = D
```

but contract version contains:

```text
A
B
C
```

then:

```text
UNKNOWN_PARTNER_MOVEMENT
```

must block snapshot creation.

Do not automatically create a fourth partner position.

That would hide contract/master-data corruption.

---

# 14. Opening position must exist for every required partner

For an existing contract:

```text
Partner A → opening position exists
Partner B → opening position exists
```

If one is missing, we need an explicit policy.

I recommend:

```text
Existing contract
    +
missing opening position
    ↓
BLOCK
```

For a genuinely new contract, an explicit initial opening position may be zero.

Don't infer zero merely because the provider returned nothing.

---

# 15. Initial contract versus subsequent period

The provider can distinguish:

```text
No previous snapshot
        ↓
contract inception
        ↓
explicit initial opening position
```

versus:

```text
Previous locked snapshot exists
        ↓
opening position = previous closing position
```

This prevents accidental resets to zero.

---

# 16. Verify continuity

For periods after the first:

```text
current opening capital
    ==
previous closing capital
```

and:

```text
current opening current account
    ==
previous closing current account
```

If not:

```text
CLOSE_POSITION_CONTINUITY_FAILURE
```

and the snapshot cannot freeze.

This catches:

- missing previous snapshot
- duplicate snapshot
- wrong contract version
- manual data corruption
- incomplete transition.

---

# 17. Contract version changes require a transition boundary

Do not permit:

```text
Contract V1 closing position
       ↓
Contract V2 opening position
```

to happen implicitly.

It must pass through the transition process we designed:

```text
V1
 ↓
locked snapshot
 ↓
settlement / transition
 ↓
V1 terminated or superseded
 ↓
V2 opening position
```

The snapshot builder should therefore reject an unexpected contract-version discontinuity.

---

# 18. Build the position

Conceptually:

```java
PartnerClosePositionSnapshot buildPosition(
        PartnerOpeningPosition opening,
        PartnerPositionAccumulator movements) {

    BigDecimal closingCapital =
            opening.openingCapital()
                    .add(movements.capitalContributions)
                    .subtract(movements.capitalReturns)
                    .subtract(movements.capitalImpairments);

    BigDecimal closingCurrentAccount =
            opening.openingCurrentAccount()
                    .add(movements.profitAllocations)
                    .subtract(movements.lossAllocations)
                    .subtract(movements.withdrawals);

    return new PartnerClosePositionSnapshot(
            opening.partyId(),
            opening.openingCapital(),
            movements.capitalContributions,
            movements.capitalReturns,
            movements.capitalImpairments,
            closingCapital,
            opening.openingCurrentAccount(),
            movements.profitAllocations,
            movements.lossAllocations,
            movements.withdrawals,
            closingCurrentAccount,
            movements.cashSettlements
    );
}
```

This is deterministic.

---

# 19. Reconcile totals at contract level

After all partners are built:

```text
sum opening capital
sum contributions
sum returns
sum impairments
sum closing capital
```

Then:

```text
total closing capital
=
total opening capital
+ total contributions
- total returns
- total impairments
```

Likewise for current accounts.

This is separate from GL trial balance reconciliation.

We now have two levels:

```text
Accounting reconciliation
    → journal / ledger

Syirkah reconciliation
    → partner economic position
```

Both must succeed.

---

# 20. Reconcile against `CloseCalculation`

The final check should include:

```text
financial result
        ↕
partner allocations
        ↕
partner movements
        ↕
partner positions
```

For example:

```text
CloseCalculation
    netProfit = 100m

Partner allocations:
    A = 60m
    B = 40m

Movements:
    A PROFIT_ALLOCATION = 60m
    B PROFIT_ALLOCATION = 40m

Snapshot:
    A profit = 60m
    B profit = 40m
```

Everything agrees.

---

# 21. What if accounting posting exists but movement does not?

This should be an explicit reconciliation failure.

Example:

```text
Distribution journal
    = 100m

Partner movements
    = 80m
```

Do not make the snapshot 80m and pretend the remaining 20m doesn't exist.

Instead:

```text
ACCOUNTING_SYIRKAH_MOVEMENT_MISMATCH
```

This is precisely why we separated:

```text
Business movement
        ↓
Accounting policy
        ↓
Journal
```

and require reconciliation between them.

---

# 22. What if movement exists but journal is missing?

Same principle:

```text
Partner allocation movement
    = 100m

Accounting journal
    = 0
```

For a close that requires accounting posting:

```text
ACCOUNTING_POSTING_MISSING
```

The exact point at which this blocks can be controlled by close policy, but a locked snapshot should not silently claim the accounting side is complete.

---

# 23. Snapshot builder result

I would make the builder return a result containing diagnostics:

```java
public record SyirkahCloseSnapshotBuildResult(
        SyirkahCloseSnapshot snapshot,
        SyirkahPositionReconciliation reconciliation
) {
}
```

However, if reconciliation fails, preferably don't return a normal snapshot.

Use:

```text
success
    → snapshot

failure
    → structured reconciliation findings
```

This makes application/API behavior much cleaner.

---

# 24. Repository contract

The snapshot repository should expose immutable operations:

```java
public interface SyirkahCloseSnapshotRepository {

    Uni<Optional<SyirkahCloseSnapshot>> findByCloseCycle(
            TenantId tenantId,
            LedgerId ledgerId,
            String closeCycleId);

    Uni<Optional<SyirkahCloseSnapshot>> findByCalculation(
            TenantId tenantId,
            LedgerId ledgerId,
            String calculationId);

    Uni<Void> save(
            SyirkahCloseSnapshot snapshot);
}
```

No:

```java
update(snapshot)
```

and no:

```java
delete(snapshot)
```

for normal operation.

---

# 25. Snapshot idempotency

The service should use:

```text
tenant + ledger + closeCycle
```

as the primary identity.

Flow:

```text
createSnapshot()
      ↓
findByCloseCycle()
      │
      ├── found → return existing
      │
      └── absent
            ↓
        build
            ↓
        INSERT
```

Database uniqueness handles concurrent calls.

---

# 26. Snapshot hash should be generated after reconciliation

The sequence is:

```text
Load inputs
   ↓
Aggregate
   ↓
Calculate closing positions
   ↓
Reconcile
   ↓
Verify balanced
   ↓
Canonicalize snapshot
   ↓
SHA-256
   ↓
Freeze
   ↓
Persist
```

Never hash an unreconciled state and then call it final.

---

# 27. This gives us the actual `tutup buku` artifact

The period now has:

```text
CloseCalculation
    = what happened financially

SyirkahCloseSnapshot
    = what each partner's frozen position became

DistributionObligation
    = what was owed/distributed

SettlementObligation
    = what was actually payable/paid
```

These should remain separate.

---

# 28. The resulting model

```text
                 CLOSE CYCLE
                     │
                     ▼
             Close Calculation
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
 Policy Snapshot        Contract Version
          │                     │
          └──────────┬──────────┘
                     ▼
          Syirkah Snapshot Builder
                     │
          ┌──────────┼──────────┐
          ▼          ▼          ▼
      Opening     Movements   Allocation
      Positions                Result
          │          │          │
          └──────────┼──────────┘
                     ▼
             Reconciliation
                     │
                balanced?
                 /      \
               no        yes
               │          │
               ▼          ▼
             BLOCK      FREEZE
                            │
                            ▼
                  SyirkahCloseSnapshot
```

This is the correct foundation for the next layer.

**Next:** connect this snapshot to **partner statements and period reporting**, including the rule that open periods use live projections while locked periods must read from the frozen snapshot. After that, we can wire the snapshot into **partner entry/exit and contract transition**, where the frozen position becomes the predecessor state for the next akad.

///////////////

Next, we connect the frozen close snapshot to **Syirkah reporting and partner statements**. The important rule is that reporting must switch source depending on whether the period is still open or already locked.

## 1. Establish the reporting source rule

```text
OPEN PERIOD
    ↓
Live read models / movements
    ↓
Dynamic statement

LOCKED PERIOD
    ↓
SyirkahCloseSnapshot
    ↓
Immutable statement
```

Never rebuild a locked historical statement from today's `PartnerAccount`.

---

## 2. Introduce the period statement provider

```java
public interface SyirkahPeriodStatementProvider {

    Uni<SyirkahPeriodStatement> build(
            TenantId tenantId,
            LedgerId ledgerId,
            String contractId,
            LocalDate periodStart,
            LocalDate periodEnd);
}
```

Internally:

```text
periodEnd
   ↓
is period locked?
   ├── yes → resolve frozen snapshot
   └── no  → build live projection
```

---

## 3. Snapshot resolver

```java
public interface SyirkahPeriodSnapshotResolver {

    Uni<Optional<SyirkahCloseSnapshot>> findLockedSnapshot(
            TenantId tenantId,
            LedgerId ledgerId,
            String contractId,
            LocalDate periodEnd);
}
```

Do not search merely by date.

The identity needs:

```text
tenant
ledger
contract
period
```

because different contracts can have different periods.

---

## 4. Partner statement

The partner statement should be a reporting projection:

```java
public record SyirkahPartnerStatement(
        TenantId tenantId,
        LedgerId ledgerId,
        String contractId,
        int contractVersion,
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
        BigDecimal outstandingSettlement
) {
}
```

The statement deliberately keeps:

```text
capital
current account
cash settlement
```

separate.

---

# 5. Statement calculation from snapshot

For a locked period:

```text
SyirkahCloseSnapshot
       ↓
PartnerClosePositionSnapshot
       ↓
SyirkahPartnerStatement
```

There should be almost no business calculation here.

The statement is primarily a projection.

That is important because the snapshot is already reconciled.

---

# 6. Open-period reporting

For an open period, the provider can use:

```text
opening snapshot
+
posted PartnerAccountMovement
+
current distribution/settlement state
```

But it should clearly be considered:

```text
LIVE / UNLOCKED
```

rather than historical final truth.

A reporting response can expose:

```java
public enum StatementState {
    LIVE,
    LOCKED
}
```

---

# 7. Add statement provenance

I recommend putting provenance directly into the reporting model:

```java
public record SyirkahStatementProvenance(
        StatementState state,
        String snapshotId,
        String calculationId,
        String policySnapshotHash
) {
}
```

Then a user viewing a report can determine:

```text
LOCKED
Snapshot: SNAP-2026-03-31-001
Calculation: CALC-...
Policy: SHA-256(...)
```

This is much better for audit than a generic "generated at" timestamp.

---

# 8. Capital statement

Create a dedicated capital statement:

```java
public record SyirkahCapitalStatement(
        TenantId tenantId,
        LedgerId ledgerId,
        String contractId,
        int contractVersion,
        LocalDate periodStart,
        LocalDate periodEnd,

        BigDecimal openingCapital,
        BigDecimal capitalContributions,
        BigDecimal capitalReturns,
        BigDecimal capitalImpairments,
        BigDecimal closingCapital
) {
}
```

The invariant:

```text
closingCapital
=
openingCapital
+ contributions
- returns
- impairments
```

This should be validated when constructing the report.

---

# 9. Profit distribution statement

Separate profit allocation from payment:

```java
public record SyirkahProfitDistributionStatement(
        TenantId tenantId,
        LedgerId ledgerId,
        String contractId,
        LocalDate periodStart,
        LocalDate periodEnd,

        BigDecimal totalProfit,
        BigDecimal totalAllocated,
        BigDecimal totalSettled,
        BigDecimal totalOutstanding
) {
}
```

Invariant:

```text
totalOutstanding
=
totalAllocated
- totalSettled
```

So:

```text
Profit
  100m
    ↓
Allocated
  100m
    ↓
Settled
   40m
    ↓
Outstanding
   60m
```

The statement never changes the allocation merely because settlement is incomplete.

---

# 10. Loss statement

Loss reporting needs to preserve the distinction we designed earlier:

```text
LOSS
├── normal business loss
│     └── partner loss allocation
│
└── responsibility
      └── recovery obligation
```

Therefore:

```java
public record SyirkahLossStatement(
        TenantId tenantId,
        LedgerId ledgerId,
        String contractId,
        LocalDate periodStart,
        LocalDate periodEnd,

        BigDecimal totalLoss,
        BigDecimal totalLossAllocated,
        BigDecimal totalLossResponsibility,
        BigDecimal totalRecovered,
        BigDecimal totalOutstandingRecovery
) {
}
```

Do not report recovery as if it were part of the original loss allocation.

---

# 11. Settlement statement

Settlement is its own report:

```java
public record SyirkahSettlementStatement(
        TenantId tenantId,
        LedgerId ledgerId,
        String contractId,
        LocalDate periodStart,
        LocalDate periodEnd,

        BigDecimal totalObligated,
        BigDecimal totalSettled,
        BigDecimal totalOutstanding
) {
}
```

Again:

```text
allocation ≠ obligation ≠ settlement
```

This distinction will become especially important for partner exits and liquidation.

---

# 12. Consolidated partner report

For a contract:

```text
                    Syirkah Contract
                           │
             ┌─────────────┼─────────────┐
             ▼             ▼             ▼
          Partner A      Partner B      Partner C
             │             │             │
             ▼             ▼             ▼
        Statement      Statement      Statement
             │             │             │
             └─────────────┼─────────────┘
                           ▼
                Consolidated Statement
```

```java
public record SyirkahPartnerConsolidatedStatement(
        TenantId tenantId,
        LedgerId ledgerId,
        String contractId,
        int contractVersion,

        LocalDate periodStart,
        LocalDate periodEnd,

        List<SyirkahPartnerStatement> partners,

        BigDecimal totalOpeningCapital,
        BigDecimal totalContributions,
        BigDecimal totalCapitalReturns,
        BigDecimal totalCapitalImpairments,
        BigDecimal totalClosingCapital,

        BigDecimal totalProfitAllocations,
        BigDecimal totalLossAllocations
) {
}
```

---

# 13. Consolidation invariants

The consolidated report must verify:

```text
sum(partner closing capital)
    ==
contract closing capital
```

and:

```text
sum(partner profit allocations)
    ==
total profit allocation
```

and:

```text
sum(partner loss allocations)
    ==
total loss allocation
```

If these fail, the report should expose a reconciliation failure rather than silently presenting totals.

---

# 14. Period statement

Now the broader period statement becomes a composition:

```text
Financial Result
       +
Partner Positions
       +
Distribution
       +
Settlement
       +
Loss Recovery
       ↓
SyirkahPeriodStatement
```

The existing model we designed remains appropriate:

```java
public record SyirkahPeriodStatement(
        TenantId tenantId,
        LedgerId ledgerId,
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

        BigDecimal totalProfitAllocated,
        BigDecimal totalLossAllocated,

        BigDecimal openingCapital,
        BigDecimal capitalContributions,
        BigDecimal capitalReturns,
        BigDecimal capitalImpairments,
        BigDecimal closingCapital,

        BigDecimal openingCurrentAccount,
        BigDecimal withdrawals,
        BigDecimal closingCurrentAccount,

        BigDecimal totalSettlementObligations,
        BigDecimal totalSettled,
        BigDecimal totalOutstanding,

        BigDecimal totalLossResponsibility,
        BigDecimal totalLossRecovery,
        BigDecimal totalOutstandingRecovery,

        SyirkahPeriodReconciliation reconciliation
) {
}
```

---

# 15. Reconciliation should happen at report construction

```text
Build statement
      ↓
Calculate reconciliation
      ↓
balanced?
   ├── no → report inconsistency
   └── yes
        ↓
return statement
```

The reconciliation should cover:

```text
Result allocation
Capital roll-forward
Current-account roll-forward
Settlement
Loss recovery
```

This creates one consistent reporting contract.

---

# 16. The most important historical rule

Suppose:

```text
March 31
Close snapshot:
A closing capital = 500m
B closing capital = 300m
```

Then April has:

```text
A contribution = 100m
```

March reporting must still say:

```text
A = 500m
```

not:

```text
A = 600m
```

Therefore:

```text
March report
    ↓
March snapshot

April report
    ↓
March opening snapshot
    +
April movements
```

This gives us proper period continuity.

---

# 17. Contract transition becomes clean

This reporting architecture now gives us the predecessor state needed for partner entry/exit.

For example:

```text
Contract V1
    ↓
March close
    ↓
Frozen snapshot
    ↓
Partner A closing position
    ↓
Exit settlement
    ↓
V1 terminated
```

Then:

```text
Contract V2
    ↓
Opening position explicitly established
```

There is no need to infer the predecessor position from mutable balances.

---

# 18. Reporting service

The application service can now expose:

```java
public interface SyirkahReportingService {

    Uni<SyirkahPartnerStatement> getPartnerStatement(
            TenantId tenantId,
            LedgerId ledgerId,
            String contractId,
            String partyId,
            LocalDate periodStart,
            LocalDate periodEnd);

    Uni<SyirkahCapitalStatement> getCapitalStatement(
            TenantId tenantId,
            LedgerId ledgerId,
            String contractId,
            LocalDate periodStart,
            LocalDate periodEnd);

    Uni<SyirkahProfitDistributionStatement> getProfitDistributionStatement(
            TenantId tenantId,
            LedgerId ledgerId,
            String contractId,
            LocalDate periodStart,
            LocalDate periodEnd);

    Uni<SyirkahLossStatement> getLossStatement(
            TenantId tenantId,
            LedgerId ledgerId,
            String contractId,
            LocalDate periodStart,
            LocalDate periodEnd);

    Uni<SyirkahSettlementStatement> getSettlementStatement(
            TenantId tenantId,
            LedgerId ledgerId,
            String contractId,
            LocalDate periodStart,
            LocalDate periodEnd);

    Uni<SyirkahPeriodStatement> getPeriodStatement(
            TenantId tenantId,
            LedgerId ledgerId,
            String contractId,
            LocalDate periodStart,
            LocalDate periodEnd);
}
```

---

# 19. Reporting source hierarchy

The resulting architecture is:

```text
                    REPORTING
                        │
                        ▼
              Is period LOCKED?
                 /          \
               YES           NO
                │             │
                ▼             ▼
       Close Snapshot    Live Projections
                │             │
                └──────┬──────┘
                       ▼
              Reporting Models
                       │
       ┌───────────────┼────────────────┐
       ▼               ▼                ▼
   Partner          Capital          Settlement
   Statement        Statement        Statement
       │               │                │
       └───────────────┼────────────────┘
                       ▼
              Period Statement
```

This gives us a clean read architecture without making reporting another transactional domain.

---

## 20. One more important safeguard: snapshot version

Add:

```java
public record SyirkahSnapshotReference(
        String snapshotId,
        String snapshotHash,
        int schemaVersion
) {
}
```

Why?

Because the snapshot structure itself will evolve.

For example:

```text
schemaVersion = 1
```

may contain the original position model, while later:

```text
schemaVersion = 2
```

adds another required historical field.

The old snapshot must remain readable without pretending it was created under the new schema.

---

# Where we are now

The Syirkah close architecture has reached this point:

```text
Contract Version
      ↓
Policy Resolution
      ↓
Frozen Policy Snapshot
      ↓
Financial Calculation
      ↓
Partner Movement Aggregation
      ↓
Reconciliation
      ↓
Frozen Syirkah Close Snapshot
      ↓
┌─────────────┬──────────────┬──────────────┐
│             │              │              │
Partner     Capital       Distribution   Settlement
Statement   Statement      Statement      Statement
│             │              │              │
└─────────────┴──────────────┴──────────────┘
                       ↓
              Syirkah Period Statement
```

### Next layer

Now we should move into the **partner transition boundary**: using the frozen snapshot to implement **partner entry, partner exit, capital addition/reduction, partner replacement, and contract renewal** without corrupting the predecessor contract.

That is where the Islamic-law-specific rule becomes operational:

```text
OLD SYIRKAH
    ↓
CLOSE
    ↓
FROZEN POSITION
    ↓
SETTLEMENT / TRANSFER
    ↓
OLD CONTRACT ENDS
    ↓
NEW SYIRKAH CONTRACT
    ↓
EXPLICIT OPENING POSITION
```

This is the next major piece before we tackle the full liquidation/termination path.


/////////////

Good. Now we make the **contract transition boundary** concrete.

The source material is particularly useful here: it describes changes in capital and nisbah followed by a new-period opening balance, and for dissolution explicitly says the Syirkah must first perform *tutup buku*. Hukum-muamalah Hukum-muamalah

So the software rule should be:

```text
Existing Syirkah
      ↓
Close predecessor period
      ↓
Freeze predecessor position
      ↓
Resolve transition / settlement
      ↓
Create successor contract/version
      ↓
Build explicit opening position
      ↓
Activate successor
```

## 1. Don't modify the old contract in place

This is the most important architectural decision.

Avoid:

```java
contract.addParty(newParty);
contract.removeParty(oldParty);
contract.changeCapital(...);
```

for a material akad change.

Instead:

```text
Contract V1
    ↓
Transition
    ↓
Contract V2
```

The old version remains historically immutable.

This is especially important because the source example shows a change in the parties/capital composition and nisbah, followed by a new-period opening balance. Hukum-muamalah

---

# 2. Transition aggregate

We already proposed `SyirkahTransition`; now make its lifecycle strict.

```java
public enum SyirkahTransitionStatus {
    DRAFT,
    CLOSING_REQUIRED,
    SNAPSHOT_FROZEN,
    SETTLEMENT_REQUIRED,
    SUCCESSOR_PENDING,
    COMPLETED,
    CANCELLED
}
```

And:

```java
public final class SyirkahTransition {

    private final String transitionId;
    private final TenantId tenantId;
    private final LedgerId ledgerId;

    private final String previousContractId;
    private final int previousContractVersion;

    private final SyirkahTransitionType type;
    private final SyirkahTransitionReason reason;
    private final LocalDate transitionDate;

    private SyirkahTransitionStatus status;

    private String closeCycleId;
    private String snapshotId;
    private String nextContractId;

    // lifecycle methods...
}
```

---

# 3. Transition types

Keep these:

```java
public enum SyirkahTransitionType {
    PARTNER_ENTRY,
    PARTNER_EXIT,
    CAPITAL_ADDITION,
    CAPITAL_REDUCTION,
    PARTNER_REPLACEMENT,
    CONTRACT_RENEWAL,
    FULL_TERMINATION
}
```

But don't treat them all identically.

There are three families:

```text
STRUCTURAL
    PARTNER_ENTRY
    PARTNER_EXIT
    PARTNER_REPLACEMENT

CAPITAL / TERMS
    CAPITAL_ADDITION
    CAPITAL_REDUCTION
    CONTRACT_RENEWAL

TERMINAL
    FULL_TERMINATION
```

That distinction will become useful for settlement and activation policy.

---

# 4. Transition creation

A transition starts as:

```text
DRAFT
```

Then the application service verifies:

```text
previous contract exists
previous version is active
transition date is valid
no conflicting transition exists
```

Then:

```java
transition.requireClose(closeCycleId);
```

moves:

```text
DRAFT
   ↓
CLOSING_REQUIRED
```

It should **not** directly create the next contract.

---

# 5. The snapshot is the boundary

Once predecessor close succeeds:

```java
transition.freezeSnapshot(snapshotId);
```

Only:

```text
CLOSING_REQUIRED
        ↓
SNAPSHOT_FROZEN
```

is allowed.

And the transition should verify that the snapshot actually belongs to:

```text
tenant
ledger
previous contract
previous version
close cycle
```

This prevents attaching a snapshot from another contract.

---

# 6. Snapshot must be immutable

The transition references:

```text
snapshotId
```

but does not modify the snapshot.

So:

```text
Transition
    ─────references────→
Frozen Close Snapshot
```

not:

```text
Transition
    ─────updates────→
Close Snapshot
```

This preserves historical evidence.

---

# 7. Extract the predecessor position

For each affected partner:

```java
public record PartnerTransitionPosition(
        String partyId,
        BigDecimal capitalAtTransition,
        BigDecimal currentAccountAtTransition
) {
}
```

This is taken from the frozen snapshot.

Not from:

```text
PartnerAccount
```

Not from:

```text
current journal balance
```

Not recalculated from today's data.

---

# 8. Transition instruction

Then introduce:

```java
public record PartnerTransitionInstruction(
        String partyId,
        TransitionAction action,
        BigDecimal capitalAtTransition,
        BigDecimal currentAccountAtTransition,
        boolean retainedInNextContract
) {
    public enum TransitionAction {
        RETAIN,
        EXIT,
        ENTER,
        REPLACE
    }
}
```

This gives us an explicit answer to:

> What happens to each partner at the boundary?

For example:

```text
V1:
A RETAIN
B EXIT

V2:
A RETAIN
C ENTER
```

---

# 9. Never infer the successor from the predecessor

For `PARTNER_REPLACEMENT`:

```text
A exits
C enters
```

does not mean:

```text
C automatically inherits A's capital.
```

Instead there are separate concepts:

```text
A's predecessor position
        ↓
transfer / settlement
        ↓
C's new contribution
```

This is exactly why we previously separated `PartnerInterestTransfer`.

---

# 10. Partner interest transfer

Keep:

```java
public record PartnerInterestTransfer(
        String transferId,
        String outgoingPartyId,
        String incomingPartyId,
        BigDecimal carryingValue,
        BigDecimal agreedTransferPrice,
        BigDecimal premiumOrDiscount
) {
}
```

and:

```java
premiumOrDiscount =
    agreedTransferPrice.subtract(carryingValue);
```

The three numbers remain distinct:

```text
carrying value
transfer price
new partner capital contribution
```

They must not be automatically equated.

---

# 11. Transfer restrictions remain policy-driven

For example:

```java
public interface PartnerTransferRestrictionPolicy {

    TransferDecision evaluate(
            String contractId,
            String outgoingPartyId,
            String incomingPartyId);
}
```

This is where contractual restrictions or the relevant *syuf'ah* treatment can be implemented.

The accounting/domain model should not hardcode a universal legal interpretation.

That is particularly important because the supplied material itself discusses applying existing Sharia rules to concrete cases (*tathbiqul hukmi*), rather than treating software as the authority that independently derives every ruling. Hukum-muamalah

---

# 12. Settlement must precede successor activation when required

After the snapshot:

```text
SNAPSHOT_FROZEN
       ↓
SETTLEMENT_REQUIRED
```

Then evaluate:

```java
public interface SyirkahTransitionSettlementPolicy {

    TransitionSettlementDecision evaluate(
            SyirkahTransition transition,
            SyirkahCloseSnapshot snapshot,
            List<SyirkahSettlementObligation> obligations);
}
```

This prevents us from hardcoding:

```text
every outstanding amount must be paid
```

because different transition types may have different legitimate settlement requirements.

---

# 13. Build the successor opening position explicitly

This is the next critical aggregate:

```java
public record NextContractOpeningPosition(
        String partyId,
        BigDecimal capitalContribution,
        BigDecimal retainedBusinessBalance
) {
}
```

The new contract receives an **explicit opening state**.

Example:

```text
Old V1
A capital = 100m
B capital = 50m

B exits
C enters with 75m

New V2
A opening capital = 100m
C opening capital = 75m
```

There is no hidden carry-over.

---

# 14. Retained profit is separate

Suppose V1 has:

```text
retained business balance = 20m
```

Do not automatically say:

```text
A capital += 20m
B capital += 20m
```

Instead:

```text
NextContractOpeningPosition
    A capital = ...
    B capital = ...
    retainedBusinessBalance = 20m
```

Only if the new akad explicitly treats retained funds as capital should they become capital.

This prevents a very dangerous accounting/domain conflation.

---

# 15. Successor contract activation

Now the activation policy we designed becomes useful:

```java
public interface SyirkahContractActivationPolicy {

    SyirkahContractActivationDecision evaluate(
            SyirkahContractActivationContext context);
}
```

Context:

```java
public record SyirkahContractActivationContext(
        TenantId tenantId,
        LedgerId ledgerId,
        String previousContractId,
        int previousContractVersion,
        String nextContractId,
        LocalDate effectiveDate,
        boolean predecessorCloseLocked,
        boolean predecessorSnapshotFrozen,
        boolean requiredSettlementCompleted,
        boolean predecessorTerminated
) {
}
```

For a successor contract:

```text
predecessor close locked      = true
snapshot frozen               = true
required settlement completed = true
predecessor terminated        = true
```

Only then:

```text
READY
  ↓
ACTIVE
```

---

# 16. Don't let `attachNextContract()` activate anything

This deserves an explicit rule.

This:

```java
transition.attachNextContract(nextContractId);
```

means only:

```text
"this is the proposed successor"
```

It must not mean:

```text
"the successor is active"
```

Otherwise a failed settlement could leave:

```text
V1 still active
V2 already active
```

which is exactly what we want to prevent.

---

# 17. Transition completion

For non-terminal transitions:

```text
DRAFT
 ↓
CLOSING_REQUIRED
 ↓
SNAPSHOT_FROZEN
 ↓
SETTLEMENT_REQUIRED
 ↓
SUCCESSOR_PENDING
 ↓
COMPLETED
```

The final transition completion requires:

```text
predecessor closed
snapshot frozen
required settlement satisfied
successor activated
```

Only then:

```java
transition.complete();
```

---

# 18. Full termination is different

For:

```java
SyirkahTransitionType.FULL_TERMINATION
```

there is no successor.

Therefore:

```java
transition.attachNextContract(...)
```

must fail.

And:

```java
transition.complete()
```

must fail.

Instead:

```java
transition.completeTermination();
```

after:

```text
close
 ↓
snapshot
 ↓
liquidation
 ↓
final result
 ↓
distribution
 ↓
capital return
 ↓
settlement policy
 ↓
terminated
```

This keeps terminal and non-terminal transitions cleanly separated.

---

# 19. Capital addition

For `CAPITAL_ADDITION`, we should still create a transition boundary if the business meaning is a new contractual capital composition.

The source material gives a concrete example where new capital enters and the subsequent period has a new opening balance. Hukum-muamalah

So:

```text
V1
 ↓
close
 ↓
snapshot
 ↓
capital addition agreed
 ↓
V2
 ↓
opening balance
```

not:

```text
V1
 ↓
random journal
 ↓
capital magically changes
```

---

# 20. Capital reduction

Likewise:

```text
V1
 ↓
snapshot
 ↓
capital return / reduction settlement
 ↓
V2
```

The return is represented explicitly as:

```java
PartnerAccountMovementType.CAPITAL_RETURN
```

not:

```java
PARTNER_WITHDRAWAL
```

because these have different economic meaning.

---

# 21. Partner exit

Partner exit should produce:

```text
PartnerClosePositionSnapshot
        ↓
Capital return obligation
        +
Current-account settlement
        +
Other applicable transfer obligation
        ↓
Settlement
```

The old partner's historical V1 position remains untouched.

---

# 22. Partner entry

Entry is the reverse direction:

```text
Successor contract
        ↓
new partner agreement
        ↓
capital contribution
        ↓
new partner opening position
        ↓
V2 active
```

The new partner should never appear retrospectively in V1.

---

# 23. Partner replacement

Replacement combines both:

```text
Outgoing partner
       ↓
V1 frozen position
       ↓
transfer / settlement
       ↓
outgoing relationship ends

Incoming partner
       ↓
new agreement
       ↓
new contribution
       ↓
V2 opening position
```

This is why `PARTNER_REPLACEMENT` should not simply be implemented as:

```java
removeParty(old);
addParty(new);
```

---

# 24. Database invariants

Add:

```sql
CREATE UNIQUE INDEX ux_syirkah_transition_active
ON syirkah_transition (
    tenant_id,
    ledger_id,
    previous_contract_id
)
WHERE status NOT IN ('COMPLETED', 'CANCELLED');
```

And successor contract activation should have its own invariant:

```sql
CREATE UNIQUE INDEX ux_syirkah_active_contract
ON syirkah_contract (
    tenant_id,
    ledger_id
)
WHERE activation_status = 'ACTIVE';
```

If the business eventually permits multiple simultaneous Syirkah relationships, introduce an **explicit business scope** rather than weakening this constraint invisibly.

---

# 25. Application service

The orchestration now becomes:

```java
public interface SyirkahTransitionService {

    Uni<SyirkahTransition> create(
            CreateSyirkahTransitionRequest request);

    Uni<SyirkahTransition> requireClose(
            String transitionId,
            String closeCycleId);

    Uni<SyirkahTransition> freezeSnapshot(
            String transitionId,
            String snapshotId);

    Uni<List<SyirkahSettlementObligation>> prepareSettlement(
            String transitionId);

    Uni<SyirkahTransition> attachNextContract(
            String transitionId,
            String nextContractId);

    Uni<SyirkahContractActivationDecision> evaluateActivation(
            String transitionId);

    Uni<SyirkahTransition> complete(
            String transitionId);
}
```

For full termination, use the dedicated termination service rather than adding ten more methods here.

---

# 26. The resulting transition state machine

```text
                    ┌──────────────────┐
                    │      DRAFT       │
                    └────────┬─────────┘
                             │
                             ▼
                 ┌──────────────────────┐
                 │ CLOSING_REQUIRED     │
                 └──────────┬───────────┘
                            │
                     close locked
                            │
                            ▼
                 ┌──────────────────────┐
                 │  SNAPSHOT_FROZEN     │
                 └──────────┬───────────┘
                            │
                            ▼
                 ┌──────────────────────┐
                 │ SETTLEMENT_REQUIRED  │
                 └──────────┬───────────┘
                            │
                  settlement policy
                            │
                            ▼
                 ┌──────────────────────┐
                 │ SUCCESSOR_PENDING    │
                 └──────────┬───────────┘
                            │
                    activation policy
                            │
                            ▼
                 ┌──────────────────────┐
                 │      COMPLETED       │
                 └──────────────────────┘
```

Terminal path:

```text
SNAPSHOT_FROZEN
      ↓
LIQUIDATION
      ↓
FINAL RECONCILIATION
      ↓
DISTRIBUTION
      ↓
CAPITAL RETURN
      ↓
SETTLEMENT
      ↓
TERMINATED
```

---

## 27. The key invariant

We can now state the Syirkah transition invariant very clearly:

```text
A successor contract may never become ACTIVE
until the predecessor's required financial state has been
closed, frozen, reconciled, and settled according to the
applicable transition policy.
```

And:

```text
A predecessor contract can never be rewritten to represent
the successor's participants, capital, nisbah, or terms.
```

That gives us historical integrity across akad changes.

### Next layer

The natural next step is **the actual `SyirkahTransitionService` orchestration + `NextContractOpeningPosition` builder**, including how to handle:

- retained partners,
- exiting partners,
- incoming partners,
- capital transfers,
- retained business balances,
- settlement obligations,
- successor contract creation,
- atomic activation,
- retry/idempotency,
- and transition audit events.

That will turn the above state machine into an executable application workflow.

//////////////


Next we make the transition workflow executable: **build the successor opening position, create the successor contract, and activate it atomically**.

## 1. Introduce a transition opening-position builder

The builder consumes only the frozen predecessor state plus explicit transition instructions.

```java
public interface SyirkahOpeningPositionBuilder {

    Uni<List<NextContractOpeningPosition>> build(
            SyirkahTransition transition,
            SyirkahCloseSnapshot predecessorSnapshot,
            SyirkahContract.Version successorVersion,
            List<PartnerTransitionInstruction> instructions);
}
```

The critical rule:

```text
predecessor snapshot
        +
explicit transition instructions
        ↓
successor opening position
```

Not:

```text
current PartnerAccount
        ↓
guess successor state
```

---

## 2. Validate every successor partner

The builder should first construct:

```text
successor parties
```

from the successor contract version.

Then require an instruction for every party.

Example:

```text
Successor:
    A
    C

Instructions:
    A RETAIN
    C ENTER
```

Valid.

But:

```text
Successor:
    A
    C

Instructions:
    A RETAIN
```

must fail.

Likewise:

```text
Instructions:
    A RETAIN
    C ENTER
    D ENTER
```

must fail if D isn't in the successor contract.

This prevents silent state creation.

---

# 3. Retained partner

For:

```java
TransitionAction.RETAIN
```

the opening position comes from the predecessor snapshot:

```java
new NextContractOpeningPosition(
    partyId,
    capitalAtTransition,
    retainedBusinessBalance
);
```

But the amount must be explicitly defined by the transition.

Do not automatically carry:

```text
current account
```

into:

```text
capital
```

or vice versa.

---

# 4. Incoming partner

For:

```java
TransitionAction.ENTER
```

the capital comes from the new partner agreement:

```text
new contract
    ↓
partner agreement
    ↓
capital contribution
    ↓
opening capital
```

There should be no predecessor position for this party.

If an incoming party already appears in the predecessor contract, that is not a normal `ENTER`; the transition must use an explicit action such as `RETAIN` or `REPLACE`.

---

# 5. Exiting partner

For:

```java
TransitionAction.EXIT
```

the successor opening position contains no position for that party.

But the predecessor position must still be preserved in the frozen snapshot.

Then settlement obligations are created from that position.

Example:

```text
V1 snapshot:

A = 100m
B = 50m

B EXIT

V2:

A = 100m
```

while:

```text
B settlement obligation = policy-determined amount
```

This is the correct separation.

---

# 6. Replacement

For:

```text
A → C
```

use:

```text
A = EXIT
C = ENTER
```

plus an explicit `PartnerInterestTransfer` if the transfer is actually between A and C.

That means:

```text
A predecessor position
        ↓
transfer / settlement
        ↓
C successor contribution
```

rather than pretending C inherited A's balance.

---

# 7. Retained business balance

Add an explicit transition field:

```java
public enum RetainedBalanceTreatment {
    DISTRIBUTE,
    RETAIN_IN_BUSINESS,
    CONVERT_TO_CAPITAL
}
```

This is policy/business-decision data, not something the builder should infer.

For:

```text
RETAIN_IN_BUSINESS
```

the successor opening position can contain:

```text
retainedBusinessBalance = X
capitalContribution = Y
```

For:

```text
CONVERT_TO_CAPITAL
```

the transition must explicitly define the amount converted.

Never silently convert retained profit into capital.

---

# 8. Transition settlement preparation

Introduce:

```java
public interface SyirkahTransitionSettlementBuilder {

    Uni<List<SyirkahSettlementObligation>> build(
            SyirkahTransition transition,
            SyirkahCloseSnapshot snapshot,
            List<PartnerTransitionInstruction> instructions);
}
```

This handles obligations such as:

```text
partner capital return
current-account settlement
transfer settlement
other transition-specific obligations
```

The builder does not execute cash movement.

It creates obligations.

---

# 9. Example: partner exit

Suppose the frozen snapshot says:

```text
Partner B

capital = 50m
current account = 10m
```

The transition policy might determine:

```text
capital return = 50m
current account settlement = 10m
```

Then:

```text
Settlement obligations
    B / CAPITAL_RETURN      50m
    B / CURRENT_ACCOUNT     10m
```

These are separate obligations.

The software must not collapse them into:

```text
B / SETTLEMENT = 60m
```

unless the applicable settlement/accounting policy explicitly wants that representation.

---

# 10. Capital return remains distinct

For liquidation we already introduced:

```java
PartnerCapitalReturnObligation
```

For ordinary transition exit, use the same conceptual separation but don't automatically reuse the liquidation aggregate.

A partner leaving a continuing Syirkah is not necessarily a liquidation of the whole business.

So use a transition-specific obligation:

```java
public record PartnerExitSettlementObligation(
        String obligationId,
        String transitionId,
        String partyId,
        BigDecimal capitalReturn,
        BigDecimal currentAccountSettlement
) {
}
```

Then generate underlying settlement obligations from it.

---

# 11. Transition service

Now the orchestration becomes:

```java
public interface SyirkahTransitionService {

    Uni<SyirkahTransition> create(
            CreateSyirkahTransitionRequest request);

    Uni<SyirkahTransition> freezePredecessor(
            String transitionId,
            String closeCycleId,
            String snapshotId,
            String operator);

    Uni<List<SyirkahSettlementObligation>> prepareSettlement(
            String transitionId,
            String operator);

    Uni<SyirkahContract> createSuccessor(
            String transitionId,
            CreateSuccessorContractRequest request,
            String operator);

    Uni<SyirkahContractActivationDecision> evaluateSuccessorActivation(
            String transitionId);

    Uni<SyirkahTransition> activateAndComplete(
            String transitionId,
            String operator);
}
```

---

# 12. `freezePredecessor`

This operation must verify:

```text
close cycle = LOCKED
snapshot = exists
snapshot = belongs to predecessor
snapshot = reconciled
```

Then:

```text
transition:
CLOSING_REQUIRED
        ↓
SNAPSHOT_FROZEN
```

All checks happen before changing the transition.

---

# 13. Prepare settlement

Then:

```text
SNAPSHOT_FROZEN
       ↓
calculate required transition obligations
       ↓
persist obligations
       ↓
SETTLEMENT_REQUIRED
```

This operation must be idempotent.

Use:

```text
tenant
+
ledger
+
transition
+
party
+
obligation type
```

as the business uniqueness boundary.

---

# 14. Idempotency

Suppose the request is retried:

```text
prepareSettlement(T1)
prepareSettlement(T1)
prepareSettlement(T1)
```

The result must remain:

```text
same obligations
```

not:

```text
three copies of the same obligation
```

Database uniqueness is the final protection.

---

# 15. Create successor contract

Only after predecessor state is frozen should the application create the successor.

```text
V1
 ↓
snapshot frozen
 ↓
transition instructions
 ↓
V2 draft
```

The successor starts as:

```java
SyirkahContractActivationStatus.PENDING_PREDECESSOR
```

not `ACTIVE`.

---

# 16. Successor contract needs explicit predecessor reference

Add:

```java
public record SyirkahContractLineage(
        String predecessorContractId,
        int predecessorContractVersion,
        String transitionId
) {
}
```

This gives us:

```text
V1
  │
  └── transition T1
          │
          ▼
         V2
```

Historical reconstruction becomes much easier.

---

# 17. Build successor opening position before activation

The successor should have a persisted opening-position artifact:

```java
public record SyirkahContractOpeningSnapshot(
        String openingSnapshotId,
        TenantId tenantId,
        LedgerId ledgerId,
        String contractId,
        int contractVersion,
        String predecessorSnapshotId,
        String transitionId,
        List<NextContractOpeningPosition> positions,
        String snapshotHash
) {
}
```

This is important.

The successor shouldn't need to repeatedly derive its opening state from V1.

---

# 18. Opening snapshot is immutable

Once V2 becomes active:

```text
Opening Snapshot
      ↓
FROZEN
```

No update.

That gives us:

```text
V1 closing snapshot
       ↓
transition
       ↓
V2 opening snapshot
       ↓
V2 active
```

This creates a clean accounting/akad chain.

---

# 19. Activation gate

The activation policy checks:

```text
predecessor close locked
snapshot frozen
required settlements satisfied
predecessor terminated/superseded
successor opening snapshot frozen
successor effective date valid
no overlapping active contract
```

Then:

```text
PENDING_PREDECESSOR
        ↓
READY
        ↓
ACTIVE
```

---

# 20. Important ordering

For a normal successor transition:

```text
1. Close V1
2. Freeze V1 snapshot
3. Determine transition obligations
4. Complete required settlement
5. Create V2
6. Build V2 opening snapshot
7. Freeze V2 opening snapshot
8. Supersede/terminate V1
9. Activate V2
10. Complete transition
```

The exact ordering of steps 8 and 9 should be performed inside one transactional boundary where the database supports it.

We must never expose:

```text
V1 inactive
V2 inactive
```

as a successful completed transition.

Nor:

```text
V1 active
V2 active
```

when concurrent active contracts are forbidden.

---

# 21. Better: use a transition transaction

Introduce:

```java
public interface SyirkahTransitionTransaction {

    Uni<SyirkahTransitionResult> activateSuccessor(
            String transitionId);
}
```

The infrastructure transaction performs:

```text
BEGIN
  lock transition
  lock predecessor contract
  lock successor contract

  verify all gates

  persist successor opening snapshot
  mark predecessor superseded/terminated
  activate successor
  complete transition

  append domain events
  write outbox

COMMIT
```

This is one of the places where application-level orchestration alone is insufficient.

---

# 22. Database concurrency matters

Two workers might simultaneously execute:

```text
activateSuccessor(T1)
activateSuccessor(T1)
```

Both may observe:

```text
READY
```

Therefore:

```text
SELECT ... FOR UPDATE
```

or the equivalent optimistic concurrency/version mechanism is required.

The unique active-contract constraint remains the final safety net.

---

# 23. Event sequence

A successful transition can emit:

```text
TRANSITION_CREATED
CLOSE_LOCKED
SNAPSHOT_FROZEN
TRANSITION_SETTLEMENT_PREPARED
TRANSITION_SETTLEMENT_COMPLETED
SUCCESSOR_CONTRACT_CREATED
SUCCESSOR_OPENING_SNAPSHOT_FROZEN
SUCCESSOR_CONTRACT_ACTIVATED
PREDECESSOR_CONTRACT_SUPERSEDED
TRANSITION_COMPLETED
```

These should use the existing:

```text
AccountingEvent
OutboxRepository
```

rather than creating a second event infrastructure.

---

# 24. Audit chain

The existing Syirkah audit evidence can record:

```text
WHO
  ↓
created transition
  ↓
which predecessor
  ↓
which snapshot
  ↓
which settlement
  ↓
which successor
  ↓
which opening snapshot
  ↓
who activated it
```

That makes the transition reconstructable without relying solely on current database state.

---

# 25. Example

Suppose V1 is:

```text
A capital = 100m
B capital = 50m
```

B exits.

Transition:

```text
PARTNER_EXIT
```

After V1 close:

```text
V1 snapshot
A = 100m
B = 50m
```

Settlement policy determines:

```text
B capital return = 50m
```

Then V2 is:

```text
A capital = 100m
```

The chain is:

```text
V1
 │
 ├── Close Snapshot
 │      A = 100m
 │      B = 50m
 │
 ├── B Exit Settlement
 │      50m
 │
 └── Transition T1
          │
          ▼
        V2
          │
          └── Opening Snapshot
                 A = 100m
```

B does not appear in V2.

But B's V1 position remains permanently available for historical reporting.

---

# 26. Example: replacement

```text
V1
A = 100m
B = 50m
```

B is replaced by C.

Transition:

```text
B EXIT
C ENTER
```

Suppose C contributes:

```text
75m
```

Then:

```text
V2
A = 100m
C = 75m
```

The difference between B's 50m position and C's 75m contribution is not magically classified.

It is represented through the explicit transfer/contribution policies.

---

# 27. Example: retained business balance

Suppose:

```text
V1 retained business balance = 20m
```

and the new contract keeps it in the business.

Then V2 opening state can be:

```text
A capital               100m
C capital                75m
retained business balance 20m
```

It is **not**:

```text
A capital = 110m
C capital = 85m
```

unless the new akad explicitly establishes that conversion.

---

# 28. Contract lineage

At this point, we have a very useful graph:

```text
Contract V1
    │
    ├── Close Snapshot S1
    │
    ├── Transition T1
    │      │
    │      ├── Settlement O1
    │      │
    │      └── Opening Snapshot S2
    │
    └──────────────► Contract V2
```

And potentially:

```text
V2
 │
 └── T2
      ↓
     V3
```

So a Syirkah relationship becomes a **contract lineage**, not merely one mutable contract record.

---

# 29. This also solves reporting continuity

Reporting can now walk:

```text
V1
 ↓
V1 close snapshot
 ↓
V2 opening snapshot
 ↓
V2 periods
```

There is no ambiguity about where the opening balance came from.

---

# 30. Final invariant for successor creation

The successor contract can be created as `DRAFT` before all gates are satisfied.

But:

```text
ACTIVE successor
```

requires:

```text
predecessor close = LOCKED
predecessor snapshot = FROZEN
transition settlement = SATISFIED according to policy
successor opening snapshot = FROZEN
effective date = valid
no conflicting active contract
```

That is the boundary we want.

---

## Architecture after this step

```text
                  SYIRKAH CONTRACT LINEAGE

                         Contract V1
                              │
                              ▼
                       Close Cycle V1
                              │
                              ▼
                   Frozen Close Snapshot
                              │
                              ▼
                       Transition T1
                    ┌─────────┴─────────┐
                    ▼                   ▼
             Settlement             Transfer
                    │                   │
                    └─────────┬─────────┘
                              ▼
                   Successor Opening
                       Snapshot
                              │
                              ▼
                         Contract V2
                              │
                              ▼
                         Close Cycle
                              │
                             ...
```

This gives us the proper foundation for **partner entry/exit and contract renewal without rewriting history**.

**Next:** the remaining major branch is **full Syirkah termination/liquidation**—asset realization, liability settlement, final result determination, profit/loss distribution, capital return, settlement completion, and finally `CONTRACT_TERMINATED`. That branch should reuse this same snapshot/obligation architecture but has no successor contract.


///////////////


## 1. Full termination is a different workflow

For `FULL_TERMINATION`, the lifecycle is:

```text
Active Syirkah
    ↓
Tutup Buku
    ↓
Frozen Close Snapshot
    ↓
Liquidation
    ↓
Asset Realization
    ↓
Liability Settlement
    ↓
Final Reconciliation
    ↓
Final Profit/Loss
    ↓
Distribution / Loss Absorption
    ↓
Capital Return
    ↓
Cash Settlement
    ↓
Contract TERMINATED
```

This is consistent with the supplied material's treatment of dissolution: first determine the financial position, then settle the remaining assets/liabilities and distribute the resulting position. Hukum-muamalah

The crucial difference from `PARTNER_EXIT` is:

```text
PARTNER_EXIT
    → business continues
    → successor contract may exist

FULL_TERMINATION
    → business relationship ends
    → no successor contract
```

---

# 2. Create the liquidation aggregate

We already defined the core object. Now make its state machine explicit:

```java
public enum LiquidationStatus {
    NOT_STARTED,
    ASSET_REALIZATION,
    LIABILITY_SETTLEMENT,
    RECONCILIATION,
    RESULT_DETERMINED,
    DISTRIBUTION,
    CAPITAL_RETURN,
    COMPLETED,
    CANCELLED
}
```

The aggregate:

```java
public final class SyirkahLiquidation {

    private final String liquidationId;
    private final TenantId tenantId;
    private final LedgerId ledgerId;

    private final String contractId;
    private final int contractVersion;
    private final String transitionId;
    private final String closeCycleId;

    private LiquidationStatus status;

    private BigDecimal grossRealization;
    private BigDecimal liabilitiesSettled;
    private BigDecimal netRealization;
    private BigDecimal profitOrLoss;

    private String completedBy;
    private Instant completedAt;

    // state-changing methods...
}
```

---

# 3. The liquidation cannot start from an open period

The first invariant:

```text
FULL_TERMINATION
        ↓
predecessor close must be LOCKED
        ↓
close snapshot must be FROZEN
```

Therefore:

```java
public Uni<SyirkahLiquidation> start(
        StartLiquidationRequest request) {
    ...
}
```

must require:

```text
closeCycle.status = LOCKED
snapshot exists
snapshot.reconciliation = BALANCED
```

No shortcut.

---

# 4. Link liquidation to transition

Add:

```java
private final String transitionId;
```

to `SyirkahLiquidation`.

This produces:

```text
Contract
   ↓
Termination Transition
   ↓
Frozen Close Snapshot
   ↓
Liquidation
```

So a liquidation cannot accidentally belong to another contract.

---

# 5. Start liquidation

Initial state:

```text
NOT_STARTED
```

then:

```java
liquidation.startAssetRealization();
```

moves:

```text
NOT_STARTED
      ↓
ASSET_REALIZATION
```

The operation should only succeed if:

```text
snapshot frozen
transition = FULL_TERMINATION
contract = correct predecessor
```

---

# 6. Asset realization is a separate domain record

Do not put individual assets directly into the liquidation aggregate.

Use:

```java
public record LiquidationRealization(
        String realizationId,
        String liquidationId,
        String assetReference,
        BigDecimal carryingValue,
        BigDecimal realizedAmount,
        LocalDate realizationDate,
        String sourceReference
) {
    public LiquidationRealization {
        Objects.requireNonNull(realizationId);
        Objects.requireNonNull(liquidationId);
        Objects.requireNonNull(assetReference);
        Objects.requireNonNull(carryingValue);
        Objects.requireNonNull(realizedAmount);
        Objects.requireNonNull(realizationDate);

        if (carryingValue.signum() < 0) {
            throw new IllegalArgumentException(
                    "Carrying value cannot be negative");
        }

        if (realizedAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Realized amount cannot be negative");
        }
    }

    public BigDecimal realizationGainOrLoss() {
        return realizedAmount.subtract(carryingValue);
    }
}
```

This gives us:

```text
asset carrying value
        +
actual realization
        ↓
realization gain/loss
```

---

# 7. Don't use journal entries as the realization source

A journal might eventually record:

```text
Dr Cash
Dr/Cr Gain/Loss
Cr Asset
```

But the liquidation domain needs to know:

```text
what asset was realized
for how much
when
under which liquidation
```

Therefore:

```text
LiquidationRealization
        ↓
accounting policy
        ↓
JournalEntry
```

not:

```text
JournalEntry
        ↓
guess liquidation realization
```

---

# 8. Complete asset realization

After all required realization records exist:

```java
liquidation.completeAssetRealization();
```

moves:

```text
ASSET_REALIZATION
        ↓
LIABILITY_SETTLEMENT
```

But before that, validate:

```text
all required liquidation assets handled
no duplicate realization
realization totals reconciled
```

Missing assets must block completion.

---

# 9. Liability settlement

Use a separate record:

```java
public record LiquidationLiabilitySettlement(
        String settlementId,
        String liquidationId,
        String liabilityReference,
        BigDecimal outstandingAmount,
        BigDecimal settledAmount,
        LocalDate settlementDate
) {
    public LiquidationLiabilitySettlement {
        if (outstandingAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Outstanding amount cannot be negative");
        }

        if (settledAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Settled amount cannot be negative");
        }

        if (settledAmount.compareTo(outstandingAmount) > 0) {
            throw new IllegalArgumentException(
                    "Settled amount cannot exceed outstanding amount");
        }
    }
}
```

Again, this is the **business liquidation record**.

The payment itself remains a settlement concern.

---

# 10. Do not silently ignore unpaid liabilities

This is important.

Suppose:

```text
Assets realized = 1,000m
Liabilities = 300m
Paid = 250m
```

The system must not simply say:

```text
netRealization = 750m
```

and pretend the liquidation is finished.

Instead it must explicitly know:

```text
liabilities:
    outstanding = 300m
    settled     = 250m
    remaining   = 50m
```

Then the liquidation completion policy decides whether that outstanding amount is allowed.

---

# 11. Final reconciliation

After realization and liability settlement:

```text
ASSET_REALIZATION
        ↓
LIABILITY_SETTLEMENT
        ↓
RECONCILIATION
```

Calculate:

```text
grossRealization
    =
sum(realized amounts)
```

and:

```text
liabilitiesSettled
    =
sum(settled liability amounts)
```

Then:

```text
netRealization
    =
grossRealization
    - liabilitiesSettled
```

This is an arithmetic reconciliation, not yet the final Syirkah result.

---

# 12. Final result is policy-driven

Do **not** universally calculate:

```java
profitOrLoss =
    netRealization.subtract(originalCapital);
```

That may be valid for a particular contract/policy, but it must not become a universal Syirkah rule.

Use:

```java
public interface SyirkahLiquidationResultPolicy {

    Uni<SyirkahLiquidationResult> determine(
            SyirkahLiquidation liquidation,
            SyirkahCloseSnapshot openingSnapshot,
            SyirkahContract.Version contractVersion);
}
```

Then:

```text
SyirkahType
    ↓
LiquidationResultPolicyResolver
    ↓
approved implementation
```

This also allows the frozen Sharia policy snapshot to determine exactly which implementation/version was used.

---

# 13. Liquidation result

```java
public record SyirkahLiquidationResult(
        String liquidationId,
        String contractId,
        int contractVersion,
        BigDecimal grossRealization,
        BigDecimal liabilitiesSettled,
        BigDecimal netRealization,
        BigDecimal originalCapital,
        BigDecimal profitOrLoss
) {
    public boolean isProfit() {
        return profitOrLoss.signum() > 0;
    }

    public boolean isLoss() {
        return profitOrLoss.signum() < 0;
    }

    public boolean isBreakEven() {
        return profitOrLoss.signum() == 0;
    }
}
```

This result should be immutable.

---

# 14. Why original capital comes from the snapshot

The liquidation needs the predecessor state:

```text
openingSnapshot
    ↓
partner positions
    ↓
original capital / relevant position
```

Do not read current partner balances.

By termination time, those mutable projections may already contain operational activity.

The frozen snapshot is the historical boundary.

---

# 15. Determine the final result

Once reconciliation succeeds:

```java
liquidation.determineResult(profitOrLoss);
```

moves:

```text
RECONCILIATION
       ↓
RESULT_DETERMINED
```

The result must be persisted immutably.

---

# 16. Distribution for profit

If:

```text
profitOrLoss > 0
```

then:

```text
RESULT_DETERMINED
       ↓
DISTRIBUTION
```

Use the same general separation as normal close:

```text
result
  ↓
partner allocation
  ↓
distribution obligation
  ↓
accounting
  ↓
settlement
```

The liquidation itself should not directly transfer cash.

---

# 17. Loss

If:

```text
profitOrLoss < 0
```

then the system uses:

```text
LossAllocationPolicy
```

through the frozen policy snapshot.

For example, a normal business loss may use capital-proportion allocation for a relevant contract type.

A responsibility case should go through:

```text
loss
 ↓
responsibility determination
 ↓
recovery obligation
```

rather than automatically charging a partner.

---

# 18. Break-even

If:

```text
profitOrLoss == 0
```

then:

```text
distribution = 0
loss absorption = 0
```

but capital return can still be required.

This is why:

```text
profit/loss
```

and:

```text
capital return
```

must remain independent.

---

# 19. Capital return

After the result is settled conceptually:

```text
DISTRIBUTION
       ↓
CAPITAL_RETURN
```

Create:

```java
PartnerCapitalReturnObligation
```

for each applicable partner.

Example:

```text
A capital return = 100m
B capital return = 50m
```

These are obligations, not yet cash payments.

---

# 20. Capital return must reconcile

Before completing the capital-return stage:

```text
sum(capital return obligations)
```

must agree with the liquidation reconciliation.

For example:

```text
net distributable position
       =
profit/loss treatment
       +
capital return
       +
remaining balance
```

The exact formula is policy-dependent, but the system must explicitly reconcile it.

No silent residual.

---

# 21. Residual balance must be explicit

A dangerous implementation is:

```java
remaining = ZERO;
```

after rounding.

Instead:

```java
public record SyirkahLiquidationReconciliation(
        BigDecimal netRealization,
        BigDecimal totalProfitDistribution,
        BigDecimal totalLossAbsorption,
        BigDecimal totalCapitalReturn,
        BigDecimal remainingBalance,
        boolean balanced,
        List<String> errors
) {
}
```

If:

```text
remainingBalance != 0
```

under the applicable monetary tolerance:

```text
LIQUIDATION_RECONCILIATION_FAILURE
```

and termination is blocked.

---

# 22. Settlement remains separate

After accounting obligations are created:

```text
Capital Return Obligation
       ↓
Settlement Obligation
       ↓
Payment Processor
       ↓
Cash Settlement
```

So:

```text
capital return = 150m
cash paid      = 100m
outstanding    = 50m
```

is a valid intermediate state if the completion policy permits it.

---

# 23. Completion policy

Use:

```java
public interface SyirkahLiquidationCompletionPolicy {

    CompletionDecision evaluate(
            SyirkahLiquidation liquidation,
            SyirkahLiquidationReconciliation liquidationReconciliation,
            SyirkahSettlementReconciliation settlementReconciliation);
}
```

Two useful policies:

```text
AllSettlementsRequiredPolicy
    → every required settlement must be complete

OutstandingSettlementsAllowedPolicy
    → obligations may remain outstanding
       if all required accounting/reconciliation conditions pass
```

The contract/business policy chooses which applies.

---

# 24. Final liquidation lifecycle

Now the full state machine becomes:

```text
NOT_STARTED
     ↓
ASSET_REALIZATION
     ↓
LIABILITY_SETTLEMENT
     ↓
RECONCILIATION
     ↓
RESULT_DETERMINED
     ↓
DISTRIBUTION
     ↓
CAPITAL_RETURN
     ↓
COMPLETED
```

Settlement execution can happen alongside the obligation stages, but **completion is gated by the configured settlement policy**.

---

# 25. Termination transition

The associated:

```text
SyirkahTransition(FULL_TERMINATION)
```

moves separately through:

```text
SNAPSHOT_FROZEN
       ↓
SETTLEMENT_REQUIRED
       ↓
READY_TO_TERMINATE
       ↓
COMPLETED
```

But `READY_TO_TERMINATE` is only possible after liquidation passes its completion policy.

Then:

```java
transition.completeTermination();
```

and the contract becomes:

```text
TERMINATED
```

---

# 26. Atomic finalization

The final operation should be transactional:

```java
public interface SyirkahTerminationTransaction {

    Uni<SyirkahTerminationResult> complete(
            String transitionId,
            String liquidationId);
}
```

Inside one database transaction:

```text
BEGIN

lock transition
lock liquidation
lock contract

verify liquidation reconciliation
verify settlement completion policy
verify all accounting requirements

mark liquidation COMPLETED
mark transition COMPLETED
mark contract TERMINATED

append termination event
write outbox
write audit evidence

COMMIT
```

This is the actual termination boundary.

---

# 27. No successor contract

The transaction explicitly verifies:

```java
if (transition.type() == SyirkahTransitionType.FULL_TERMINATION) {
    if (transition.nextContractId() != null) {
        throw new IllegalStateException(
                "Full termination cannot have a successor contract");
    }
}
```

This prevents accidental reuse of the normal transition path.

---

# 28. Final termination event

Use the existing event infrastructure:

```java
public record SyirkahContractTerminated(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        LedgerId ledgerId,
        String contractId,
        int contractVersion,
        String transitionId,
        String liquidationId,
        String closeCycleId,
        String correlationId,
        String causationId
) implements AccountingEvent {
}
```

The event should be emitted only after the transaction has successfully persisted the terminal state.

---

# 29. Audit trail

The termination audit chain should show:

```text
CONTRACT_CREATED
       ↓
VERSION_ACTIVATED
       ↓
CLOSE_STARTED
       ↓
CLOSE_RESULT_CALCULATED
       ↓
SNAPSHOT_FROZEN
       ↓
TRANSITION_CREATED
       ↓
LIQUIDATION_STARTED
       ↓
ASSET_REALIZED
       ↓
LIABILITY_SETTLED
       ↓
LIQUIDATION_RESULT_DETERMINED
       ↓
DISTRIBUTION_POSTED
       ↓
CAPITAL_RETURN_POSTED
       ↓
SETTLEMENT_RECONCILED
       ↓
CONTRACT_TERMINATED
```

This is exactly the kind of chain the existing `SyirkahAuditEvidence` model was designed to capture.

---

# 30. Full termination architecture

We now have:

```text
                    ACTIVE CONTRACT
                          │
                          ▼
                     CLOSE CYCLE
                          │
                          ▼
                 FROZEN CLOSE SNAPSHOT
                          │
                          ▼
                FULL TERMINATION T1
                          │
                          ▼
                     LIQUIDATION
                          │
             ┌────────────┴────────────┐
             ▼                         ▼
      Asset Realization         Liability Settlement
             │                         │
             └────────────┬────────────┘
                          ▼
                    Reconciliation
                          │
                          ▼
                    Final Result
                    /          \
                 PROFIT        LOSS
                   │             │
                   ▼             ▼
              Allocation    Loss Allocation
                   │             │
                   └──────┬──────┘
                          ▼
                    Capital Return
                          │
                          ▼
                    Cash Settlement
                          │
                          ▼
                  Completion Policy
                          │
                          ▼
                    TERMINATED
```

The important architectural outcome is that **termination does not bypass any of the artifacts we already built**:

```text
CloseCalculation
      ↓
SyirkahCloseSnapshot
      ↓
Liquidation
      ↓
Result
      ↓
Obligations
      ↓
Journal
      ↓
Settlement
      ↓
Audit
      ↓
Contract termination
```

So the whole Syirkah lifecycle is now coherent:

```text
                    SYIRKAH LIFECYCLE

        ┌───────────────┐
        │    Contract   │
        └───────┬───────┘
                │
                ▼
        ┌───────────────┐
        │ Active Period │
        └───────┬───────┘
                │
          ┌─────┴─────┐
          │           │
          ▼           ▼
     Normal Close   Transition
          │           │
          ▼           ▼
       Snapshot   Settlement
          │           │
          │      ┌────┴────┐
          │      ▼         ▼
          │   Successor  Termination
          │      │         │
          │      ▼         ▼
          │   New Akad  Liquidation
          │                │
          └───────┬────────┘
                  ▼
             Final State
```

**The next layer should now be the accounting bridge for liquidation**: map asset realization, liability settlement, profit/loss distribution, and capital-return obligations into journal entries with explicit `sourceType/sourceId`, idempotency, and reconciliation—without hardcoding a single universal Sharia accounting treatment.

///////////////

## Next: liquidation accounting bridge

The existing accounting implementation already has `GeneralLedgerService` and `PostJournalEntryCommand`; we should **extend them**, not create a second ledger. The current service, however, mutates account balances before journal persistence, so liquidation posting should go through the transaction/idempotency boundary we identified earlier. syirkah-framework(1)

### 1. Introduce one generic liquidation accounting policy

```java
public interface SyirkahLiquidationAccountingPolicy {

    LiquidationAccountingEntries mapAssetRealization(
            SyirkahLiquidation liquidation,
            LiquidationRealization realization);

    LiquidationAccountingEntries mapLiabilitySettlement(
            SyirkahLiquidation liquidation,
            LiquidationLiabilitySettlement settlement);

    LiquidationAccountingEntries mapResult(
            SyirkahLiquidation liquidation,
            SyirkahLiquidationResult result);

    LiquidationAccountingEntries mapCapitalReturn(
            SyirkahLiquidation liquidation,
            PartnerCapitalReturnObligation obligation);
}
```

The important point is that **the domain determines what happened; the accounting policy determines how it is booked**.

---

## 2. Accounting entries are not journal entries yet

Use an intermediate model:

```java
public record LiquidationAccountingEntries(
        String sourceType,
        String sourceId,
        String description,
        List<LiquidationAccountingLine> lines
) {
    public LiquidationAccountingEntries {
        if (sourceType == null || sourceType.isBlank()) {
            throw new IllegalArgumentException("Source type is required");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("Source ID is required");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Accounting lines are required");
        }
    }
}
```

```java
public record LiquidationAccountingLine(
        String accountId,
        AccountingEntryType type,
        BigDecimal amount,
        String currencyCode,
        String description
) {
}
```

```java
public enum AccountingEntryType {
    DEBIT,
    CREDIT
}
```

This keeps the Syirkah capability independent from the concrete ledger adapter.

---

# 3. Stable source types

Do not use generic `"LIQUIDATION"` for everything.

Use stable source types:

```java
public final class SyirkahAccountingSourceTypes {

    public static final String ASSET_REALIZATION =
            "SYIRKAH_LIQUIDATION_ASSET_REALIZATION";

    public static final String LIABILITY_SETTLEMENT =
            "SYIRKAH_LIQUIDATION_LIABILITY_SETTLEMENT";

    public static final String RESULT =
            "SYIRKAH_LIQUIDATION_RESULT";

    public static final String PROFIT_DISTRIBUTION =
            "SYIRKAH_LIQUIDATION_PROFIT_DISTRIBUTION";

    public static final String LOSS_ALLOCATION =
            "SYIRKAH_LIQUIDATION_LOSS_ALLOCATION";

    public static final String CAPITAL_RETURN =
            "SYIRKAH_LIQUIDATION_CAPITAL_RETURN";

    private SyirkahAccountingSourceTypes() {
    }
}
```

Then every journal can be traced directly back to its business artifact.

---

# 4. Asset realization posting

For example, an asset with:

```text
carrying value = 100m
realized       = 120m
```

could produce, **if the configured accounting policy says so**:

```text
Dr Cash                         120m
    Cr Asset carrying amount       100m
    Cr Realization gain             20m
```

The policy generates those lines.

It should **not** be embedded in `SyirkahLiquidation`.

---

# 5. Loss on realization

Likewise:

```text
carrying value = 100m
realized       = 80m
```

could become:

```text
Dr Cash                          80m
Dr Realization loss              20m
    Cr Asset                      100m
```

Again, this is accounting policy, not the liquidation aggregate.

---

# 6. Liability settlement

Example:

```text
liability = 50m
cash paid = 50m
```

Policy may map:

```text
Dr Liability                    50m
    Cr Cash                     50m
```

The liquidation domain only knows:

```text
outstanding = 50m
settled     = 50m
```

---

# 7. Result distribution

This must remain separate from realization.

Suppose final result:

```text
profit = 30m
A = 60%
B = 40%
```

Business allocation:

```text
A = 18m
B = 12m
```

Then the accounting policy determines whether that becomes, for example:

```text
Dr Syirkah Result / Equity Clearing     30m
    Cr Partner A Distribution             18m
    Cr Partner B Distribution             12m
```

or another treatment appropriate to the configured accounting policy.

**Do not hardcode the above account treatment globally.**

---

# 8. Capital return is separate from profit

Suppose:

```text
A capital = 100m
B capital = 50m

profit = 30m

capital return:
A = 100m
B = 50m
```

There are two different economic events:

```text
profit distribution
capital return
```

Therefore two source records and potentially two journal families.

This prevents the common error of treating:

```text
capital returned
```

as:

```text
expense
```

or:

```text
profit distribution
```

---

# 9. Posting service

Create:

```java
public interface SyirkahLiquidationAccountingService {

    Uni<JournalEntryId> postAssetRealization(
            String liquidationId,
            String realizationId,
            String postedBy);

    Uni<JournalEntryId> postLiabilitySettlement(
            String liquidationId,
            String settlementId,
            String postedBy);

    Uni<List<JournalEntryId>> postResult(
            String liquidationId,
            String resultId,
            String postedBy);

    Uni<JournalEntryId> postCapitalReturn(
            String liquidationId,
            String obligationId,
            String postedBy);
}
```

The service should:

```text
load business artifact
        ↓
verify ownership
        ↓
verify status
        ↓
resolve frozen policy
        ↓
generate accounting lines
        ↓
post idempotently
        ↓
attach journalEntryId
        ↓
mark accounting status POSTED
```

---

# 10. Every business artifact needs accounting state

For example, extend realization with:

```java
public enum AccountingPostingStatus {
    PENDING,
    POSTED,
    FAILED,
    CANCELLED
}
```

Then:

```java
private AccountingPostingStatus accountingStatus;
private String journalEntryId;
```

The important invariant:

```text
POSTED
    => journalEntryId != null
```

and:

```text
journalEntryId != null
    => corresponding journal exists and is POSTED
```

---

# 11. But don't let journal state become the only state

Keep both:

```text
LiquidationRealization
    accountingStatus
    journalEntryId

JournalEntry
    sourceType
    sourceId
```

So reconciliation can run in either direction:

```text
business → accounting
accounting → business
```

---

# 12. Idempotency

Posting:

```text
liquidation realization R123
```

twice must not create two journals.

The call should use:

```text
tenantId
ledgerId
sourceType
sourceId
```

and rely on the database uniqueness constraint:

```sql
CREATE UNIQUE INDEX ux_journal_source
ON journal_entry (
    tenant_id,
    ledger_id,
    source_type,
    source_id
);
```

The application first checks:

```java
journalEntryRepository.findBySource(
        tenantId,
        ledgerId,
        sourceType,
        sourceId
);
```

but the database constraint remains the actual concurrency protection.

---

# 13. Extend `PostJournalEntryCommand`

The existing command already has:

```text
sourceType
sourceId
postedBy
```

so this is a targeted extension rather than a new posting API.

Add:

```java
public record PostJournalEntryCommand(
        JournalEntryId journalEntryId,
        String tenantId,
        String ledgerId,
        String description,
        String referenceNumber,
        List<JournalLineCommand> lines,
        String sourceType,
        String sourceId,
        String postedBy
) implements Command {
}
```

Then `GeneralLedgerService` must construct the journal using the supplied tenant and ledger rather than the current default tenant/primary ledger behavior. syirkah-framework(1)

---

# 14. Transaction boundary

The critical operation is:

```text
BEGIN
    lock business artifact

    check source journal

    resolve accounting policy

    create JournalEntry

    validate journal

    update account balances

    persist JournalEntry

    mark business artifact POSTED
COMMIT
```

Not:

```text
post journal
    ↓
later update liquidation
```

because a failure between those two operations creates an accounting/business-state mismatch.

---

# 15. Reconciliation service

Add:

```java
public interface SyirkahLiquidationAccountingReconciliationService {

    Uni<SyirkahLiquidationAccountingReconciliation>
    reconcile(String liquidationId);
}
```

Result:

```java
public record SyirkahLiquidationAccountingReconciliation(
        String liquidationId,
        BigDecimal assetRealizationAmount,
        BigDecimal liabilitySettlementAmount,
        BigDecimal resultAmount,
        BigDecimal capitalReturnAmount,
        BigDecimal accountedAmount,
        boolean balanced,
        List<String> errors
) {
}
```

---

# 16. Reconciliation must check four dimensions

```text
1. Business artifacts
2. Partner obligations
3. Journal entries
4. Settlement records
```

For example:

```text
Asset realizations
        ↓
expected accounting amount
        ↓
actual journals
```

and:

```text
Capital return obligations
        ↓
expected accounting
        ↓
actual journals
        ↓
settlements
```

---

# 17. Completion gate

`SyirkahLiquidationService.complete()` should refuse completion unless:

```text
close snapshot = frozen
asset realization = complete
liabilities = reconciled
final result = determined
distribution = reconciled
capital return = reconciled
accounting = reconciled
settlement policy = satisfied
```

So:

```java
if (!accountingReconciliation.balanced()) {
    throw new IllegalStateException(
            "Liquidation accounting reconciliation is not balanced");
}
```

---

# 18. Policy snapshot is mandatory

At liquidation completion, don't resolve the current policy dynamically.

Use:

```text
SyirkahCloseSnapshot
    ↓
policySnapshotHash
    ↓
SyirkahLiquidation
    ↓
accounting policy implementation
```

The accounting result must be reproducible years later.

That means the audit evidence should record:

```text
policySnapshotId
policySnapshotHash
implementationKey
implementationVersion
```

---

# 19. The resulting architecture

```text
              SYIRKAH LIQUIDATION
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
 Asset Realization  Liability       Final Result
                    Settlement
        │              │              │
        └──────────────┼──────────────┘
                       ▼
              Accounting Policy
                       │
                       ▼
             Accounting Entries
                       │
                       ▼
              General Ledger
                       │
                       ▼
             Journal Entry
                       │
                       ▼
                Reconciliation
                       │
          ┌────────────┴────────────┐
          ▼                         ▼
   Capital Return              Settlement
          │                         │
          └────────────┬────────────┘
                       ▼
              Completion Policy
                       │
                       ▼
                TERMINATED
```

### One important correction to preserve

The existing framework's `GeneralLedgerService` currently applies account mutations and only then saves the journal. That is not strong enough for this financial workflow. syirkah-framework(1)

So **before implementing the liquidation accounting adapter**, the next engineering step should be to harden the existing journal-posting transaction:

```text
tenant + ledger + source uniqueness
+
atomic account mutation
+
journal persistence
+
business artifact posting state
```

Once that foundation is fixed, the same mechanism can safely serve:

- normal Syirkah distribution
- partner capital movements
- loss recovery
- liquidation realization
- liquidation result
- capital return
- settlement completion

without creating another accounting engine.

////////////


## Next: harden the existing journal posting boundary

Before adding more Syirkah accounting features, this is the right point to fix the existing posting path. The current `PostJournalEntryCommand` already has `sourceType`, `sourceId`, and `postedBy`, while `GeneralLedgerService` currently creates the journal with default tenant/ledger context and mutates accounts before saving the journal. syirkah-framework(1) syirkah-framework(1)

### 1. Extend `PostJournalEntryCommand`

Do not create another command.

```java id="48172"
public record PostJournalEntryCommand(
        JournalEntryId journalEntryId,
        TenantId tenantId,
        LedgerId ledgerId,
        String description,
        String referenceNumber,
        List<JournalLineCommand> lines,
        String sourceType,
        String sourceId,
        String postedBy
) implements Command {

    public PostJournalEntryCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(ledgerId, "ledgerId cannot be null");

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description cannot be empty");
        }

        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException(
                    "Journal entry must have at least one line");
        }

        if (sourceType != null && sourceType.isBlank()) {
            throw new IllegalArgumentException(
                    "Source type cannot be blank");
        }

        if (sourceId != null && sourceId.isBlank()) {
            throw new IllegalArgumentException(
                    "Source ID cannot be blank");
        }
    }
}
```

Use the existing `TenantId` and `LedgerId` types rather than introducing new strings.

---

# 2. Make source identity a first-class invariant

The existing command has source fields, but they currently aren't actually applied to the `JournalEntry`. syirkah-framework(1)

Add to `JournalEntry`:

```java id="73594"
private String sourceType;
private String sourceId;

public void setPostingSource(
        String sourceType,
        String sourceId) {

    if (status != EntryStatus.DRAFT) {
        throw new IllegalStateException(
                "Posting source cannot be changed after draft state");
    }

    if (sourceType == null || sourceType.isBlank()) {
        throw new IllegalArgumentException(
                "Source type is required");
    }

    if (sourceId == null || sourceId.isBlank()) {
        throw new IllegalArgumentException(
                "Source ID is required");
    }

    this.sourceType = sourceType.trim();
    this.sourceId = sourceId.trim();
}

public String getSourceType() {
    return sourceType;
}

public String getSourceId() {
    return sourceId;
}
```

This means:

```text
business artifact
      ↓
sourceType + sourceId
      ↓
JournalEntry
```

becomes traceable.

---

# 3. Repository lookup

Extend the existing repository:

```java id="19683"
Uni<Optional<JournalEntry>> findBySource(
        TenantId tenantId,
        LedgerId ledgerId,
        String sourceType,
        String sourceId
);
```

This is better than:

```java
findBySource(sourceType, sourceId)
```

because source identifiers are only unique **inside tenant + ledger scope**.

---

# 4. Database uniqueness

This is the real idempotency guarantee:

```sql id="32015"
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

Application lookup is an optimization.

The database constraint handles:

```text
Worker A ──┐
           ├── same source
Worker B ──┘
```

when both arrive concurrently.

---

# 5. Introduce the transaction port

The application service should no longer own the database transaction details.

```java id="70231"
public interface JournalPostingTransaction {

    Uni<JournalEntryId> post(
            PostJournalEntryCommand command,
            JournalEntry entry);
}
```

Its implementation owns:

```text
account mutation
journal persistence
source uniqueness
transaction commit
```

---

# 6. General ledger becomes orchestration

The service should evolve toward:

```java id="94371"
public Uni<JournalEntryId> postJournalEntry(
        PostJournalEntryCommand command) {

    return journalEntryRepository.findBySource(
            command.tenantId(),
            command.ledgerId(),
            command.sourceType(),
            command.sourceId()
    ).chain(existing -> {

        if (existing.isPresent()) {
            JournalEntry entry = existing.get();

            if (entry.getStatus() == EntryStatus.POSTED) {
                return Uni.createFrom().item(entry.id());
            }

            return Uni.createFrom().failure(
                    new IllegalStateException(
                            "A journal already exists for source but is not posted: "
                                    + command.sourceType()
                                    + "/"
                                    + command.sourceId()));
        }

        JournalEntry entry = createEntry(command);

        return journalPostingTransaction.post(command, entry);
    });
}
```

This gives us safe retries:

```text
POST request #1
    ↓
journal created
    ↓
network timeout

POST request #2
    ↓
same source
    ↓
existing POSTED journal
    ↓
return same JournalEntryId
```

---

# 7. Entry construction

The current implementation constructs:

```java
new JournalEntry(...)
```

without passing the command's tenant/ledger context. syirkah-framework(1)

Change the creation boundary so the entry receives:

```text
tenantId
ledgerId
```

and then:

```java id="22460"
entry.setPostingSource(
        command.sourceType(),
        command.sourceId()
);
```

No default tenant or primary ledger should be silently substituted for financial postings.

Defaults can still exist at higher-level convenience APIs, but the final posting command should have explicit context.

---

# 8. Account isolation

There is another important issue.

A journal for:

```text
tenant A / ledger X
```

must not mutate an account belonging to:

```text
tenant B / ledger Y
```

So the posting transaction must validate:

```java id="54019"
if (!account.getTenantId().equals(command.tenantId())) {
    throw new IllegalStateException(
            "Account belongs to a different tenant");
}

if (!account.getLedgerId().equals(command.ledgerId())) {
    throw new IllegalStateException(
            "Account belongs to a different ledger");
}
```

This should be enforced in the accounting compliance layer too, not merely the Syirkah adapter.

---

# 9. Atomic sequence

The concrete transaction becomes:

```text id="77164"
BEGIN
 │
 ├─ lock/check source
 │
 ├─ verify fiscal period
 │
 ├─ verify accounts
 │
 ├─ validate journal
 │
 ├─ mutate account balances
 │
 ├─ persist JournalEntry
 │
 └─ COMMIT
```

If anything fails:

```text
ROLLBACK
```

So this bad state cannot happen:

```text
Account balance updated
        ↓
Journal save fails
        ↓
no journal
        ↓
wrong balance
```

---

# 10. Don't fix only Syirkah

This transaction boundary should become the foundation for **all** posting.

Then:

```text
Invoice
Payment
Payroll
Depreciation
FX Revaluation
Syirkah Distribution
Syirkah Liquidation
Partner Capital
Loss Recovery
```

all use:

```text
JournalPostingTransaction
```

instead of implementing their own account mutation logic.

---

# 11. Source types now become a universal accounting contract

For Syirkah:

```text
SYIRKAH_DISTRIBUTION
SYIRKAH_PROFIT_ALLOCATION
SYIRKAH_LOSS_ALLOCATION
SYIRKAH_CAPITAL_CONTRIBUTION
SYIRKAH_CAPITAL_RETURN
SYIRKAH_LIQUIDATION_ASSET_REALIZATION
SYIRKAH_LIQUIDATION_LIABILITY_SETTLEMENT
SYIRKAH_LIQUIDATION_RESULT
SYIRKAH_LIQUIDATION_CAPITAL_RETURN
```

For ordinary accounting:

```text
INVOICE
PAYMENT
DEPRECIATION
FX_REVALUATION
ACCRUAL
```

The important rule:

> `sourceType/sourceId` identifies the business fact that caused the journal. It does not describe the accounting treatment.

That distinction will become very valuable for audit and replay.

---

# 12. Reversal also needs the source identity

The existing reversal implementation creates a reversal and mutates accounts, but it should use the same transaction boundary. syirkah-framework(1)

The reversal should preserve:

```text
original sourceType
original sourceId
```

and additionally identify the reversal:

```java id="21985"
public static final String REVERSAL_SOURCE_TYPE =
        "JOURNAL_REVERSAL";
```

with:

```text
sourceId = originalJournalEntryId
```

while the reversal journal itself retains the original business source metadata for traceability.

---

# 13. Prevent duplicate reversals

Add a uniqueness rule for the reversal relationship:

```sql id="68034"
CREATE UNIQUE INDEX ux_journal_reversal
ON journal_entry (reversal_of_entry_id)
WHERE reversal_of_entry_id IS NOT NULL;
```

Then:

```text
Original JE
    ↓
one reversal
```

rather than:

```text
Original JE
    ↓
Reversal A
Reversal B
Reversal C
```

unless a later explicit business workflow creates a new correction chain.

---

# 14. Outbox belongs in the same transaction

The existing framework already has accounting events/outbox infrastructure.

Therefore final posting should be:

```text id="71402"
BEGIN
   JournalEntry
   Account balances
   OutboxEvent
COMMIT
```

Not:

```text
commit journal
   ↓
publish event
```

with a possibility that the event disappears.

The dispatcher can remain asynchronous.

---

# 15. This gives us the proper Syirkah accounting pipeline

Now the liquidation path is:

```text
LiquidationRealization
        │
        ▼
AccountingPolicy
        │
        ▼
PostJournalEntryCommand
        │
        ├── tenantId
        ├── ledgerId
        ├── sourceType
        └── sourceId
        │
        ▼
GeneralLedgerService
        │
        ▼
JournalPostingTransaction
        │
        ├── Account
        ├── JournalEntry
        └── Outbox
        │
        ▼
AccountingPostingStatus.POSTED
        │
        ▼
Liquidation Reconciliation
```

That is the reusable boundary we want.

---

## Next

With this foundation, the next piece should be **`SyirkahLiquidationAccountingService` itself**: concrete application service, policy resolution, posting each realization/liability/result/capital-return artifact, and making the liquidation completion gate depend on accounting reconciliation.


///////////

Next: implement the **`SyirkahLiquidationAccountingService`** as the application-layer bridge. The important point is that it should orchestrate existing liquidation artifacts and the hardened journal boundary, rather than introduce another accounting engine.

### 1. Accounting posting artifact

```java
package tech.kayys.syirkah.accounting.application.syirkah.liquidation;

import java.util.List;
import java.util.Objects;

public record LiquidationAccountingEntries(
        String sourceType,
        String sourceId,
        String description,
        List<LiquidationAccountingLine> lines
) {
    public LiquidationAccountingEntries {
        if (sourceType == null || sourceType.isBlank()) {
            throw new IllegalArgumentException("Source type is required");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("Source ID is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description is required");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Accounting lines are required");
        }

        lines = List.copyOf(lines);
    }
}
```

```java
package tech.kayys.syirkah.accounting.application.syirkah.liquidation;

import tech.kayys.syirkah.accounting.application.api.command.PostJournalEntryCommand;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record LiquidationAccountingLine(
        UUID accountId,
        String type,
        BigDecimal amount,
        String currencyCode,
        String description
) {
    public LiquidationAccountingLine {
        Objects.requireNonNull(accountId, "Account ID is required");
        Objects.requireNonNull(amount, "Amount is required");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (!"DEBIT".equals(type) && !"CREDIT".equals(type)) {
            throw new IllegalArgumentException("Type must be DEBIT or CREDIT");
        }

        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("Currency code is required");
        }
    }

    public PostJournalEntryCommand.JournalLineCommand toJournalLine() {
        return new PostJournalEntryCommand.JournalLineCommand(
                accountId,
                type,
                amount.toPlainString(),
                currencyCode,
                description
        );
    }
}
```

This intentionally stays between domain and accounting. The liquidation policy decides the accounts; the service merely converts them into the existing journal command.

---

# 2. Accounting policy

The policy remains the only place that understands the chart-of-accounts treatment.

```java
package tech.kayys.syirkah.accounting.application.syirkah.liquidation;

import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.*;
import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.obligation.PartnerCapitalReturnObligation;

public interface SyirkahLiquidationAccountingPolicy {

    LiquidationAccountingEntries mapAssetRealization(
            SyirkahLiquidation liquidation,
            LiquidationRealization realization
    );

    LiquidationAccountingEntries mapLiabilitySettlement(
            SyirkahLiquidation liquidation,
            LiquidationLiabilitySettlement settlement
    );

    LiquidationAccountingEntries mapResult(
            SyirkahLiquidation liquidation,
            SyirkahLiquidationResult result
    );

    LiquidationAccountingEntries mapCapitalReturn(
            SyirkahLiquidation liquidation,
            PartnerCapitalReturnObligation obligation
    );
}
```

Notice that there is **no generic `mapLiquidation()`**.

That is deliberate.

Asset realization, liability settlement, result allocation, and capital return are different business facts and must remain independently auditable.

---

# 3. Posting status on liquidation artifacts

We should now make posting state explicit.

```java
package tech.kayys.syirkah.accounting.domain.syirkah.accounting;

public enum AccountingPostingStatus {
    PENDING,
    POSTED,
    FAILED,
    CANCELLED
}
```

For example, `LiquidationRealization` gets:

```java
private AccountingPostingStatus accountingStatus =
        AccountingPostingStatus.PENDING;

private JournalEntryId journalEntryId;

public void markAccountingPosted(JournalEntryId journalEntryId) {
    if (journalEntryId == null) {
        throw new IllegalArgumentException("Journal entry ID is required");
    }

    if (accountingStatus == AccountingPostingStatus.POSTED) {
        if (!journalEntryId.equals(this.journalEntryId)) {
            throw new IllegalStateException(
                    "Realization is already posted to another journal entry");
        }
        return;
    }

    accountingStatus = AccountingPostingStatus.POSTED;
    this.journalEntryId = journalEntryId;
}

public void markAccountingFailed() {
    if (accountingStatus == AccountingPostingStatus.POSTED) {
        throw new IllegalStateException(
                "Posted accounting cannot be marked failed");
    }

    accountingStatus = AccountingPostingStatus.FAILED;
}

public AccountingPostingStatus getAccountingStatus() {
    return accountingStatus;
}

public JournalEntryId getJournalEntryId() {
    return journalEntryId;
}
```

Apply the same invariant to:

- `LiquidationLiabilitySettlement`
- `SyirkahLiquidationResult` or its accounting artifact
- `PartnerCapitalReturnObligation`

The important invariant is:

```text
POSTED
    => journalEntryId != null

journalEntryId != null
    => corresponding journal entry is POSTED
```

---

# 4. Service interface

Now the actual application service:

```java
package tech.kayys.syirkah.accounting.application.syirkah.liquidation;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;

import java.util.List;

public interface SyirkahLiquidationAccountingService {

    Uni<JournalEntryId> postAssetRealization(
            String liquidationId,
            String realizationId,
            String postedBy
    );

    Uni<JournalEntryId> postLiabilitySettlement(
            String liquidationId,
            String settlementId,
            String postedBy
    );

    Uni<List<JournalEntryId>> postResult(
            String liquidationId,
            String resultId,
            String postedBy
    );

    Uni<JournalEntryId> postCapitalReturn(
            String liquidationId,
            String obligationId,
            String postedBy
    );
}
```

---

# 5. Implementation

The implementation should depend on repositories and the existing journal command service, not directly manipulate `Account`.

```java
package tech.kayys.syirkah.accounting.application.syirkah.liquidation;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.api.command.PostJournalEntryCommand;
import tech.kayys.syirkah.accounting.application.port.JournalEntryRepository;
import tech.kayys.syirkah.accounting.application.spi.AccountingCommandService;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;
import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.*;
import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.obligation.PartnerCapitalReturnObligation;

import java.util.List;
import java.util.Objects;

public class DefaultSyirkahLiquidationAccountingService
        implements SyirkahLiquidationAccountingService {

    private static final String SOURCE_ASSET_REALIZATION =
            "SYIRKAH_LIQUIDATION_ASSET_REALIZATION";

    private static final String SOURCE_LIABILITY_SETTLEMENT =
            "SYIRKAH_LIQUIDATION_LIABILITY_SETTLEMENT";

    private static final String SOURCE_RESULT =
            "SYIRKAH_LIQUIDATION_RESULT";

    private static final String SOURCE_CAPITAL_RETURN =
            "SYIRKAH_LIQUIDATION_CAPITAL_RETURN";

    private final SyirkahLiquidationRepository liquidationRepository;
    private final LiquidationRealizationRepository realizationRepository;
    private final LiquidationLiabilitySettlementRepository liabilitySettlementRepository;
    private final PartnerCapitalReturnObligationRepository capitalReturnRepository;
    private final SyirkahLiquidationResultRepository resultRepository;

    private final SyirkahLiquidationAccountingPolicy accountingPolicy;
    private final AccountingCommandService accountingCommandService;
    private final JournalEntryRepository journalEntryRepository;

    public DefaultSyirkahLiquidationAccountingService(
            SyirkahLiquidationRepository liquidationRepository,
            LiquidationRealizationRepository realizationRepository,
            LiquidationLiabilitySettlementRepository liabilitySettlementRepository,
            PartnerCapitalReturnObligationRepository capitalReturnRepository,
            SyirkahLiquidationResultRepository resultRepository,
            SyirkahLiquidationAccountingPolicy accountingPolicy,
            AccountingCommandService accountingCommandService,
            JournalEntryRepository journalEntryRepository
    ) {
        this.liquidationRepository =
                Objects.requireNonNull(liquidationRepository);

        this.realizationRepository =
                Objects.requireNonNull(realizationRepository);

        this.liabilitySettlementRepository =
                Objects.requireNonNull(liabilitySettlementRepository);

        this.capitalReturnRepository =
                Objects.requireNonNull(capitalReturnRepository);

        this.resultRepository =
                Objects.requireNonNull(resultRepository);

        this.accountingPolicy =
                Objects.requireNonNull(accountingPolicy);

        this.accountingCommandService =
                Objects.requireNonNull(accountingCommandService);

        this.journalEntryRepository =
                Objects.requireNonNull(journalEntryRepository);
    }

    @Override
    public Uni<JournalEntryId> postAssetRealization(
            String liquidationId,
            String realizationId,
            String postedBy
    ) {
        return liquidationRepository.findById(liquidationId)
                .chain(liquidation ->
                        realizationRepository.findById(realizationId)
                                .chain(realization -> {

                                    validateOwnership(
                                            liquidation,
                                            realization.getLiquidationId()
                                    );

                                    if (realization.getJournalEntryId() != null) {
                                        return Uni.createFrom()
                                                .item(realization.getJournalEntryId());
                                    }

                                    LiquidationAccountingEntries entries =
                                            accountingPolicy.mapAssetRealization(
                                                    liquidation,
                                                    realization
                                            );

                                    return post(
                                            liquidation,
                                            entries,
                                            postedBy
                                    ).chain(journalEntryId -> {

                                        realization.markAccountingPosted(
                                                journalEntryId
                                        );

                                        return realizationRepository
                                                .save(realization)
                                                .replaceWith(journalEntryId);
                                    });
                                })
                );
    }

    @Override
    public Uni<JournalEntryId> postLiabilitySettlement(
            String liquidationId,
            String settlementId,
            String postedBy
    ) {
        return liquidationRepository.findById(liquidationId)
                .chain(liquidation ->
                        liabilitySettlementRepository.findById(settlementId)
                                .chain(settlement -> {

                                    validateOwnership(
                                            liquidation,
                                            settlement.getLiquidationId()
                                    );

                                    if (settlement.getJournalEntryId() != null) {
                                        return Uni.createFrom()
                                                .item(settlement.getJournalEntryId());
                                    }

                                    LiquidationAccountingEntries entries =
                                            accountingPolicy.mapLiabilitySettlement(
                                                    liquidation,
                                                    settlement
                                            );

                                    return post(
                                            liquidation,
                                            entries,
                                            postedBy
                                    ).chain(journalEntryId -> {

                                        settlement.markAccountingPosted(
                                                journalEntryId
                                        );

                                        return liabilitySettlementRepository
                                                .save(settlement)
                                                .replaceWith(journalEntryId);
                                    });
                                })
                );
    }

    @Override
    public Uni<List<JournalEntryId>> postResult(
            String liquidationId,
            String resultId,
            String postedBy
    ) {
        return liquidationRepository.findById(liquidationId)
                .chain(liquidation ->
                        resultRepository.findById(resultId)
                                .chain(result -> {

                                    validateOwnership(
                                            liquidation,
                                            result.getLiquidationId()
                                    );

                                    LiquidationAccountingEntries entries =
                                            accountingPolicy.mapResult(
                                                    liquidation,
                                                    result
                                            );

                                    return post(
                                            liquidation,
                                            entries,
                                            postedBy
                                    ).map(List::of);
                                })
                );
    }

    @Override
    public Uni<JournalEntryId> postCapitalReturn(
            String liquidationId,
            String obligationId,
            String postedBy
    ) {
        return liquidationRepository.findById(liquidationId)
                .chain(liquidation ->
                        capitalReturnRepository.findById(obligationId)
                                .chain(obligation -> {

                                    validateOwnership(
                                            liquidation,
                                            obligation.getLiquidationId()
                                    );

                                    if (obligation.getJournalEntryId() != null) {
                                        return Uni.createFrom()
                                                .item(obligation.getJournalEntryId());
                                    }

                                    LiquidationAccountingEntries entries =
                                            accountingPolicy.mapCapitalReturn(
                                                    liquidation,
                                                    obligation
                                            );

                                    return post(
                                            liquidation,
                                            entries,
                                            postedBy
                                    ).chain(journalEntryId -> {

                                        obligation.markAccountingPosted(
                                                journalEntryId
                                        );

                                        return capitalReturnRepository
                                                .save(obligation)
                                                .replaceWith(journalEntryId);
                                    });
                                })
                );
    }

    private Uni<JournalEntryId> post(
            SyirkahLiquidation liquidation,
            LiquidationAccountingEntries entries,
            String postedBy
    ) {
        PostJournalEntryCommand command =
                PostJournalEntryCommand.builder()
                        .tenantId(liquidation.getTenantId())
                        .ledgerId(liquidation.getLedgerId())
                        .description(entries.description())
                        .referenceNumber(
                                "SYIRKAH-LIQ-" +
                                liquidation.getLiquidationId()
                        )
                        .lines(
                                entries.lines()
                                        .stream()
                                        .map(LiquidationAccountingLine::toJournalLine)
                                        .toList()
                        )
                        .sourceType(entries.sourceType())
                        .sourceId(entries.sourceId())
                        .postedBy(postedBy)
                        .build();

        return accountingCommandService.postJournalEntry(command);
    }

    private void validateOwnership(
            SyirkahLiquidation liquidation,
            String artifactLiquidationId
    ) {
        if (!liquidation.getLiquidationId().equals(artifactLiquidationId)) {
            throw new IllegalArgumentException(
                    "Accounting artifact does not belong to liquidation"
            );
        }
    }
}
```

There is one deliberate change I'd make to the earlier design: **the service should not query `JournalEntryRepository` itself for normal idempotency once `AccountingCommandService.postJournalEntry()` is hardened with the source uniqueness boundary.**

That gives us one financial idempotency boundary instead of several competing ones.

---

# 6. Result posting needs one more refinement

A liquidation result can legitimately produce more than one journal.

For example:

```text
Liquidation result
        │
        ├── profit recognition
        ├── partner profit allocation
        └── loss allocation
```

Therefore the policy should eventually return a list:

```java
public interface SyirkahLiquidationAccountingPolicy {

    List<LiquidationAccountingEntries> mapResult(
            SyirkahLiquidation liquidation,
            SyirkahLiquidationResult result
    );

    // ...
}
```

Then:

```java
@Override
public Uni<List<JournalEntryId>> postResult(
        String liquidationId,
        String resultId,
        String postedBy
) {
    return liquidationRepository.findById(liquidationId)
            .chain(liquidation ->
                    resultRepository.findById(resultId)
                            .chain(result -> {

                                validateOwnership(
                                        liquidation,
                                        result.getLiquidationId()
                                );

                                List<LiquidationAccountingEntries> entries =
                                        accountingPolicy.mapResult(
                                                liquidation,
                                                result
                                        );

                                Uni<List<JournalEntryId>> chain =
                                        Uni.createFrom().item(
                                                new java.util.ArrayList<>()
                                        );

                                for (LiquidationAccountingEntries entry :
                                        entries) {

                                    chain = chain.chain(ids ->
                                            post(
                                                    liquidation,
                                                    entry,
                                                    postedBy
                                            ).map(id -> {
                                                ids.add(id);
                                                return ids;
                                            })
                                    );
                                }

                                return chain;
                            })
            );
}
```

However, **this introduces an important atomicity problem**:

```text
result journal #1 -> SUCCESS
result journal #2 -> FAILURE
```

The liquidation result must not become partially accounted.

So this is the point where we should stop treating individual journal posts as independent operations.

---

# 7. Introduce a multi-entry posting transaction

Add:

```java
public interface JournalPostingTransaction {

    Uni<List<JournalEntryId>> postAll(
            PostJournalEntryCommandBatch batch
    );
}
```

And:

```java
public record PostJournalEntryCommandBatch(
        List<PostJournalEntryCommand> commands
) {
    public PostJournalEntryCommandBatch {
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one journal command is required"
            );
        }

        commands = List.copyOf(commands);
    }
}
```

The infrastructure implementation then performs:

```text
BEGIN TRANSACTION

  validate all source keys
  validate all fiscal periods
  validate all accounts
  validate all journals
  mutate all affected account balances
  persist all JournalEntry rows
  persist all outbox events

COMMIT
```

If one fails:

```text
ROLLBACK EVERYTHING
```

This becomes especially important for:

- liquidation result
- profit allocation
- loss allocation
- partner capital return
- close-period distribution

---

# 8. The final liquidation accounting gate

Now the liquidation completion service should no longer ask merely:

```java
if (status == CAPITAL_RETURN) {
    complete();
}
```

It should execute:

```text
                    ┌───────────────────────┐
                    │ Close snapshot frozen │
                    └───────────┬───────────┘
                                ↓
                    ┌───────────────────────┐
                    │ Assets fully realized│
                    └───────────┬───────────┘
                                ↓
                    ┌───────────────────────┐
                    │ Liabilities settled   │
                    └───────────┬───────────┘
                                ↓
                    ┌───────────────────────┐
                    │ Result determined     │
                    └───────────┬───────────┘
                                ↓
                    ┌───────────────────────┐
                    │ Distribution posted   │
                    └───────────┬───────────┘
                                ↓
                    ┌───────────────────────┐
                    │ Capital return posted │
                    └───────────┬───────────┘
                                ↓
                    ┌───────────────────────┐
                    │ Accounting reconcile  │
                    └───────────┬───────────┘
                                ↓
                    ┌───────────────────────┐
                    │ Settlement reconcile  │
                    └───────────┬───────────┘
                                ↓
                    ┌───────────────────────┐
                    │ TERMINATED            │
                    └───────────────────────┘
```

So the next concrete class should be:

```java
public interface SyirkahLiquidationAccountingReconciliationService {

    Uni<SyirkahLiquidationAccountingReconciliation>
    reconcile(String liquidationId);
}
```

with the invariant:

```text
business liquidation artifacts
        =
accounting journals
        =
partner obligations
        =
settlement obligations
```

Only after all four agree should `FULL_TERMINATION` be allowed to complete.

This also gives us the clean boundary:

**Syirkah domain → accounting policy → journal transaction → accounting reconciliation → liquidation completion.**

And importantly, the existing accounting implementation is extended rather than replaced. The source framework already has `AccountingCommandService`, `PostJournalEntryCommand`, `PostJournalEntryHandler`, journal repositories, compliance, projections, and outbox infrastructure, so the Syirkah layer should plug into those rather than create a parallel ledger. syirkah-framework(1) syirkah-framework(1)

////////////

Next, we should close the loop with **liquidation accounting reconciliation**. This is the gate that prevents `FULL_TERMINATION` from completing while business artifacts and GL are out of sync.

## 1. Reconciliation model

```java id="48317"
package tech.kayys.syirkah.accounting.application.syirkah.liquidation;

import java.math.BigDecimal;
import java.util.List;

public record SyirkahLiquidationAccountingReconciliation(
        String liquidationId,
        BigDecimal assetRealizationAmount,
        BigDecimal liabilitySettlementAmount,
        BigDecimal resultAmount,
        BigDecimal capitalReturnAmount,
        BigDecimal accountedAmount,
        boolean balanced,
        List<String> errors
) {
    public SyirkahLiquidationAccountingReconciliation {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean isReadyForCompletion() {
        return balanced && errors.isEmpty();
    }
}
```

I would keep the amounts as explicit reconciliation dimensions rather than only exposing one `accountedAmount`. This makes failures explainable.

---

## 2. Reconciliation source port

Do not make the reconciliation service reconstruct business facts from journal lines.

The business artifacts remain authoritative for **what happened**.

```java id="71942"
package tech.kayys.syirkah.accounting.application.syirkah.liquidation;

import io.smallrye.mutiny.Uni;

import java.math.BigDecimal;

public interface SyirkahLiquidationAccountingFactProvider {

    Uni<BigDecimal> totalAssetRealizations(String liquidationId);

    Uni<BigDecimal> totalLiabilitySettlements(String liquidationId);

    Uni<BigDecimal> totalResultAccountingAmount(String liquidationId);

    Uni<BigDecimal> totalCapitalReturnObligations(String liquidationId);
}
```

And separately:

```java id="28564"
package tech.kayys.syirkah.accounting.application.syirkah.liquidation;

import io.smallrye.mutiny.Uni;

import java.math.BigDecimal;

public interface SyirkahLiquidationJournalProvider {

    Uni<BigDecimal> totalPostedAssetRealizationAmount(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<BigDecimal> totalPostedLiabilitySettlementAmount(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<BigDecimal> totalPostedResultAmount(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<BigDecimal> totalPostedCapitalReturnAmount(
            String tenantId,
            String ledgerId,
            String liquidationId
    );
}
```

This distinction matters:

```text
Business facts
     │
     ├── realization
     ├── liability settlement
     ├── result
     └── capital return
             │
             ↓
      expected accounting
             │
             ↕
       posted journal
```

We should never derive the liquidation result merely by inspecting arbitrary GL accounts.

---

# 3. Reconciliation service

```java id="95420"
package tech.kayys.syirkah.accounting.application.syirkah.liquidation;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.SyirkahLiquidation;
import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.SyirkahLiquidationRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DefaultSyirkahLiquidationAccountingReconciliationService
        implements SyirkahLiquidationAccountingReconciliationService {

    private final SyirkahLiquidationRepository liquidationRepository;
    private final SyirkahLiquidationAccountingFactProvider factProvider;
    private final SyirkahLiquidationJournalProvider journalProvider;

    public DefaultSyirkahLiquidationAccountingReconciliationService(
            SyirkahLiquidationRepository liquidationRepository,
            SyirkahLiquidationAccountingFactProvider factProvider,
            SyirkahLiquidationJournalProvider journalProvider
    ) {
        this.liquidationRepository =
                Objects.requireNonNull(liquidationRepository);

        this.factProvider =
                Objects.requireNonNull(factProvider);

        this.journalProvider =
                Objects.requireNonNull(journalProvider);
    }

    @Override
    public Uni<SyirkahLiquidationAccountingReconciliation> reconcile(
            String liquidationId
    ) {
        return liquidationRepository.findById(liquidationId)
                .chain(liquidation -> reconcileLiquidation(liquidation));
    }

    private Uni<SyirkahLiquidationAccountingReconciliation> reconcileLiquidation(
            SyirkahLiquidation liquidation
    ) {
        String id = liquidation.getLiquidationId();
        String tenantId = liquidation.getTenantId();
        String ledgerId = liquidation.getLedgerId();

        return Uni.combine().all().unis(
                factProvider.totalAssetRealizations(id),
                factProvider.totalLiabilitySettlements(id),
                factProvider.totalResultAccountingAmount(id),
                factProvider.totalCapitalReturnObligations(id),

                journalProvider.totalPostedAssetRealizationAmount(
                        tenantId,
                        ledgerId,
                        id
                ),

                journalProvider.totalPostedLiabilitySettlementAmount(
                        tenantId,
                        ledgerId,
                        id
                ),

                journalProvider.totalPostedResultAmount(
                        tenantId,
                        ledgerId,
                        id
                ),

                journalProvider.totalPostedCapitalReturnAmount(
                        tenantId,
                        ledgerId,
                        id
                )
        ).asTuple()
        .map(tuple -> {

            BigDecimal expectedAssets = tuple.getItem1();
            BigDecimal expectedLiabilities = tuple.getItem2();
            BigDecimal expectedResult = tuple.getItem3();
            BigDecimal expectedCapitalReturn = tuple.getItem4();

            BigDecimal postedAssets = tuple.getItem5();
            BigDecimal postedLiabilities = tuple.getItem6();
            BigDecimal postedResult = tuple.getItem7();
            BigDecimal postedCapitalReturn = tuple.getItem8();

            List<String> errors = new ArrayList<>();

            compare(
                    errors,
                    "asset realization",
                    expectedAssets,
                    postedAssets
            );

            compare(
                    errors,
                    "liability settlement",
                    expectedLiabilities,
                    postedLiabilities
            );

            compare(
                    errors,
                    "result accounting",
                    expectedResult,
                    postedResult
            );

            compare(
                    errors,
                    "capital return",
                    expectedCapitalReturn,
                    postedCapitalReturn
            );

            BigDecimal accountedAmount =
                    postedAssets
                            .subtract(postedLiabilities)
                            .add(postedResult)
                            .add(postedCapitalReturn);

            boolean balanced = errors.isEmpty();

            return new SyirkahLiquidationAccountingReconciliation(
                    id,
                    expectedAssets,
                    expectedLiabilities,
                    expectedResult,
                    expectedCapitalReturn,
                    accountedAmount,
                    balanced,
                    errors
            );
        });
    }

    private void compare(
            List<String> errors,
            String category,
            BigDecimal expected,
            BigDecimal actual
    ) {
        if (expected == null || actual == null) {
            errors.add(
                    category + " reconciliation contains null amount"
            );
            return;
        }

        if (expected.compareTo(actual) != 0) {
            errors.add(
                    category +
                    " mismatch: expected=" +
                    expected +
                    ", posted=" +
                    actual
            );
        }
    }
}
```

The exact arithmetic of `accountedAmount` may eventually need to become policy-specific. The **individual category comparisons**, however, should remain.

---

# 4. Stronger journal query

The journal provider should use the source metadata we added earlier.

For example:

```java id="14835"
public interface JournalEntryRepository {

    Uni<Optional<JournalEntry>> findBySource(
            TenantId tenantId,
            LedgerId ledgerId,
            String sourceType,
            String sourceId
    );

    Uni<List<JournalEntry>> findPostedBySourcePrefix(
            TenantId tenantId,
            LedgerId ledgerId,
            String sourceType,
            String sourceIdPrefix
    );
}
```

But I would actually prefer a more explicit query for reconciliation:

```java id="60392"
public interface JournalEntryRepository {

    Uni<List<JournalEntry>> findPostedBySource(
            TenantId tenantId,
            LedgerId ledgerId,
            String sourceType,
            String sourceId
    );
}
```

Then each business artifact has exactly one source identity.

For example:

```text
SYIRKAH_LIQUIDATION_ASSET_REALIZATION
    sourceId = realizationId

SYIRKAH_LIQUIDATION_LIABILITY_SETTLEMENT
    sourceId = settlementId

SYIRKAH_LIQUIDATION_RESULT
    sourceId = resultId

SYIRKAH_LIQUIDATION_CAPITAL_RETURN
    sourceId = obligationId
```

This is much safer than trying to identify liquidation journals from descriptions or reference numbers.

---

# 5. Detect partial posting

The reconciliation service now catches:

```text
Asset realization:
expected = 100,000,000
posted   = 100,000,000
OK

Liability settlement:
expected = 30,000,000
posted   = 30,000,000
OK

Result:
expected = 20,000,000
posted   = 0
FAIL

Capital return:
expected = 90,000,000
posted   = 90,000,000
OK
```

Result:

```text
balanced = false

errors:
- result accounting mismatch:
  expected=20,000,000, posted=0
```

Therefore:

```text
FULL_TERMINATION
       ↓
BLOCKED
```

rather than silently terminating the contract.

---

# 6. Integrate with liquidation completion

Add the reconciliation dependency to the liquidation service:

```java id="61572"
private final SyirkahLiquidationAccountingReconciliationService
        accountingReconciliationService;
```

Then before completion:

```java id="75164"
public Uni<Void> complete(
        String liquidationId,
        String completedBy
) {
    return liquidationRepository.findById(liquidationId)
            .chain(liquidation ->
                    validateCompletionGates(liquidation)
                            .chain(() ->
                                    accountingReconciliationService
                                            .reconcile(liquidationId)
                            )
                            .chain(reconciliation -> {

                                if (!reconciliation.isReadyForCompletion()) {
                                    return Uni.createFrom().failure(
                                            new IllegalStateException(
                                                    "Liquidation accounting is not reconciled: "
                                                            + reconciliation.errors()
                                            )
                                    );
                                }

                                return settlementReconciliationService
                                        .reconcile(liquidationId);
                            })
                            .chain(settlementReconciliation -> {

                                if (!settlementReconciliation.isReadyForCompletion()) {
                                    return Uni.createFrom().failure(
                                            new IllegalStateException(
                                                    "Liquidation settlements are not complete"
                                            )
                                    );
                                }

                                liquidation.complete(completedBy);

                                return liquidationRepository.save(liquidation);
                            })
            );
}
```

The ordering is intentional:

```text
business completion gates
        ↓
accounting reconciliation
        ↓
settlement reconciliation
        ↓
contract termination
```

---

# 7. One important correction: result accounting must be allocation-aware

For Syirkah, simply comparing one `resultAmount` is not enough.

We need to reconcile the result down to the partner level:

```text
Liquidation result
        │
        ├── Partner A profit allocation
        ├── Partner B profit allocation
        └── Partner C profit allocation
```

or:

```text
Liquidation loss
        │
        ├── Partner A loss allocation
        ├── Partner B loss allocation
        └── responsibility/recovery obligations
```

So add:

```java id="41867"
public interface SyirkahLiquidationPartnerAccountingFactProvider {

    Uni<List<PartnerAccountingFact>> findExpectedAllocations(
            String liquidationId
    );

    record PartnerAccountingFact(
            String partyId,
            String allocationType,
            java.math.BigDecimal amount
    ) {}
}
```

And corresponding journal reconciliation:

```java id="71052"
public interface SyirkahLiquidationPartnerJournalProvider {

    Uni<List<PartnerJournalFact>> findPostedAllocations(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    record PartnerJournalFact(
            String partyId,
            String allocationType,
            java.math.BigDecimal amount
    ) {}
}
```

Then reconcile:

```text
Expected partner allocations
             =
Posted partner allocation journals
```

This prevents an especially dangerous error:

```text
Total result = correct
Partner A    = wrong
Partner B    = wrong
```

The aggregate would appear balanced while the individual Syirkah rights are incorrect.

---

# 8. Final reconciliation structure

I recommend the final object evolve toward:

```java id="64173"
public record SyirkahLiquidationAccountingReconciliation(
        String liquidationId,

        BigDecimal assetRealizationAmount,
        BigDecimal liabilitySettlementAmount,

        BigDecimal resultAmount,
        BigDecimal capitalReturnAmount,

        List<PartnerAllocationReconciliation> partnerAllocations,

        boolean assetRealizationBalanced,
        boolean liabilitySettlementBalanced,
        boolean resultBalanced,
        boolean partnerAllocationBalanced,
        boolean capitalReturnBalanced,

        boolean balanced,
        List<String> errors
) {
    public boolean isReadyForCompletion() {
        return balanced
                && errors.isEmpty()
                && assetRealizationBalanced
                && liabilitySettlementBalanced
                && resultBalanced
                && partnerAllocationBalanced
                && capitalReturnBalanced;
    }
}
```

That gives us the correct final invariant:

```text
                    LIQUIDATION
                         │
        ┌────────────────┼────────────────┐
        ↓                ↓                ↓
    Business          Accounting       Settlement
     facts              facts             facts
        │                │                │
        └──────────── reconciliation ─────┘
                         │
                         ↓
                  COMPLETION GATE
                         │
                         ↓
                    TERMINATED
```

The existing framework already has accounting reconciliation patterns and a substantial accounting application layer, so this remains an extension of the current platform rather than introducing a separate Syirkah ledger. syirkah-framework(1)

**Next:** I would move from liquidation to the other missing close boundary: **`SyirkahDistributionPostingService` + `CloseCycle` integration**, so ordinary periodic `tutup buku` and full liquidation use the same hardened posting/reconciliation mechanics.

/////////////

Next, we should implement **periodic Syirkah distribution posting and connect it to `tutup buku`**.

The existing framework already has `FinancialCloseService`, `CloseCycle`, mandatory close tasks, and hard period locking. syirkah-framework(1) syirkah-framework(1)

The key rule is:

> `TASK_SYIRKAH_DISTRIBUTION` must not become COMPLETED merely because the distribution was calculated. It becomes COMPLETED only after all required accounting postings and reconciliation succeed.

## 1. Distribution posting service

```java
package tech.kayys.syirkah.accounting.application.syirkah.distribution;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;

import java.util.List;

public interface SyirkahDistributionPostingService {

    Uni<List<JournalEntryId>> postApprovedDistributions(
            String tenantId,
            String ledgerId,
            String closeCycleId,
            String postedBy
    );

    Uni<SyirkahDistributionPostingResult> reconcile(
            String tenantId,
            String ledgerId,
            String closeCycleId
    );
}
```

Result:

```java
package tech.kayys.syirkah.accounting.application.syirkah.distribution;

import java.math.BigDecimal;
import java.util.List;

public record SyirkahDistributionPostingResult(
        String closeCycleId,
        BigDecimal expectedAmount,
        BigDecimal postedAmount,
        int expectedObligationCount,
        int postedObligationCount,
        boolean balanced,
        List<String> errors
) {
    public SyirkahDistributionPostingResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean isReadyForClose() {
        return balanced
                && errors.isEmpty()
                && expectedObligationCount == postedObligationCount;
    }
}
```

---

# 2. Keep distribution accounting policy-driven

The existing design should remain:

```java
public interface SyirkahDistributionAccountingPolicy {

    DistributionAccountingEntries map(
            PartnerDistributionObligation obligation
    );
}
```

Do **not** put account IDs directly into `SyirkahDistributionPostingService`.

For example:

```text
PartnerDistributionObligation
          ↓
SyirkahDistributionAccountingPolicy
          ↓
DistributionAccountingEntries
          ↓
PostJournalEntryCommand
          ↓
General Ledger
```

This preserves the separation:

```text
Syirkah domain
    = what happened

Accounting policy
    = how it is booked

Ledger
    = financial record
```

---

# 3. Posting one obligation

The application service should enforce maker/checker state:

```java
private Uni<JournalEntryId> post(
        PartnerDistributionObligation obligation,
        String postedBy
) {
    if (obligation.getStatus()
            != PartnerDistributionObligationStatus.APPROVED) {

        return Uni.createFrom().failure(
                new IllegalStateException(
                        "Distribution obligation must be APPROVED"
                )
        );
    }

    DistributionAccountingEntries entries =
            accountingPolicy.map(obligation);

    PostJournalEntryCommand command =
            PostJournalEntryCommand.builder()
                    .tenantId(obligation.getTenantId())
                    .ledgerId(obligation.getLedgerId())
                    .description(entries.description())
                    .referenceNumber(
                            "SYIRKAH-DISTRIBUTION-" +
                            obligation.getObligationId()
                    )
                    .lines(
                            entries.lines()
                                    .stream()
                                    .map(DistributionAccountingLine::toJournalLine)
                                    .toList()
                    )
                    .sourceType("SYIRKAH_DISTRIBUTION")
                    .sourceId(obligation.getObligationId())
                    .postedBy(postedBy)
                    .build();

    return accountingCommandService
            .postJournalEntry(command)
            .chain(journalEntryId -> {

                obligation.markPosted(
                        journalEntryId,
                        postedBy
                );

                return distributionRepository
                        .save(obligation)
                        .replaceWith(journalEntryId);
            });
}
```

Because the journal source is:

```text
tenant + ledger + SYIRKAH_DISTRIBUTION + obligationId
```

retries remain idempotent once the hardened journal transaction is in place.

---

# 4. Do not post arbitrary obligations during close

The query should explicitly select:

```text
tenant
ledger
closeCycle
status = APPROVED
```

So:

```java
public interface SyirkahDistributionRepository {

    Uni<List<PartnerDistributionObligation>>
    findApprovedForClose(
            String tenantId,
            String ledgerId,
            String closeCycleId
    );

    Uni<List<PartnerDistributionObligation>>
    findForClose(
            String tenantId,
            String ledgerId,
            String closeCycleId
    );

    Uni<PartnerDistributionObligation>
    save(PartnerDistributionObligation obligation);
}
```

This avoids a dangerous situation where a retry accidentally posts a distribution belonging to another period.

---

# 5. Posting all distributions

```java
@Override
public Uni<List<JournalEntryId>> postApprovedDistributions(
        String tenantId,
        String ledgerId,
        String closeCycleId,
        String postedBy
) {
    return distributionRepository
            .findApprovedForClose(
                    tenantId,
                    ledgerId,
                    closeCycleId
            )
            .chain(obligations -> {

                Uni<List<JournalEntryId>> result =
                        Uni.createFrom().item(
                                new java.util.ArrayList<>()
                        );

                for (PartnerDistributionObligation obligation :
                        obligations) {

                    result = result.chain(ids ->
                            post(obligation, postedBy)
                                    .map(journalEntryId -> {
                                        ids.add(journalEntryId);
                                        return ids;
                                    })
                    );
                }

                return result;
            });
}
```

But there is a subtle problem here.

If there are 20 obligations:

```text
1  POSTED
2  POSTED
3  POSTED
4  FAILED
```

the first three remain posted.

For financial close, that may be acceptable **only if each obligation is independently idempotent and reconciliation prevents close completion**.

That gives us a safe retry:

```text
retry
  ↓
1 already POSTED → skip/return existing journal
2 already POSTED → skip/return existing journal
3 already POSTED → skip/return existing journal
4 retry
```

So we should make `post()` explicitly idempotent.

---

# 6. Distribution status machine

Use:

```text
CALCULATED
    ↓
APPROVED
    ↓
POSTED
    ↓
SETTLED
```

Never allow:

```text
CALCULATED → POSTED
```

and never:

```text
APPROVED → SETTLED
```

without the accounting posting.

The domain should enforce this.

```java
public void markPosted(
        JournalEntryId journalEntryId,
        String postedBy
) {
    Objects.requireNonNull(journalEntryId);
    Objects.requireNonNull(postedBy);

    if (status == PartnerDistributionObligationStatus.POSTED) {
        if (!journalEntryId.equals(this.journalEntryId)) {
            throw new IllegalStateException(
                    "Distribution already posted to another journal"
            );
        }
        return;
    }

    if (status != PartnerDistributionObligationStatus.APPROVED) {
        throw new IllegalStateException(
                "Only APPROVED distribution can be posted"
        );
    }

    this.journalEntryId = journalEntryId;
    this.postedBy = postedBy;
    this.postedAt = Instant.now();
    this.status = PartnerDistributionObligationStatus.POSTED;
}
```

---

# 7. Distribution reconciliation

This is the periodic-close equivalent of the liquidation reconciliation we just implemented.

```java
public interface SyirkahDistributionReconciliationService {

    Uni<SyirkahDistributionReconciliation>
    reconcile(
            String tenantId,
            String ledgerId,
            String closeCycleId
    );
}
```

```java
public record SyirkahDistributionReconciliation(
        String closeCycleId,
        BigDecimal calculatedAmount,
        BigDecimal postedAmount,
        BigDecimal settledAmount,
        int calculatedCount,
        int postedCount,
        boolean balanced,
        List<String> errors
) {
    public boolean isReadyForClose() {
        return balanced
                && errors.isEmpty()
                && calculatedCount == postedCount;
    }
}
```

The reconciliation should verify:

```text
calculated obligations
        =
posted obligations
        =
journal source records
```

Settlement is a separate dimension:

```text
posted obligations
        =
settled obligations
```

because an accounting distribution can be POSTED without the cash settlement having occurred yet, depending on the configured settlement policy.

---

# 8. Now connect it to `CloseCycle`

This is the important part.

The current framework's close service can complete arbitrary tasks and then approve/lock the cycle. syirkah-framework(1)

We should not let callers manually complete:

```text
TASK_SYIRKAH_DISTRIBUTION
```

without executing the workflow.

Introduce:

```java
public interface SyirkahCloseTaskService {

    Uni<Void> completeResultTask(
            String closeCycleId,
            String operator
    );

    Uni<Void> completeAllocationTask(
            String closeCycleId,
            String operator
    );

    Uni<Void> completeDistributionTask(
            String closeCycleId,
            String operator
    );
}
```

The distribution operation becomes:

```java
public Uni<Void> completeDistributionTask(
        String closeCycleId,
        String operator
) {
    return closeCycleRepository.findById(closeCycleId)
            .chain(cycle -> {

                assertSyirkahCloseCycle(cycle);

                return distributionPostingService
                        .postApprovedDistributions(
                                cycle.tenantId(),
                                cycle.ledgerId(),
                                cycle.id().value(),
                                operator
                        );
            })
            .chain(ignored ->
                    distributionReconciliationService.reconcile(
                            tenantId,
                            ledgerId,
                            closeCycleId
                    )
            )
            .chain(reconciliation -> {

                if (!reconciliation.isReadyForClose()) {
                    return Uni.createFrom().failure(
                            new IllegalStateException(
                                    "Syirkah distribution is not reconciled: "
                                            + reconciliation.errors()
                            )
                    );
                }

                return closeTaskService.complete(
                        closeCycleId,
                        "TASK_SYIRKAH_DISTRIBUTION",
                        operator
                );
            });
}
```

---

# 9. Strengthen `FinancialCloseService`

The existing service currently exposes:

```java
public void completeTask(
        CloseCycleId cycleId,
        String taskCode,
        String user
)
```

That is too permissive once Syirkah tasks become financially significant.

Keep the generic method for ordinary tasks, but route Syirkah tasks specially:

```java
public Uni<Void> completeTask(
        CloseCycleId cycleId,
        String taskCode,
        String user
) {
    return switch (taskCode) {
        case "TASK_SYIRKAH_RESULT" ->
                syirkahCloseTaskService.completeResultTask(
                        cycleId.value(),
                        user
                );

        case "TASK_SYIRKAH_ALLOCATION" ->
                syirkahCloseTaskService.completeAllocationTask(
                        cycleId.value(),
                        user
                );

        case "TASK_SYIRKAH_DISTRIBUTION" ->
                syirkahCloseTaskService.completeDistributionTask(
                        cycleId.value(),
                        user
                );

        default -> completeOrdinaryTask(
                cycleId,
                taskCode,
                user
        );
    };
}
```

This is a much safer extension than making `CloseTask.complete()` know about accounting, Syirkah, journals, or repositories.

---

# 10. `CloseCycle` should become Syirkah-aware only at task definition

The existing framework's `CloseCycle` currently creates the ordinary task list. syirkah-framework(1)

Extend it with the previously discussed overload:

```java
public static CloseCycle open(
        CloseCycleId id,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,
        boolean syirkahEnabled
) {
    List<CloseTask> tasks = new ArrayList<>();

    tasks.add(new CloseTask(
            "TASK_SUBLEDGER_CUTOFF",
            "Subledger Cut-off",
            CloseTaskKind.SUBLEDGER_CUTOFF,
            true
    ));

    tasks.add(new CloseTask(
            "TASK_ACCRUALS",
            "Post Accruals",
            CloseTaskKind.ACCRUALS,
            true
    ));

    tasks.add(new CloseTask(
            "TASK_DEPRECIATION",
            "Fixed Assets Depreciation",
            CloseTaskKind.DEPRECIATION,
            true
    ));

    tasks.add(new CloseTask(
            "TASK_FX_REVALUATION",
            "Multi-Currency FX Revaluation",
            CloseTaskKind.FX_REVALUATION,
            false
    ));

    tasks.add(new CloseTask(
            "TASK_RECONCILIATION",
            "Subledger & Bank Reconciliation",
            CloseTaskKind.RECONCILIATION,
            true
    ));

    if (syirkahEnabled) {
        tasks.add(new CloseTask(
                "TASK_SYIRKAH_RESULT",
                "Determine Syirkah Result",
                CloseTaskKind.SYIRKAH_RESULT,
                true
        ));

        tasks.add(new CloseTask(
                "TASK_SYIRKAH_ALLOCATION",
                "Allocate Syirkah Result",
                CloseTaskKind.SYIRKAH_ALLOCATION,
                true
        ));

        tasks.add(new CloseTask(
                "TASK_SYIRKAH_DISTRIBUTION",
                "Post Syirkah Distribution",
                CloseTaskKind.SYIRKAH_DISTRIBUTION,
                true
        ));
    }

    tasks.add(new CloseTask(
            "TASK_LOCK",
            "General Ledger Hard Lock",
            CloseTaskKind.LOCK,
            true
    ));

    return new CloseCycle(
            id,
            tenantId,
            ledgerId,
            fiscalPeriodId,
            tasks
    );
}
```

The original four-argument method remains backward compatible.

---

# 11. The complete Syirkah `tutup buku`

We now have:

```text
OPEN
 │
 ├─ subledger cutoff
 ├─ accruals
 ├─ depreciation
 ├─ FX
 ├─ reconciliation
 │
 ├─ SYIRKAH RESULT
 │       ↓
 │   financial result frozen
 │
 ├─ SYIRKAH ALLOCATION
 │       ↓
 │   partner allocations frozen
 │
 ├─ SYIRKAH DISTRIBUTION
 │       ↓
 │   journals posted
 │       ↓
 │   distribution reconciled
 │
 ├─ validation
 │
 ├─ approval
 │
 └─ LOCK
       ↓
   SyirkahCloseSnapshot
```

The important historical boundary is:

```text
Before LOCK
    → live calculations/movements

After LOCK
    → immutable SyirkahCloseSnapshot
```

That means the monthly `tutup buku` is no longer just an accounting lock. It becomes the **legal/business historical boundary** for the Syirkah period.

---

## 12. One more important rule

Do **not** let `approveAndLock()` directly lock the period if a Syirkah close snapshot has not been frozen.

So the final validation becomes:

```text
Mandatory close tasks complete
        +
Syirkah calculation frozen
        +
partner allocation reconciled
        +
distribution accounting reconciled
        +
SyirkahCloseSnapshot frozen
        ↓
APPROVED
        ↓
LOCKED
```

This is the point where `tutup buku` becomes robust enough to support the later:

- partner entry
- partner exit
- partner replacement
- capital change
- contract renewal
- full termination

because those transitions can safely use the locked snapshot as their predecessor state.

**Next:** implement the **`SyirkahCloseSnapshot` creation as an atomic final-close transaction**, including snapshot hash, policy snapshot, partner positions, reconciliation, and the exact point at which `TASK_SYIRKAH_DISTRIBUTION` and `TASK_LOCK` become irreversible.

/////////////////


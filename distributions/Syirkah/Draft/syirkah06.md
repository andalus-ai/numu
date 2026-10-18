The existing framework confirms the important gap: `FiscalPeriodService` currently performs `findById → mutate → save`, while journal posting similarly mutates accounts before persistence. That is not sufficient for the final financial lock boundary. Hukum-muamalah

So the next implementation should move the **finalization transaction below the application services**, where row locking and atomic persistence are guaranteed.

## 1. Transactional SPI

Add a small accounting infrastructure port:

```java id="x4m72"
package tech.kayys.syirkah.accounting.application.port;

import io.smallrye.mutiny.Uni;

import java.util.function.Supplier;

public interface AccountingTransactionManager {

    <T> Uni<T> inTransaction(Supplier<Uni<T>> work);
}
```

Quarkus implementation:

```java id="q8k31"
package tech.kayys.syirkah.accounting.infrastructure.persistence.transaction;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.syirkah.accounting.application.port.AccountingTransactionManager;

import java.util.function.Supplier;

@ApplicationScoped
public class QuarkusAccountingTransactionManager
        implements AccountingTransactionManager {

    @Override
    @WithTransaction
    public <T> Uni<T> inTransaction(Supplier<Uni<T>> work) {
        return work.get();
    }
}
```

The important point is that the transaction manager is an infrastructure concern. Domain code should not know about Hibernate or Panache.

---

# 2. Add pessimistic locking to the fiscal-period port

The existing repository needs a separate method rather than changing every existing `findById`.

```java id="p7c42"
package tech.kayys.syirkah.accounting.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.identifier.FiscalPeriodId;
import tech.kayys.syirkah.accounting.domain.model.FiscalPeriod;

import java.util.List;
import java.util.Optional;

public interface FiscalPeriodRepository {

    Uni<Optional<FiscalPeriod>> findById(FiscalPeriodId id);

    Uni<FiscalPeriod> save(FiscalPeriod period);

    Uni<List<FiscalPeriod>> findAll();

    Uni<Optional<FiscalPeriod>> findActivePeriod(
            java.time.LocalDate date
    );

    Uni<Optional<FiscalPeriod>> findByIdForUpdate(
            FiscalPeriodId id
    );
}
```

The critical method is:

```java
Uni<Optional<FiscalPeriod>> findByIdForUpdate(...)
```

It must translate to:

```sql
SELECT ...
FROM fiscal_period
WHERE id = ?
FOR UPDATE;
```

---

# 3. Tenant-aware version

Because Syirkah is multi-tenant, the production version should preferably make tenant and ledger structural rather than implicit.

```java id="n6q51"
public interface FiscalPeriodRepository {

    Uni<Optional<FiscalPeriod>> findByIdForUpdate(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId
    );

    Uni<FiscalPeriod> save(FiscalPeriod period);
}
```

SQL:

```sql id="r8w21"
SELECT
    id,
    tenant_id,
    ledger_id,
    period_name,
    start_date,
    end_date,
    status,
    locked_at,
    locked_by,
    version
FROM fiscal_period
WHERE tenant_id = ?
  AND ledger_id = ?
  AND id = ?
FOR UPDATE;
```

This is preferable to relying on a default tenant.

The existing `PostJournalEntryHandler` currently constructs entries with `TenantId.defaultTenant()` and `LedgerId.primary()`, which is another boundary that should be corrected as part of the accounting hardening. syirkah-foundation

---

# 4. Correction repository lock

```java id="j7s53"
package tech.kayys.syirkah.accounting.application.correction;

import io.smallrye.mutiny.Uni;

import java.util.Optional;

public interface SyirkahPeriodCorrectionRepository {

    Uni<Optional<SyirkahPeriodCorrection>> findById(
            String tenantId,
            String ledgerId,
            String correctionId
    );

    Uni<Optional<SyirkahPeriodCorrection>> findByIdForUpdate(
            String tenantId,
            String ledgerId,
            String correctionId
    );

    Uni<SyirkahPeriodCorrection> save(
            SyirkahPeriodCorrection correction
    );
}
```

SQL:

```sql id="m4h82"
SELECT *
FROM syirkah_period_correction
WHERE tenant_id = ?
  AND ledger_id = ?
  AND correction_id = ?
FOR UPDATE;
```

---

# 5. Snapshot lock

```java id="v2f91"
package tech.kayys.syirkah.accounting.application.snapshot;

import io.smallrye.mutiny.Uni;

import java.util.Optional;

public interface SyirkahCloseSnapshotRepository {

    Uni<Optional<SyirkahCloseSnapshot>> findById(
            String tenantId,
            String ledgerId,
            String snapshotId
    );

    Uni<Optional<SyirkahCloseSnapshot>> findByIdForUpdate(
            String tenantId,
            String ledgerId,
            String snapshotId
    );

    Uni<SyirkahCloseSnapshot> save(
            SyirkahCloseSnapshot snapshot
    );
}
```

---

# 6. Final transaction implementation

Now the important part.

```java id="c1t85"
package tech.kayys.syirkah.accounting.application.close;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.port.AccountingTransactionManager;
import tech.kayys.syirkah.accounting.application.port.FiscalPeriodRepository;
import tech.kayys.syirkah.accounting.application.correction.SyirkahPeriodCorrection;
import tech.kayys.syirkah.accounting.application.correction.SyirkahPeriodCorrectionRepository;
import tech.kayys.syirkah.accounting.application.snapshot.SyirkahCloseSnapshot;
import tech.kayys.syirkah.accounting.application.snapshot.SyirkahCloseSnapshotRepository;
import tech.kayys.syirkah.accounting.application.snapshot.SyirkahSnapshotIntegrityGate;
import tech.kayys.syirkah.accounting.domain.identifier.FiscalPeriodId;

public final class DefaultAccountingPeriodFinalizationTransaction
        implements AccountingPeriodFinalizationTransaction {

    private final AccountingTransactionManager transactionManager;
    private final FiscalPeriodRepository fiscalPeriodRepository;
    private final SyirkahPeriodCorrectionRepository correctionRepository;
    private final SyirkahCloseSnapshotRepository snapshotRepository;
    private final CloseValidationPipeline finalValidationPipeline;
    private final SyirkahCorrectionFinalizationWriter correctionWriter;
    private final NormalCloseFinalizationWriter normalCloseWriter;

    public DefaultAccountingPeriodFinalizationTransaction(
            AccountingTransactionManager transactionManager,
            FiscalPeriodRepository fiscalPeriodRepository,
            SyirkahPeriodCorrectionRepository correctionRepository,
            SyirkahCloseSnapshotRepository snapshotRepository,
            CloseValidationPipeline finalValidationPipeline,
            SyirkahCorrectionFinalizationWriter correctionWriter,
            NormalCloseFinalizationWriter normalCloseWriter) {

        this.transactionManager = transactionManager;
        this.fiscalPeriodRepository = fiscalPeriodRepository;
        this.correctionRepository = correctionRepository;
        this.snapshotRepository = snapshotRepository;
        this.finalValidationPipeline = finalValidationPipeline;
        this.correctionWriter = correctionWriter;
        this.normalCloseWriter = normalCloseWriter;
    }

    @Override
    public Uni<Void> finalize(
            CloseValidationContext context,
            String operator) {

        return transactionManager.inTransaction(() ->
                lockFinalizationState(context)
                        .chain(() ->
                                finalValidationPipeline.validate(context)
                        )
                        .chain(result -> {

                            if (!result.passed()) {
                                return Uni.createFrom().failure(
                                        new CloseValidationException(
                                                result.errors()
                                        )
                                );
                            }

                            if (context.isCorrection()) {
                                return correctionWriter.finalizeCorrection(
                                        context,
                                        operator
                                );
                            }

                            return normalCloseWriter.finalizeClose(
                                    context,
                                    operator
                            );
                        })
        );
    }

    private Uni<Void> lockFinalizationState(
            CloseValidationContext context) {

        return fiscalPeriodRepository
                .findByIdForUpdate(
                        context.tenantId(),
                        context.ledgerId(),
                        context.fiscalPeriodId()
                )
                .chain(period -> {

                    if (period.isEmpty()) {
                        return Uni.createFrom().failure(
                                new IllegalArgumentException(
                                        "Fiscal period not found."
                                )
                        );
                    }

                    return Uni.createFrom().voidItem();
                })
                .chain(() -> {

                    if (!context.isCorrection()) {
                        return Uni.createFrom().voidItem();
                    }

                    return correctionRepository
                            .findByIdForUpdate(
                                    context.tenantId(),
                                    context.ledgerId(),
                                    context.correctionId()
                            )
                            .chain(correction -> {

                                if (correction.isEmpty()) {
                                    return Uni.createFrom().failure(
                                            new IllegalArgumentException(
                                                    "Syirkah correction not found."
                                            )
                                    );
                                }

                                return Uni.createFrom().voidItem();
                            });
                });
    }
}
```

There is one refinement we should make immediately: the final validation pipeline must operate against the **locked rows**, not independently query unlocked copies and accidentally validate stale state.

So each gate should either:

1. receive the already-loaded locked state, or
2. use repository methods that participate in the same transaction and lock where necessary.

For the most critical aggregates, I prefer option 1.

---

# 7. Finalization state object

```java id="b5d72"
package tech.kayys.syirkah.accounting.application.close;

import tech.kayys.syirkah.accounting.application.correction.SyirkahPeriodCorrection;
import tech.kayys.syirkah.accounting.application.snapshot.SyirkahCloseSnapshot;
import tech.kayys.syirkah.accounting.domain.model.FiscalPeriod;

public record FinalizationState(
        FiscalPeriod fiscalPeriod,
        SyirkahPeriodCorrection correction,
        SyirkahCloseSnapshot originalSnapshot,
        SyirkahCloseSnapshot correctedSnapshot
) {
}
```

Then:

```java id="a9c42"
public interface CloseValidationPipeline {

    Uni<CloseGateResult> validate(
            CloseValidationContext context,
            FinalizationState state
    );
}
```

This is much safer than letting every gate independently load state.

---

# 8. Lock the corrected snapshot too

For correction finalization:

```text id="u6n8p"
1. fiscal_period FOR UPDATE
2. correction FOR UPDATE
3. original_snapshot FOR UPDATE
4. corrected_snapshot FOR UPDATE
```

The corrected snapshot may already have been created before finalization, so we lock it before activating it.

```java id="m3v81"
private Uni<FinalizationState> loadCorrectionState(
        CloseValidationContext context) {

    return correctionRepository
            .findByIdForUpdate(
                    context.tenantId(),
                    context.ledgerId(),
                    context.correctionId()
            )
            .chain(correction -> {

                if (correction.isEmpty()) {
                    return Uni.createFrom().failure(
                            new IllegalArgumentException(
                                    "Correction not found."
                            )
                    );
                }

                SyirkahPeriodCorrection value = correction.get();

                return snapshotRepository
                        .findByIdForUpdate(
                                context.tenantId(),
                                context.ledgerId(),
                                value.getOriginalSnapshotId()
                        )
                        .chain(original -> {

                            if (original.isEmpty()) {
                                return Uni.createFrom().failure(
                                        new IllegalStateException(
                                                "Original snapshot not found."
                                        )
                                );
                            }

                            return snapshotRepository
                                    .findByIdForUpdate(
                                            context.tenantId(),
                                            context.ledgerId(),
                                            value.getNewSnapshotId()
                                    )
                                    .chain(corrected -> {

                                        if (corrected.isEmpty()) {
                                            return Uni.createFrom()
                                                    .failure(
                                                            new IllegalStateException(
                                                                    "Corrected snapshot not found."
                                                            )
                                                    );
                                        }

                                        return Uni.createFrom().item(
                                                new FinalizationState(
                                                        null,
                                                        value,
                                                        original.get(),
                                                        corrected.get()
                                                )
                                        );
                                    });
                        });
            });
}
```

---

# 9. Snapshot swap

Do not rely on two independent saves.

Use an explicit repository operation:

```java id="k4t91"
public interface SyirkahCloseSnapshotLifecycleRepository {

    Uni<Void> activateCorrectedSnapshot(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String originalSnapshotId,
            String correctedSnapshotId
    );
}
```

SQL should execute inside the same transaction:

```sql id="e8m43"
UPDATE syirkah_close_snapshot
SET status = 'SUPERSEDED',
    superseded_by_snapshot_id = ?
WHERE tenant_id = ?
  AND ledger_id = ?
  AND fiscal_period_id = ?
  AND snapshot_id = ?
  AND status = 'ACTIVE';
```

Then:

```sql id="q5c19"
UPDATE syirkah_close_snapshot
SET status = 'ACTIVE'
WHERE tenant_id = ?
  AND ledger_id = ?
  AND fiscal_period_id = ?
  AND snapshot_id = ?
  AND status = 'PENDING';
```

And verify exactly one active snapshot:

```sql id="z3r76"
SELECT COUNT(*)
FROM syirkah_close_snapshot
WHERE tenant_id = ?
  AND ledger_id = ?
  AND fiscal_period_id = ?
  AND status = 'ACTIVE';
```

Expected:

```text
1
```

The partial unique index remains the final database guarantee.

---

# 10. Lock fiscal period last

After the final snapshot is active:

```java id="h7k24"
public interface FiscalPeriodFinalizationRepository {

    Uni<Void> lockPeriod(
            String tenantId,
            String ledgerId,
            String fiscalPeriodId,
            String lockedBy
    );
}
```

SQL:

```sql id="c8s51"
UPDATE fiscal_period
SET status = 'LOCKED',
    locked_by = ?,
    locked_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE tenant_id = ?
  AND ledger_id = ?
  AND id = ?
  AND status = 'OPEN';
```

Then verify:

```sql id="d3v82"
SELECT status
FROM fiscal_period
WHERE tenant_id = ?
  AND ledger_id = ?
  AND id = ?;
```

Expected:

```text
LOCKED
```

The domain aggregate should still validate its legal state transition before persistence.

---

# 11. Correction completion

Only after period lock succeeds:

```java id="s8k12"
correction.markReclosed(
        correctedSnapshotId,
        operator
);
```

Then save.

The ordering is:

```text id="w2j61"
corrected snapshot ACTIVE
        ↓
fiscal period LOCKED
        ↓
correction RECLOSED
```

All three are committed together.

---

# 12. Audit and outbox are inside the same transaction

The framework already contains `OutboxRepository` and projection infrastructure. The current reversal handler persists an outbox event after journal changes, which is the pattern we should retain but move under one actual DB transaction for correction finalization. 

```java id="v9c13"
public interface SyirkahCorrectionCompletionWriter {

    Uni<Void> writeAuditAndOutbox(
            FinalizationState state,
            String operator
    );
}
```

It writes:

```text
CORRECTION_COMPLETION
SNAPSHOT_SUPERSEDED
SNAPSHOT_ACTIVATED
PERIOD_LOCKED
```

as transactional outbox records.

Do **not** directly invoke reporting projections here.

---

# 13. Finalization sequence

The resulting implementation is:

```text id="n6f72"
BEGIN TRANSACTION
    │
    ├── SELECT fiscal_period FOR UPDATE
    │
    ├── SELECT correction FOR UPDATE
    │
    ├── SELECT original snapshot FOR UPDATE
    │
    ├── SELECT corrected snapshot FOR UPDATE
    │
    ├── FINAL VALIDATION
    │     ├── close tasks
    │     ├── journals
    │     ├── result
    │     ├── allocation
    │     ├── responsibility
    │     ├── distribution
    │     ├── settlement
    │     ├── partner positions
    │     └── snapshot integrity
    │
    ├── original snapshot → SUPERSEDED
    │
    ├── corrected snapshot → ACTIVE
    │
    ├── fiscal period → LOCKED
    │
    ├── correction → RECLOSED
    │
    ├── audit evidence
    │
    └── outbox events
          │
       COMMIT
```

If any operation throws:

```text
ROLLBACK
```

and the authoritative state remains unchanged.

---

# 14. Important: don't use `Uni` sequencing as transactionality

This distinction is worth making explicit.

This:

```java
return repository.save(a)
        .chain(() -> repository.save(b))
        .chain(() -> repository.save(c));
```

does **not**, by itself, guarantee:

```text
A + B + C = one transaction
```

The transaction must surround the entire chain:

```java
return transactionManager.inTransaction(() ->
        repository.save(a)
                .chain(() -> repository.save(b))
                .chain(() -> repository.save(c))
);
```

That is the boundary we want throughout accounting corrections.

---

# 15. Concurrency test: normal close versus correction

This is the first integration test I would add.

Scenario:

```text
Period P = OPEN
Correction C = READY_TO_RECLOSE
Snapshot S1 = ACTIVE
Snapshot S2 = PENDING
```

Two workers:

```text
Worker A → normal close
Worker B → correction reclose
```

Expected:

```text
exactly one succeeds
exactly one observes the locked/finalized state
```

JUnit shape:

```java id="q2n58"
@Test
void shouldPreventConcurrentPeriodFinalization() {

    Uni<Void> normalClose =
            finalizationService.finalize(
                    normalCloseContext(),
                    "operator-a"
            );

    Uni<Void> correctionClose =
            finalizationService.finalize(
                    correctionContext(),
                    "operator-b"
            );

    Uni.combine()
            .all()
            .unis(normalClose, correctionClose)
            .asTuple()
            .subscribe()
            .with(
                    ignored -> fail(
                            "Both finalizations must not succeed."
                    ),
                    failure -> {
                        // One transaction must win.
                    }
            );
}
```

For a real integration test, use two independent transaction/session contexts and assert the database state afterward rather than relying on the exact exception ordering.

---

# 16. More important race test

Two correction workers executing the **same correction**:

```text id="g8x41"
Worker A
    ↓
Correction C1

Worker B
    ↓
Correction C1
```

Expected:

```text
one execution lease
one corrected snapshot
one RECLOSED transition
one completion event
```

Database assertions:

```sql
SELECT COUNT(*)
FROM syirkah_close_snapshot
WHERE correction_id = :correctionId
  AND status = 'ACTIVE';
```

Expected:

```text
1
```

And:

```sql
SELECT COUNT(*)
FROM syirkah_correction_action
WHERE correction_id = :correctionId
  AND action_type = 'CREATE_CORRECTED_DISTRIBUTION'
  AND target_id = :distributionId;
```

Expected:

```text
1
```

---

# 17. Regression test for the existing `FiscalPeriodService`

The existing implementation currently allows a simple application-service call to:

```text
findById
→ period.lock()
→ save
```

and similarly:

```text
findById
→ period.reopen()
→ save
```



We should **not delete those APIs immediately**, because existing consumers may depend on them.

Instead, make them delegate to the new authorization/finalization path where appropriate.

For example:

```java id="y6p32"
public Uni<Void> lockPeriod(FiscalPeriodId periodId) {
    return accountingPeriodFinalizationService.finalize(
            buildNormalCloseContext(periodId),
            "system"
    );
}
```

But only if that endpoint is intended to perform a complete accounting close.

If it is retained as a low-level administrative operation, rename/separate it so nobody mistakes:

```text
lockPeriod()
```

for:

```text
tutup buku yang sudah tervalidasi
```

I strongly recommend the latter: **don't let a generic REST `/lock` endpoint bypass the new gates.**

---

# 18. REST API correction

The existing resource exposes:

```text
POST /api/v1/accounting/periods/{id}/lock
POST /api/v1/accounting/periods/{id}/reopen
```



For production Syirkah accounting, these should not directly mutate state.

Instead:

```text
POST /periods/{id}/close
```

→ close workflow

```text
POST /periods/{id}/corrections
```

→ correction request

```text
POST /periods/{id}/corrections/{correctionId}/approve
```

→ maker/checker approval

```text
POST /periods/{id}/corrections/{correctionId}/execute
```

→ correction execution

```text
POST /periods/{id}/corrections/{correctionId}/reclose
```

→ finalization transaction

The old direct `lock`/`reopen` operations should either be removed from the external API or restricted to an explicit internal administrative capability.

---

# 19. One more accounting hardening we should do now

The same transaction boundary needs to be applied to journal posting.

The current framework mutates account balances and then saves the journal entry. Hukum-muamalah

That means the correction finalization can be perfectly transactional while its accounting consequences are still unsafe.

The correct boundary is:

```text id="j2d47"
BEGIN
   │
   ├── validate source/idempotency
   ├── validate fiscal period
   ├── lock affected accounts
   ├── mutate account balances
   ├── persist journal
   ├── persist accounting event
   └── persist outbox
COMMIT
```

So the next implementation after this should be the **transactional journal posting/reversal adapter**, using the already-designed:

```java
JournalPostingTransaction
JournalReversalTransaction
```

and extending them to lock accounts and make journal source identity database-enforced.

That will close the last major hole between the Syirkah correction engine and the underlying general ledger.

///////////

### Next slice: transactional journal posting and reversal

The important change is to make **journal posting the accounting boundary**, not just a service method that happens to update accounts.

The target flow becomes:

```text
POST JOURNAL
  │
  ├─ resolve tenant + ledger
  ├─ idempotency/source lookup
  ├─ validate fiscal period
  ├─ validate journal
  ├─ lock accounts in deterministic order
  ├─ mutate account balances
  ├─ persist journal
  ├─ persist accounting event
  ├─ persist outbox
  └─ COMMIT
```

For correction:

```text
CORRECTION
  │
  └─ REVERSE ORIGINAL JOURNAL
       │
       ├─ original remains immutable
       ├─ create compensating journal
       ├─ mutate accounts
       ├─ persist reversal linkage
       ├─ persist event/outbox
       └─ COMMIT
```

## 1. Extend `PostJournalEntryCommand`

The existing command already has `sourceType` and `sourceId`; add tenant and ledger explicitly rather than allowing handlers to silently use defaults.

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

    public PostJournalEntryCommand {
        requireText(tenantId, "Tenant ID");
        requireText(ledgerId, "Ledger ID");
        requireText(description, "Description");

        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException(
                    "Journal entry must have at least one line");
        }

        if ((sourceType == null) != (sourceId == null)) {
            throw new IllegalArgumentException(
                    "Source type and source ID must be provided together");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
    }
}
```

The builder should expose:

```java
public Builder tenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
}

public Builder ledgerId(String ledgerId) {
    this.ledgerId = ledgerId;
    return this;
}

public Builder sourceType(String sourceType) {
    this.sourceType = sourceType;
    return this;
}

public Builder sourceId(String sourceId) {
    this.sourceId = sourceId;
    return this;
}
```

This is particularly important for:

```text
SYIRKAH_DISTRIBUTION
SYIRKAH_CORRECTION_REVERSAL
SYIRKAH_CORRECTION_ADJUSTMENT
SYIRKAH_LIQUIDATION_*
```

because these are financial consequences that must be traceable to their originating artifact.

---

# 2. Add the transactional posting port

Keep this in the application layer:

```java
public interface JournalPostingTransaction {

    Uni<JournalEntryId> post(PostJournalEntryCommand command);
}
```

And reversal:

```java
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

The correction executor must depend on these ports rather than directly manipulating `JournalEntry`, `Account`, or repositories.

---

# 3. Add account locking to the repository

The existing:

```java
Uni<Optional<Account>> findById(AccountId id);
```

is insufficient for concurrent financial posting.

Add:

```java
public interface AccountRepository {

    Uni<Optional<Account>> findById(AccountId id);

    Uni<Optional<Account>> findByIdForUpdate(
            String tenantId,
            String ledgerId,
            AccountId accountId
    );

    Uni<Void> save(Account account);

    // existing methods...
}
```

SQL implementation:

```sql
SELECT *
FROM account
WHERE tenant_id = ?
  AND ledger_id = ?
  AND id = ?
FOR UPDATE;
```

### Deterministic lock order

Never lock accounts in arbitrary journal-line order.

Instead:

```java
List<AccountId> accountIds = entry.getLines()
        .stream()
        .map(JournalEntry.JournalLine::accountId)
        .distinct()
        .sorted(Comparator.comparing(AccountId::value))
        .toList();
```

Then:

```java
for (AccountId accountId : accountIds) {
    // acquire FOR UPDATE lock
}
```

This substantially reduces deadlock risk when two journals contain overlapping accounts in opposite line order.

---

# 4. Add source lookup to `JournalEntryRepository`

```java
public interface JournalEntryRepository {

    Uni<Optional<JournalEntry>> findById(JournalEntryId id);

    Uni<Optional<JournalEntry>> findBySource(
            TenantId tenantId,
            LedgerId ledgerId,
            String sourceType,
            String sourceId
    );

    Uni<Void> save(JournalEntry entry);

    // ...
}
```

Database protection:

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

The database constraint is the authoritative idempotency mechanism.

An in-memory idempotency guard can still exist as an optimization, but it must never be the financial guarantee.

---

# 5. Harden `GeneralLedgerService`

The existing implementation currently does this:

```text
mutate Account
    ↓
save Account
    ↓
save JournalEntry
```

That is unsafe.

If the application crashes after the account update but before the journal is persisted, the ledger becomes inconsistent.

Replace the implementation with a transaction-oriented service:

```java
public final class TransactionalJournalPostingService
        implements JournalPostingTransaction {

    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;
    private final FiscalPeriodRepository fiscalPeriodRepository;
    private final AccountingTransactionManager transactionManager;
    private final AccountingComplianceEngine complianceEngine;
    private final AccountingEventWriter eventWriter;

    public TransactionalJournalPostingService(
            JournalEntryRepository journalEntryRepository,
            AccountRepository accountRepository,
            FiscalPeriodRepository fiscalPeriodRepository,
            AccountingTransactionManager transactionManager,
            AccountingComplianceEngine complianceEngine,
            AccountingEventWriter eventWriter
    ) {
        this.journalEntryRepository =
                Objects.requireNonNull(journalEntryRepository);
        this.accountRepository =
                Objects.requireNonNull(accountRepository);
        this.fiscalPeriodRepository =
                Objects.requireNonNull(fiscalPeriodRepository);
        this.transactionManager =
                Objects.requireNonNull(transactionManager);
        this.complianceEngine =
                Objects.requireNonNull(complianceEngine);
        this.eventWriter =
                Objects.requireNonNull(eventWriter);
    }

    @Override
    public Uni<JournalEntryId> post(PostJournalEntryCommand command) {
        return transactionManager.inTransaction(
                () -> postInsideTransaction(command)
        );
    }

    private Uni<JournalEntryId> postInsideTransaction(
            PostJournalEntryCommand command
    ) {
        TenantId tenantId = TenantId.of(command.tenantId());
        LedgerId ledgerId = LedgerId.of(command.ledgerId());

        return findExisting(
                tenantId,
                ledgerId,
                command
        ).chain(existing -> {
            if (existing.isPresent()) {
                return handleExisting(existing.get(), command);
            }

            return createAndPost(
                    tenantId,
                    ledgerId,
                    command
            );
        });
    }

    private Uni<Optional<JournalEntry>> findExisting(
            TenantId tenantId,
            LedgerId ledgerId,
            PostJournalEntryCommand command
    ) {
        if (command.sourceType() == null) {
            return Uni.createFrom().item(Optional.empty());
        }

        return journalEntryRepository.findBySource(
                tenantId,
                ledgerId,
                command.sourceType(),
                command.sourceId()
        );
    }

    private Uni<JournalEntryId> handleExisting(
            JournalEntry existing,
            PostJournalEntryCommand command
    ) {
        if (existing.getStatus() == JournalEntry.EntryStatus.POSTED) {
            return Uni.createFrom().item(existing.getId());
        }

        return Uni.createFrom().failure(
                new IllegalStateException(
                        "A journal already exists for source "
                                + command.sourceType()
                                + "/"
                                + command.sourceId()
                                + " but is not posted"
                )
        );
    }

    private Uni<JournalEntryId> createAndPost(
            TenantId tenantId,
            LedgerId ledgerId,
            PostJournalEntryCommand command
    ) {
        JournalEntryId entryId = command.journalEntryId() != null
                ? command.journalEntryId()
                : JournalEntryId.generate();

        JournalEntry entry = new JournalEntry(
                entryId,
                tenantId,
                ledgerId,
                command.referenceNumber() != null
                        ? command.referenceNumber()
                        : generateEntryNumber(),
                Instant.now(),
                command.description()
        );

        entry.setPostingSource(
                command.sourceType(),
                command.sourceId()
        );

        addLines(entry, command);

        complianceEngine.validateJournalEntry(entry);

        LocalDate postingDate =
                LocalDate.ofInstant(
                        entry.getEntryDate(),
                        ZoneOffset.UTC
                );

        return validatePostingPeriod(
                tenantId,
                ledgerId,
                postingDate
        )
        .chain(() -> lockAccounts(entry, tenantId, ledgerId))
        .chain(() -> applyAccountMovements(
                entry,
                tenantId,
                ledgerId
        ))
        .chain(() -> {
            entry.post(
                    command.postedBy() != null
                            ? command.postedBy()
                            : "system"
            );

            return journalEntryRepository.save(entry);
        })
        .chain(() -> eventWriter.writeJournalPosted(entry))
        .map(v -> entryId);
    }

    private void addLines(
            JournalEntry entry,
            PostJournalEntryCommand command
    ) {
        for (PostJournalEntryCommand.JournalLineCommand line
                : command.lines()) {

            AccountId accountId =
                    AccountId.of(line.accountId());

            Money amount = Money.of(
                    new BigDecimal(line.amount()),
                    line.currencyCode()
            );

            Money debit =
                    "DEBIT".equalsIgnoreCase(line.type())
                            ? amount
                            : Money.zero(line.currencyCode());

            Money credit =
                    "CREDIT".equalsIgnoreCase(line.type())
                            ? amount
                            : Money.zero(line.currencyCode());

            entry.addLine(
                    accountId,
                    debit,
                    credit,
                    line.description()
            );
        }
    }

    private Uni<Void> validatePostingPeriod(
            TenantId tenantId,
            LedgerId ledgerId,
            LocalDate postingDate
    ) {
        return fiscalPeriodRepository
                .findActivePeriod(
                        tenantId,
                        ledgerId,
                        postingDate
                )
                .chain(optional -> {
                    if (optional.isEmpty()) {
                        return Uni.createFrom().failure(
                                new IllegalStateException(
                                        "No fiscal period exists for "
                                                + postingDate
                                )
                        );
                    }

                    FiscalPeriod period = optional.get();

                    if (!period.isOpen()) {
                        return Uni.createFrom().failure(
                                new IllegalStateException(
                                        "Fiscal period is not open: "
                                                + period.getPeriodName()
                                )
                        );
                    }

                    return Uni.createFrom().voidItem();
                });
    }

    private Uni<Void> lockAccounts(
            JournalEntry entry,
            TenantId tenantId,
            LedgerId ledgerId
    ) {
        List<AccountId> ids = entry.getLines()
                .stream()
                .map(JournalEntry.JournalLine::accountId)
                .distinct()
                .sorted(Comparator.comparing(AccountId::value))
                .toList();

        Uni<Void> chain = Uni.createFrom().voidItem();

        for (AccountId id : ids) {
            chain = chain.chain(() ->
                    accountRepository.findByIdForUpdate(
                            tenantId,
                            ledgerId,
                            id
                    ).chain(account -> {
                        if (account.isEmpty()) {
                            return Uni.createFrom().failure(
                                    new IllegalArgumentException(
                                            "Account not found: " + id
                                    )
                            );
                        }

                        return Uni.createFrom().voidItem();
                    })
            );
        }

        return chain;
    }

    private Uni<Void> applyAccountMovements(
            JournalEntry entry,
            TenantId tenantId,
            LedgerId ledgerId
    ) {
        Uni<Void> chain = Uni.createFrom().voidItem();

        for (JournalEntry.JournalLine line : entry.getLines()) {
            chain = chain.chain(() ->
                    accountRepository
                            .findByIdForUpdate(
                                    tenantId,
                                    ledgerId,
                                    line.accountId()
                            )
                            .chain(optional -> {
                                if (optional.isEmpty()) {
                                    return Uni.createFrom().failure(
                                            new IllegalArgumentException(
                                                    "Account not found: "
                                                            + line.accountId()
                                            )
                                    );
                                }

                                Account account = optional.get();

                                complianceEngine
                                        .validateAccount(account);

                                if (line.debit().isPositive()) {
                                    account.debit(line.debit());
                                }

                                if (line.credit().isPositive()) {
                                    account.credit(line.credit());
                                }

                                return accountRepository.save(account);
                            })
            );
        }

        return chain;
    }

    private String generateEntryNumber() {
        return "JE-" + UUID.randomUUID()
                .toString()
                .substring(0, 8);
    }
}
```

The exact repository method signatures can be adapted to the existing implementation, but the **ordering and transaction semantics should remain unchanged**.

---

# 6. Important: don't publish projections inside the transaction

The framework currently has code that effectively does:

```java
outboxRepository.save(...)
    .chain(() -> projectionRegistry.publishToProjections(...))
```

That is not appropriate for the new financial boundary.

Instead:

```text
DB transaction
    journal
    accounts
    accounting event
    outbox
COMMIT
        ↓
outbox dispatcher
        ↓
projections
```

So introduce:

```java
public interface AccountingEventWriter {

    Uni<Void> writeJournalPosted(JournalEntry entry);

    Uni<Void> writeJournalReversed(
            JournalEntry original,
            JournalEntry reversal
    );
}
```

Its implementation writes the `AccountingEvent` and `OutboxEvent` in the same database transaction.

Projection execution belongs to the post-commit dispatcher.

---

# 7. Transactional reversal

The reversal must never modify or delete the original journal.

```java
public final class TransactionalJournalReversalService
        implements JournalReversalTransaction {

    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;
    private final FiscalPeriodRepository fiscalPeriodRepository;
    private final AccountingTransactionManager transactionManager;
    private final AccountingEventWriter eventWriter;

    @Override
    public Uni<JournalEntryId> reverse(
            JournalEntryId originalEntryId,
            String tenantId,
            String ledgerId,
            String correctionId,
            String reason,
            String reversedBy
    ) {
        return transactionManager.inTransaction(
                () -> reverseInsideTransaction(
                        originalEntryId,
                        tenantId,
                        ledgerId,
                        correctionId,
                        reason,
                        reversedBy
                )
        );
    }

    private Uni<JournalEntryId> reverseInsideTransaction(
            JournalEntryId originalEntryId,
            String tenantId,
            String ledgerId,
            String correctionId,
            String reason,
            String reversedBy
    ) {
        TenantId tenant = TenantId.of(tenantId);
        LedgerId ledger = LedgerId.of(ledgerId);

        return journalEntryRepository
                .findByIdForUpdate(
                        tenant,
                        ledger,
                        originalEntryId
                )
                .chain(optional -> {
                    if (optional.isEmpty()) {
                        return Uni.createFrom().failure(
                                new IllegalArgumentException(
                                        "Journal entry not found: "
                                                + originalEntryId
                                )
                        );
                    }

                    JournalEntry original = optional.get();

                    if (original.getStatus()
                            != JournalEntry.EntryStatus.POSTED) {
                        return Uni.createFrom().failure(
                                new IllegalStateException(
                                        "Only posted journals can be reversed"
                                )
                        );
                    }

                    return createReversal(
                            original,
                            tenant,
                            ledger,
                            correctionId,
                            reason,
                            reversedBy
                    );
                });
    }

    private Uni<JournalEntryId> createReversal(
            JournalEntry original,
            TenantId tenant,
            LedgerId ledger,
            String correctionId,
            String reason,
            String reversedBy
    ) {
        String sourceId =
                correctionId + ":" + original.getId().value();

        return journalEntryRepository.findBySource(
                tenant,
                ledger,
                "SYIRKAH_CORRECTION_REVERSAL",
                sourceId
        ).chain(existing -> {

            if (existing.isPresent()) {
                return Uni.createFrom()
                        .item(existing.get().getId());
            }

            JournalEntryId reversalId =
                    JournalEntryId.generate();

            JournalEntry reversal =
                    original.createStornoReversal(
                            reversalId,
                            "REV-" + original.getEntryNumber()
                                    + "-"
                                    + UUID.randomUUID()
                                    .toString()
                                    .substring(0, 6),
                            Instant.now(),
                            reversedBy,
                            reason
                    );

            reversal.setPostingSource(
                    "SYIRKAH_CORRECTION_REVERSAL",
                    sourceId
            );

            LocalDate postingDate =
                    LocalDate.ofInstant(
                            reversal.getEntryDate(),
                            ZoneOffset.UTC
                    );

            return fiscalPeriodRepository
                    .findActivePeriod(
                            tenant,
                            ledger,
                            postingDate
                    )
                    .chain(period -> {
                        if (period.isEmpty()
                                || !period.get().isOpen()) {
                            return Uni.createFrom().failure(
                                    new IllegalStateException(
                                            "Correction reversal cannot "
                                                    + "be posted into a "
                                                    + "closed period"
                                    )
                            );
                        }

                        return applyReversalAccounts(
                                reversal,
                                tenant,
                                ledger
                        );
                    })
                    .chain(() -> {
                        reversal.post(reversedBy);

                        original.markReversed(
                                reversalId,
                                Instant.now(),
                                reversedBy
                        );

                        return journalEntryRepository
                                .save(original);
                    })
                    .chain(() ->
                            journalEntryRepository.save(reversal))
                    .chain(() ->
                            eventWriter.writeJournalReversed(
                                    original,
                                    reversal
                            ))
                    .map(v -> reversalId);
        });
    }

    private Uni<Void> applyReversalAccounts(
            JournalEntry reversal,
            TenantId tenant,
            LedgerId ledger
    ) {
        List<AccountId> accountIds = reversal.getLines()
                .stream()
                .map(JournalEntry.JournalLine::accountId)
                .distinct()
                .sorted(Comparator.comparing(AccountId::value))
                .toList();

        Uni<Void> lockChain =
                Uni.createFrom().voidItem();

        for (AccountId id : accountIds) {
            lockChain = lockChain.chain(() ->
                    accountRepository.findByIdForUpdate(
                            tenant,
                            ledger,
                            id
                    ).chain(account -> {
                        if (account.isEmpty()) {
                            return Uni.createFrom().failure(
                                    new IllegalArgumentException(
                                            "Account not found: " + id
                                    )
                            );
                        }

                        return Uni.createFrom().voidItem();
                    })
            );
        }

        return lockChain.chain(() -> {
            Uni<Void> mutation =
                    Uni.createFrom().voidItem();

            for (JournalEntry.JournalLine line
                    : reversal.getLines()) {

                mutation = mutation.chain(() ->
                        accountRepository
                                .findByIdForUpdate(
                                        tenant,
                                        ledger,
                                        line.accountId()
                                )
                                .chain(account -> {
                                    Account value = account.orElseThrow(
                                            () -> new IllegalArgumentException(
                                                    "Account not found: "
                                                            + line.accountId()
                                            )
                                    );

                                    if (line.debit().isPositive()) {
                                        value.debit(line.debit());
                                    }

                                    if (line.credit().isPositive()) {
                                        value.credit(line.credit());
                                    }

                                    return accountRepository
                                            .save(value);
                                })
                );
            }

            return mutation;
        });
    }
}
```

One subtle but important point: **the reversal itself must be posted into an open correction period**.

If the original period has been reopened specifically for correction, the correction workflow controls that state. If the original period is still locked, reversal must fail.

---

# 8. Add reversal linkage explicitly

The journal should have first-class lineage rather than relying only on the description.

Add:

```java
private JournalEntryId reversesJournalEntryId;
private JournalEntryId reversedByJournalEntryId;
```

Methods:

```java
public void markReversed(
        JournalEntryId reversalId,
        Instant reversedAt,
        String reversedBy
) {
    if (status != EntryStatus.POSTED) {
        throw new IllegalStateException(
                "Only posted journal entries can be reversed"
        );
    }

    if (reversedByJournalEntryId != null) {
        throw new IllegalStateException(
                "Journal entry has already been reversed"
        );
    }

    this.reversedByJournalEntryId = reversalId;
    this.reversedAt = reversedAt;
    this.reversedBy = reversedBy;
    this.status = EntryStatus.REVERSED;
}
```

And the reversal:

```java
reversal.reversesJournalEntryId = original.getId();
```

Database:

```sql
ALTER TABLE journal_entry
    ADD COLUMN reverses_journal_entry_id VARCHAR(100);

ALTER TABLE journal_entry
    ADD COLUMN reversed_by_journal_entry_id VARCHAR(100);

CREATE INDEX ix_journal_entry_reverses
ON journal_entry (
    tenant_id,
    ledger_id,
    reverses_journal_entry_id
);
```

This gives us:

```text
original journal
      │
      └── reversed by
             │
             ▼
       reversal journal
```

instead of destroying history.

---

# 9. Correction executor must use this boundary

The correction action:

```java
REVERSE_JOURNAL
```

should now be implemented as:

```java
public Uni<Void> execute(
        SyirkahCorrectionAction action,
        SyirkahCorrectionContext context,
        String operator
) {
    return journalReversalTransaction.reverse(
            JournalEntryId.of(action.targetId()),
            context.tenantId(),
            context.ledgerId(),
            context.correctionId(),
            action.reason(),
            operator
    )
    .chain(reversalId ->
            actionRepository.markCompleted(
                    action.actionId(),
                    reversalId.value().toString()
            )
    );
}
```

Do **not** do this:

```java
journalEntryRepository.findById(...)
accountRepository.findById(...)
accountRepository.save(...)
journalEntryRepository.save(...)
```

inside the correction executor.

That would recreate the exact consistency problem we are fixing.

---

# 10. Correction adjustment journals

For a changed Syirkah result, reversal alone is not enough.

The normal pattern becomes:

```text
Original result journal
        │
        ▼
Correction reversal
        │
        ▼
Corrected result calculation
        │
        ▼
Adjustment journal
```

Use:

```java
public static final String CORRECTION_REVERSAL =
        "SYIRKAH_CORRECTION_REVERSAL";

public static final String CORRECTION_ADJUSTMENT =
        "SYIRKAH_CORRECTION_ADJUSTMENT";
```

And stable IDs:

```text
SYIRKAH_CORRECTION_REVERSAL:
    {correctionId}:{originalJournalId}

SYIRKAH_CORRECTION_ADJUSTMENT:
    {correctionId}:{artifactId}:{revision}
```

This makes retries deterministic.

---

# 11. The correction sequence is now stronger

The full correction path should therefore be:

```text
REQUESTED
    ↓
APPROVED
    ↓
REOPENED
    ↓
CORRECTING
    │
    ├── reverse affected journals
    │
    ├── recalculate financial result
    │
    ├── recalculate partner allocations
    │
    ├── recalculate loss responsibility
    │
    ├── create accounting adjustments
    │
    ├── recalculate distributions
    │
    ├── reconcile settlements
    │
    ├── rebuild partner positions
    │
    └── create corrected snapshot
    ↓
READY_TO_RECLOSE
    ↓
FINAL VALIDATION
    ↓
ATOMIC TRANSACTION
    ├── old snapshot → SUPERSEDED
    ├── corrected snapshot → ACTIVE
    ├── fiscal period → LOCKED
    ├── correction → RECLOSED
    └── completion events → OUTBOX
    ↓
COMMIT
```

### One more guard is necessary

The journal posting service should reject any journal whose tenant/ledger does not match the fiscal period.

That prevents a subtle cross-tenant/cross-ledger corruption path:

```java
if (!period.getTenantId().equals(tenantId)) {
    throw new IllegalStateException(
            "Fiscal period belongs to another tenant"
    );
}

if (!period.getLedgerId().equals(ledgerId)) {
    throw new IllegalStateException(
            "Fiscal period belongs to another ledger"
    );
}
```

And the same tenant/ledger invariant should be enforced at the database level wherever practical.

---

## Tests to add immediately

These are now mandatory regression tests:

```java
@Test
void postingSameSourceTwiceDoesNotMutateBalancesTwice() {
    // first post -> balances change
    // second post -> same journal ID returned
    // balances remain unchanged
}
```

```java
@Test
void postingIntoLockedPeriodFailsBeforeAccountMutation() {
    // locked period
    // posting fails
    // account balances unchanged
    // journal does not exist
}
```

```java
@Test
void postingFailureRollsBackAccountMutationAndJournal() {
    // account mutation occurs
    // journal persistence deliberately fails
    // transaction rolls everything back
}
```

```java
@Test
void reversalNeverDeletesOrMutatesOriginalLines() {
    // original remains historical
    // reversal contains opposite lines
    // original references reversal
    // reversal references original
}
```

```java
@Test
void correctionReversalIsIdempotent() {
    // execute same correction action twice
    // exactly one reversal journal
    // exactly one balance mutation
}
```

```java
@Test
void concurrentPostingLocksAccountsInDeterministicOrder() {
    // opposing line orders
    // concurrent transactions
    // no deadlock
}
```

The next layer after this is to connect **`SyirkahCorrectionAction` → transactional journal boundary → partner movement correction**, so accounting journals and partner-position movements cannot diverge during a locked-period correction.

////////////

The source also reveals a correction in the journal model itself: `markReversed()` currently stores the **reversal journal ID in a field named `reversalOfEntryId`**. That is semantically inverted. We should fix that before wiring partner movements.

## 1. Correct journal reversal lineage

Use two distinct fields:

```java
private JournalEntryId reversalOfEntryId;
private JournalEntryId reversedByEntryId;
```

Meaning:

```text
reversal journal:
    reversalOfEntryId = original journal

original journal:
    reversedByEntryId = reversal journal
```

Methods:

```java
public void markReversed(
        JournalEntryId reversalEntryId,
        Instant at,
        String by
) {
    if (status != EntryStatus.POSTED) {
        throw new IllegalStateException(
                "Only posted entries can be reversed"
        );
    }

    if (reversedByEntryId != null) {
        throw new IllegalStateException(
                "Journal entry has already been reversed"
        );
    }

    this.status = EntryStatus.REVERSED;
    this.reversedByEntryId = reversalEntryId;
    this.reversalDate = at;
    this.reversedBy = by;
}
```

And:

```java
public JournalEntry createStornoReversal(
        JournalEntryId newId,
        String newEntryNumber,
        Instant newEntryDate,
        String by,
        String reason
) {
    if (this.status != EntryStatus.POSTED) {
        throw new IllegalStateException(
                "Cannot create reversal for unposted entry"
        );
    }

    JournalEntry reversal = new JournalEntry(
            newId,
            tenantId,
            ledgerId,
            newEntryNumber,
            newEntryDate,
            "REVERSAL [" + entryNumber + "]: "
                    + (reason != null ? reason : description)
    );

    reversal.shariaContractType = shariaContractType;
    reversal.reversalOfEntryId = this.id;
    reversal.reversedBy = by;
    reversal.reversalDate = newEntryDate;

    for (JournalLine line : lines) {
        reversal.addLine(new JournalLine(
                line.accountId(),
                line.credit(),
                line.debit(),
                "Reversal of line: " + line.description(),
                line.contractType(),
                line.baseCredit(),
                line.baseDebit(),
                line.dimensions()
        ));
    }

    return reversal;
}
```

Also add:

```java
public JournalEntryId getReversedByEntryId() {
    return reversedByEntryId;
}

public boolean isReversal() {
    return reversalOfEntryId != null;
}
```

The existing domain event raised from `createStornoReversal()` should be moved to the transactional application boundary. Creating a domain object should not imply that the reversal was successfully persisted.

---

# 2. Now connect partner movements to accounting

The crucial Syirkah invariant is:

```text
Partner economic movement
        ↕
Accounting journal
```

Neither one should become authoritative independently.

For example, profit allocation:

```text
Financial result
      ↓
Partner allocation
      ↓
PartnerAccountMovement
      ↓
Accounting journal
      ↓
POSTED
```

A movement must **not** become `POSTED` merely because a journal command was constructed.

It becomes posted only after the journal transaction commits.

---

# 3. Strengthen `PartnerAccountMovement`

The existing states are good:

```java
public enum MovementStatus {
    PENDING_ACCOUNTING,
    POSTED,
    CANCELLED
}
```

Add immutable lineage:

```java
public final class PartnerAccountMovement {

    private final String movementId;
    private final String tenantId;
    private final String ledgerId;
    private final String contractId;
    private final String contractVersion;
    private final String partyId;
    private final MovementType type;
    private final Money amount;
    private final LocalDate effectiveDate;

    private final String sourceType;
    private final String sourceId;

    private final String correctionId;
    private final String supersedesMovementId;

    private MovementStatus status;

    private JournalEntryId journalEntryId;

    // ...
}
```

The important fields are:

```text
sourceType
sourceId
correctionId
supersedesMovementId
journalEntryId
```

They make a correction reconstructable.

---

# 4. Never mutate a posted movement

Add:

```java
public void attachAccountingJournal(
        JournalEntryId journalEntryId
) {
    if (status != MovementStatus.PENDING_ACCOUNTING) {
        throw new IllegalStateException(
                "Accounting journal can only be attached "
                        + "to a pending movement"
        );
    }

    this.journalEntryId = Objects.requireNonNull(
            journalEntryId
    );

    this.status = MovementStatus.POSTED;
}
```

And:

```java
public void cancel(String reason) {
    if (status == MovementStatus.POSTED) {
        throw new IllegalStateException(
                "Posted partner movements cannot be cancelled"
        );
    }

    this.status = MovementStatus.CANCELLED;
}
```

For correction, create a **compensating movement**, rather than changing the original.

Example:

```text
Original:
    PROFIT_ALLOCATION
    +1,000,000

Correction:
    PROFIT_ALLOCATION_REVERSAL
    -1,000,000

Corrected:
    PROFIT_ALLOCATION
    +850,000
```

The historical `+1,000,000` remains intact.

---

# 5. Add explicit movement correction types

Extend:

```java
public enum MovementType {

    CAPITAL_CONTRIBUTION,
    PROFIT_ALLOCATION,
    LOSS_ALLOCATION,
    CAPITAL_RETURN,
    CAPITAL_IMPAIRMENT,
    PARTNER_WITHDRAWAL,
    CASH_SETTLEMENT,

    CORRECTION_REVERSAL,
    CORRECTION_ADJUSTMENT
}
```

But the original economic type should remain discoverable.

For example:

```java
public record CorrectionMovementMetadata(
        String correctionId,
        String originalMovementId,
        MovementType originalType
) {}
```

This is better than attempting to infer correction meaning from a negative amount.

A negative profit allocation is not necessarily a reversal.

---

# 6. Introduce a movement transaction boundary

```java
public interface PartnerMovementAccountingTransaction {

    Uni<JournalEntryId> postMovement(
            PartnerAccountMovement movement,
            PostJournalEntryCommand journalCommand
    );

    Uni<JournalEntryId> reverseMovement(
            PartnerAccountMovement movement,
            String correctionId,
            String reason,
            String operator
    );
}
```

Implementation:

```java
public final class DefaultPartnerMovementAccountingTransaction
        implements PartnerMovementAccountingTransaction {

    private final AccountingTransactionManager transactionManager;
    private final PartnerAccountMovementRepository movementRepository;
    private final JournalPostingTransaction journalPostingTransaction;
    private final JournalReversalTransaction journalReversalTransaction;

    @Override
    public Uni<JournalEntryId> postMovement(
            PartnerAccountMovement movement,
            PostJournalEntryCommand journalCommand
    ) {
        return transactionManager.inTransaction(() ->
                movementRepository
                        .findByIdForUpdate(
                                movement.getTenantId(),
                                movement.getLedgerId(),
                                movement.getMovementId()
                        )
                        .chain(existing -> {
                            if (existing.isEmpty()) {
                                return Uni.createFrom().failure(
                                        new IllegalArgumentException(
                                                "Partner movement not found"
                                        )
                                );
                            }

                            PartnerAccountMovement current =
                                    existing.get();

                            if (current.isPosted()) {
                                return Uni.createFrom()
                                        .item(current.getJournalEntryId());
                            }

                            if (!current.isPendingAccounting()) {
                                return Uni.createFrom().failure(
                                        new IllegalStateException(
                                                "Movement cannot be posted: "
                                                        + current.getStatus()
                                        )
                                );
                            }

                            return journalPostingTransaction
                                    .post(journalCommand)
                                    .chain(journalId -> {
                                        current.attachAccountingJournal(
                                                journalId
                                        );

                                        return movementRepository
                                                .save(current)
                                                .replaceWith(journalId);
                                    });
                        })
        );
    }

    @Override
    public Uni<JournalEntryId> reverseMovement(
            PartnerAccountMovement movement,
            String correctionId,
            String reason,
            String operator
    ) {
        if (!movement.isPosted()) {
            return Uni.createFrom().failure(
                    new IllegalStateException(
                            "Only posted movements can be corrected"
                    )
            );
        }

        return journalReversalTransaction.reverse(
                movement.getJournalEntryId(),
                movement.getTenantId(),
                movement.getLedgerId(),
                correctionId,
                reason,
                operator
        );
    }
}
```

This is the key boundary:

```text
movement + journal
       ↓
ONE transaction
```

---

# 7. But there is one transaction detail to fix

The previous `TransactionalJournalPostingService` should **not itself open a nested transaction** when called from the partner-movement transaction.

Otherwise we can accidentally get:

```text
Transaction A
    movement
    └── Transaction B
          journal
```

which is not the atomicity we want.

Use two layers:

```java
public interface JournalPostingTransaction {

    Uni<JournalEntryId> post(PostJournalEntryCommand command);
}
```

and an internal transactional primitive:

```java
public interface JournalPostingOperations {

    Uni<JournalEntryId> postInsideTransaction(
            PostJournalEntryCommand command
    );
}
```

Then:

```text
External journal posting
    → transactionManager
       → postInsideTransaction()

Partner movement posting
    → transactionManager
       → movement
       → postInsideTransaction()
       → movement POSTED
```

So the actual transaction is:

```text
BEGIN
    lock partner movement
    lock fiscal period
    lock accounts

    validate journal

    mutate accounts
    save journal

    movement.journalEntryId = journalId
    movement.status = POSTED
    save movement

    accounting event
    outbox
COMMIT
```

This is the correct boundary.

---

# 8. Correction becomes symmetrical

For a correction:

```text
BEGIN
    lock original movement
    lock original journal
    validate reopened period

    create compensating journal
    mutate accounts

    create compensating partner movement

    persist correction lineage
    outbox
COMMIT
```

The original remains:

```text
POSTED
```

and its replacement chain is:

```text
Original movement
      │
      ├── reversal movement
      │       │
      │       └── reversal journal
      │
      └── corrected movement
              │
              └── adjustment journal
```

---

# 9. Add movement lineage repository queries

```java
public interface PartnerAccountMovementRepository {

    Uni<Optional<PartnerAccountMovement>> findByIdForUpdate(
            String tenantId,
            String ledgerId,
            String movementId
    );

    Uni<List<PartnerAccountMovement>> findBySource(
            String tenantId,
            String ledgerId,
            String sourceType,
            String sourceId
    );

    Uni<List<PartnerAccountMovement>> findByCorrection(
            String tenantId,
            String ledgerId,
            String correctionId
    );

    Uni<List<PartnerAccountMovement>> findByOriginalMovement(
            String tenantId,
            String ledgerId,
            String movementId
    );

    Uni<Void> save(PartnerAccountMovement movement);
}
```

Database indexes:

```sql
CREATE INDEX ix_partner_movement_source
ON partner_account_movement (
    tenant_id,
    ledger_id,
    source_type,
    source_id
);

CREATE INDEX ix_partner_movement_correction
ON partner_account_movement (
    tenant_id,
    ledger_id,
    correction_id
);

CREATE INDEX ix_partner_movement_original
ON partner_account_movement (
    tenant_id,
    ledger_id,
    supersedes_movement_id
);
```

---

# 10. Rebuild partner position from movements

This is especially important for correction.

Do **not** do:

```java
partnerAccount.setCurrentBalance(correctedBalance);
```

Instead:

```java
public Uni<PartnerPosition> rebuildPosition(
        String tenantId,
        String ledgerId,
        String contractId,
        String partyId
) {
    return movementRepository.findPostedByPartner(
            tenantId,
            ledgerId,
            contractId,
            partyId
    ).map(movements -> {
        Money capital = Money.zero(
                movements.get(0).getAmount().currency()
        );

        Money currentAccount = capital;

        for (PartnerAccountMovement movement : movements) {
            switch (movement.getType()) {
                case CAPITAL_CONTRIBUTION ->
                        capital = capital.add(
                                movement.getAmount()
                        );

                case CAPITAL_RETURN,
                     CAPITAL_IMPAIRMENT ->
                        capital = capital.subtract(
                                movement.getAmount()
                        );

                case PROFIT_ALLOCATION ->
                        currentAccount = currentAccount.add(
                                movement.getAmount()
                        );

                case LOSS_ALLOCATION,
                     PARTNER_WITHDRAWAL ->
                        currentAccount = currentAccount.subtract(
                                movement.getAmount()
                        );

                default -> {
                    // Cash settlement is tracked separately.
                }
            }
        }

        return new PartnerPosition(
                capital,
                currentAccount
        );
    });
}
```

The actual implementation should use the existing opening snapshot as the boundary rather than replaying the entire lifetime of the contract.

The important principle is:

```text
Opening snapshot
+
POSTED movements
=
Closing position
```

not:

```text
mutable PartnerAccount
=
source of truth
```

---

# 11. Correction reconciliation becomes explicit

Add:

```java
public record PartnerMovementAccountingReconciliation(
        String correctionId,
        long originalMovementCount,
        long reversalMovementCount,
        long correctedMovementCount,
        Money originalNetAmount,
        Money reversalNetAmount,
        Money correctedNetAmount,
        Money resultingNetAmount,
        List<String> errors
) {

    public boolean isBalanced() {
        return errors.isEmpty()
                && originalNetAmount
                    .add(reversalNetAmount)
                    .add(correctedNetAmount)
                    .equals(resultingNetAmount);
    }
}
```

More importantly, compare accounting and partner movement totals by artifact:

```text
Partner allocation delta
        =
Journal delta
```

For every affected allocation:

```text
old allocation
new allocation
delta = new - old
```

Then:

```text
partner movement adjustment = delta
accounting adjustment       = delta
```

If those differ, correction cannot reach `READY_TO_RECLOSE`.

---

# 12. Add this as a finalization gate

Extend the existing validation pipeline with:

```java
public final class PartnerMovementAccountingReconciliationGate
        implements CloseValidationGate {

    private final PartnerMovementAccountingReconciliationService
            reconciliationService;

    @Override
    public Uni<CloseGateResult> validate(
            CloseValidationContext context,
            FinalizationState state
    ) {
        return reconciliationService
                .reconcile(
                        context.tenantId(),
                        context.ledgerId(),
                        context.fiscalPeriodId(),
                        context.correctionId()
                )
                .map(result -> {
                    if (result.isBalanced()) {
                        return CloseGateResult.passed();
                    }

                    return CloseGateResult.failed(
                            result.errors()
                    );
                });
    }
}
```

For correction close ordering:

```text
CLOSE_TASK_GATE
JOURNAL_RECONCILIATION_GATE
SYIRKAH_RESULT_GATE
PARTNER_MOVEMENT_ACCOUNTING_GATE
PARTNER_POSITION_GATE
DISTRIBUTION_SETTLEMENT_GATE
SNAPSHOT_INTEGRITY_GATE
CORRECTION_GATE
```

This means a corrected snapshot can never become active if the partner-side and accounting-side representations disagree.

---

# 13. The final invariant

At this point the correction subsystem should enforce:

```text
                 BUSINESS RESULT
                       │
                       ▼
               PARTNER ALLOCATION
                  │          │
                  │          │
                  ▼          ▼
          PARTNER MOVEMENT  JOURNAL
                  │          │
                  └────┬─────┘
                       ▼
                RECONCILIATION
                       │
                       ▼
                PARTNER POSITION
                       │
                       ▼
               CORRECTED SNAPSHOT
                       │
                       ▼
                  REPORTING
```

And correction:

```text
OLD ARTIFACT
    │
    ├── remains immutable
    │
    └── compensation
            │
            └── corrected artifact
```

That gives Syirkah the right historical behavior for **tutup buku**: reopening a period does not erase the old accounting or partner position; it creates an auditable correction chain and produces a new authoritative snapshot only after everything reconciles.

The next slice should be the **distribution/settlement side**, because that is where a correction becomes materially more difficult: an allocation may already have been paid, so the engine needs to distinguish **unsettled, partially settled, fully settled, over-settled, and under-settled** corrections and produce the appropriate recovery/offset/refund obligation rather than silently rewriting the distribution.

////////////

The next extension is the **distribution/settlement correction layer**. The key rule is: **settlement history is immutable**. A correction creates compensating obligations; it never changes an already-settled payment.

### 1. Settlement state

```java
package tech.kayys.syirkah.accounting.domain.syirkah.distribution;

import java.math.BigDecimal;

public enum SyirkahSettlementState {
    UNSETTLED,
    PARTIALLY_SETTLED,
    FULLY_SETTLED,
    UNDER_SETTLED,
    OVER_SETTLED;

    public static SyirkahSettlementState determine(
            BigDecimal obligationAmount,
            BigDecimal settledAmount
    ) {
        int comparison = settledAmount.compareTo(obligationAmount);

        if (settledAmount.signum() == 0) {
            return UNSETTLED;
        }

        if (comparison < 0) {
            return PARTIALLY_SETTLED;
        }

        if (comparison == 0) {
            return FULLY_SETTLED;
        }

        return OVER_SETTLED;
    }
}
```

`UNDER_SETTLED` is better treated as a **correction outcome** rather than the original settlement state:

```java
package tech.kayys.syirkah.accounting.domain.syirkah.distribution;

public enum SyirkahSettlementCorrectionState {
    NO_CHANGE,
    REDUCED_OUTSTANDING,
    ADDITIONAL_SETTLEMENT_REQUIRED,
    RECOVERY_REQUIRED,
    REFUND_REQUIRED,
    OFFSET_REQUIRED,
    MANUAL_REVIEW
}
```

### 2. Correction decision

```java
package tech.kayys.syirkah.accounting.domain.syirkah.distribution;

import java.math.BigDecimal;
import java.util.Objects;

public record SyirkahSettlementCorrectionDecision(
        SyirkahSettlementState originalSettlementState,
        SyirkahSettlementCorrectionState correctionState,
        BigDecimal originalObligationAmount,
        BigDecimal correctedObligationAmount,
        BigDecimal settledAmount,
        BigDecimal deltaAmount,
        String reason
) {

    public SyirkahSettlementCorrectionDecision {
        Objects.requireNonNull(originalSettlementState);
        Objects.requireNonNull(correctionState);
        Objects.requireNonNull(originalObligationAmount);
        Objects.requireNonNull(correctedObligationAmount);
        Objects.requireNonNull(settledAmount);
        Objects.requireNonNull(deltaAmount);
        Objects.requireNonNull(reason);
    }
}
```

### 3. Policy-controlled decision engine

Do not hard-code assumptions about refund, recovery, or offset into the accounting engine. Those are **business/policy decisions**, and where Islamic-law treatment is required, the applicable policy must be explicit.

```java
package tech.kayys.syirkah.accounting.domain.syirkah.distribution;

import java.math.BigDecimal;

public interface SyirkahSettlementCorrectionPolicy {

    SyirkahSettlementCorrectionState determine(
            SyirkahSettlementState settlementState,
            BigDecimal originalObligationAmount,
            BigDecimal correctedObligationAmount,
            BigDecimal settledAmount
    );
}
```

Default accounting policy:

```java
package tech.kayys.syirkah.accounting.domain.syirkah.distribution;

import java.math.BigDecimal;

public final class DefaultSyirkahSettlementCorrectionPolicy
        implements SyirkahSettlementCorrectionPolicy {

    @Override
    public SyirkahSettlementCorrectionState determine(
            SyirkahSettlementState settlementState,
            BigDecimal originalObligationAmount,
            BigDecimal correctedObligationAmount,
            BigDecimal settledAmount
    ) {
        int delta = correctedObligationAmount.compareTo(settledAmount);

        if (delta == 0) {
            return SyirkahSettlementCorrectionState.NO_CHANGE;
        }

        if (delta > 0) {
            return SyirkahSettlementCorrectionState.ADDITIONAL_SETTLEMENT_REQUIRED;
        }

        return switch (settlementState) {
            case UNSETTLED ->
                    SyirkahSettlementCorrectionState.REDUCED_OUTSTANDING;

            case PARTIALLY_SETTLED ->
                    SyirkahSettlementCorrectionState.RECOVERY_REQUIRED;

            case FULLY_SETTLED, OVER_SETTLED ->
                    SyirkahSettlementCorrectionState.RECOVERY_REQUIRED;

            case UNDER_SETTLED ->
                    SyirkahSettlementCorrectionState.MANUAL_REVIEW;
        };
    }
}
```

The important calculation is:

```text
corrected obligation
        -
settled amount
        =
remaining economic position
```

For example:

```text
Original obligation       100
Settled                    60
Corrected obligation       40
--------------------------------
Settled exceeds corrected  20
```

The original payment remains `60`. The correction creates a `20` recovery/offset/refund obligation according to policy.

---

# 4. Distribution correction context

```java
package tech.kayys.syirkah.accounting.domain.syirkah.distribution;

import java.math.BigDecimal;
import java.util.Objects;

public record SyirkahDistributionCorrectionContext(
        String tenantId,
        String ledgerId,
        String correctionId,
        String originalObligationId,
        BigDecimal originalObligationAmount,
        BigDecimal correctedObligationAmount,
        BigDecimal settledAmount,
        String currency
) {

    public SyirkahDistributionCorrectionContext {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(ledgerId);
        Objects.requireNonNull(correctionId);
        Objects.requireNonNull(originalObligationId);
        Objects.requireNonNull(originalObligationAmount);
        Objects.requireNonNull(correctedObligationAmount);
        Objects.requireNonNull(settledAmount);
        Objects.requireNonNull(currency);
    }

    public SyirkahSettlementState settlementState() {
        return SyirkahSettlementState.determine(
                originalObligationAmount,
                settledAmount
        );
    }

    public BigDecimal delta() {
        return correctedObligationAmount.subtract(settledAmount);
    }
}
```

---

# 5. Extend `PartnerDistributionObligation`

The existing lifecycle should remain:

```text
CALCULATED
    ↓
APPROVED
    ↓
POSTED
    ↓
SETTLED
```

with `CANCELLED` for obligations that have never become settled.

Add lineage rather than modifying historical amounts:

```java
private String correctionId;
private String supersedesObligationId;
private String correctedByObligationId;
private BigDecimal settledAmount;
private String settlementReference;
```

Useful accessors:

```java
public String correctionId() {
    return correctionId;
}

public String supersedesObligationId() {
    return supersedesObligationId;
}

public String correctedByObligationId() {
    return correctedByObligationId;
}

public BigDecimal settledAmount() {
    return settledAmount;
}

public String settlementReference() {
    return settlementReference;
}
```

And settlement must be explicit:

```java
public void markSettled(
        BigDecimal settledAmount,
        String settlementReference
) {
    if (status != PartnerDistributionObligationStatus.POSTED) {
        throw new IllegalStateException(
                "Only POSTED obligations can be settled"
        );
    }

    if (settledAmount == null || settledAmount.signum() <= 0) {
        throw new IllegalArgumentException(
                "settledAmount must be positive"
        );
    }

    if (settledAmount.compareTo(amount) > 0) {
        throw new IllegalStateException(
                "Settlement exceeds distribution obligation"
        );
    }

    this.settledAmount = settledAmount;
    this.settlementReference = settlementReference;

    if (settledAmount.compareTo(amount) == 0) {
        this.status = PartnerDistributionObligationStatus.SETTLED;
    }
}
```

For partial settlement, keep the obligation `POSTED` with `settledAmount < amount`.

That is important because:

```text
status = POSTED
settledAmount = 60
amount = 100
```

means **40 remains outstanding**.

---

# 6. Settlement records should be append-only

Do not make the obligation itself the complete payment history.

Introduce:

```java
public record SyirkahDistributionSettlement(
        String id,
        String tenantId,
        String ledgerId,
        String obligationId,
        BigDecimal amount,
        String currency,
        String settlementReference,
        java.time.Instant settledAt,
        String settledBy,
        String correctionId
) {
}
```

Then:

```text
PartnerDistributionObligation
        │
        ├── Settlement #1 = 40
        ├── Settlement #2 = 20
        └── Settlement #3 = 40
```

The historical settlements are never rewritten.

The repository should provide:

```java
public interface SyirkahDistributionSettlementRepository {

    Uni<List<SyirkahDistributionSettlement>> findByObligation(
            String tenantId,
            String ledgerId,
            String obligationId
    );

    Uni<BigDecimal> sumSettledAmount(
            String tenantId,
            String ledgerId,
            String obligationId
    );

    Uni<Optional<SyirkahDistributionSettlement>> findByReference(
            String tenantId,
            String ledgerId,
            String settlementReference
    );

    Uni<Void> save(SyirkahDistributionSettlement settlement);
}
```

Add idempotency at database level:

```sql
CREATE UNIQUE INDEX ux_syirkah_distribution_settlement_reference
ON syirkah_distribution_settlement (
    tenant_id,
    ledger_id,
    settlement_reference
);
```

---

# 7. Correction service

```java
public interface SyirkahDistributionCorrectionService {

    Uni<SyirkahSettlementCorrectionDecision> analyze(
            SyirkahDistributionCorrectionContext context
    );

    Uni<SyirkahDistributionCorrectionResult> execute(
            SyirkahDistributionCorrectionContext context,
            String operator
    );
}
```

Result:

```java
public record SyirkahDistributionCorrectionResult(
        String correctionId,
        String originalObligationId,
        String replacementObligationId,
        String recoveryObligationId,
        String refundObligationId,
        String offsetObligationId,
        SyirkahSettlementCorrectionState state
) {
}
```

---

# 8. Core correction matrix

This should become a mandatory test matrix.

| Original | Settled | Corrected | Result |
|---:|---:|---:|---|
| 100 | 0 | 80 | Replace original with 80 |
| 100 | 0 | 120 | Replace original with 120 |
| 100 | 60 | 80 | Additional 20 required |
| 100 | 60 | 40 | Recovery/offset 20 |
| 100 | 100 | 120 | Additional 20 required |
| 100 | 100 | 80 | Recovery/offset 20 |
| 100 | 120 | 80 | Over-settlement 40 → policy/manual review |

The particularly important invariant is:

```text
Historical settlements
        +
Correction obligations
        =
Corrected economic position
```

---

# 9. Unsettled correction

For:

```text
Original = 100
Settled  = 0
Corrected = 80
```

the old obligation can safely be superseded because nothing has been paid.

```text
OBLIGATION-001
amount = 100
status = CANCELLED
supersededBy = OBLIGATION-002

OBLIGATION-002
amount = 80
status = CALCULATED
correctionId = CORR-001
supersedesObligationId = OBLIGATION-001
```

No historical payment needs compensation.

---

# 10. Partially settled correction

For:

```text
Original = 100
Settled  = 60
Corrected = 80
```

do **not** cancel the original payment.

The corrected outstanding amount is:

```text
80 - 60 = 20
```

The resulting structure is:

```text
Original obligation
    amount = 100
    settled = 60
    historical = immutable

Corrected obligation
    amount = 80
    settled against existing settlement history = 60
    outstanding = 20
```

The implementation should link the corrected obligation to the original rather than duplicating the settlement.

---

# 11. Fully settled downward correction

For:

```text
Original   = 100
Settled    = 100
Corrected  = 80
Difference = 20
```

Never do:

```java
obligation.setAmount(BigDecimal.valueOf(80));
```

and never change the payment from `100` to `80`.

Instead:

```text
Original distribution       100 SETTLED
             │
             └── correction
                    │
                    └── recovery/offset/refund obligation = 20
```

The exact disposition comes from:

```java
SyirkahSettlementCorrectionPolicy
```

and should require maker/checker approval where your existing control model requires it.

---

# 12. Fully settled upward correction

For:

```text
Original  = 100
Settled   = 100
Corrected = 120
```

create:

```text
Original obligation = 100 SETTLED

Additional corrected obligation
    amount = 20
    correctionId = CORR-001
    supersedes/adjusts original = OBLIGATION-001
```

Do **not** recreate a 120 payment obligation on top of the original 100, otherwise the system could pay 220.

---

# 13. Accounting linkage

The distribution correction must use the same transactional journal boundary already established.

Conceptually:

```text
BEGIN

  lock correction

  lock original distribution obligation

  load immutable settlement history

  calculate corrected obligation

  create compensating distribution artifact

  create recovery/refund/offset obligation if required

  post accounting adjustment
      ↓
  journal
      ↓
  accounting event
      ↓
  outbox

COMMIT
```

The accounting source should remain deterministic:

```java
public static final String CORRECTION_REVERSAL =
        "SYIRKAH_CORRECTION_REVERSAL";

public static final String CORRECTION_ADJUSTMENT =
        "SYIRKAH_CORRECTION_ADJUSTMENT";
```

For example:

```text
SYIRKAH_CORRECTION_REVERSAL
    sourceId = CORR-001:OBLIGATION-001

SYIRKAH_CORRECTION_ADJUSTMENT
    sourceId = CORR-001:RECOVERY-001
```

This keeps journal posting idempotent.

---

# 14. Add the settlement reconciliation gate

The existing close pipeline should now gain a dedicated gate:

```java
public final class DistributionSettlementCorrectionGate
        implements CloseValidationGate {

    private final SyirkahDistributionSettlementReconciliationService
            reconciliationService;

    @Override
    public Uni<CloseGateResult> validate(
            CloseValidationContext context
    ) {
        return reconciliationService
                .reconcile(
                        context.tenantId(),
                        context.ledgerId(),
                        context.fiscalPeriodId(),
                        context.correctionId()
                )
                .map(result ->
                        result.isBalanced()
                                ? CloseGateResult.passed()
                                : CloseGateResult.failed(result.errors())
                );
    }
}
```

Ordering becomes:

```text
CLOSE_TASK_GATE
        ↓
JOURNAL_RECONCILIATION_GATE
        ↓
SYIRKAH_RESULT_GATE
        ↓
PARTNER_MOVEMENT_ACCOUNTING_GATE
        ↓
DISTRIBUTION_SETTLEMENT_CORRECTION_GATE
        ↓
PARTNER_POSITION_GATE
        ↓
DISTRIBUTION_SETTLEMENT_GATE
        ↓
SNAPSHOT_INTEGRITY_GATE
        ↓
CORRECTION_GATE
```

The important new invariant is:

```text
original distribution
+ reversal
+ corrected distribution
+ recovery/refund/offset
=
corrected distribution economic position
```

and independently:

```text
partner movement delta
=
accounting journal delta
=
distribution correction delta
```

If those three do not reconcile, the correction **cannot become `READY_TO_RECLOSE`**.

### Resulting architecture

The correction path is now:

```text
Business correction
       │
       ▼
Financial result
       │
       ▼
Partner allocation
       │
       ├──────────────► Loss responsibility
       │
       ▼
Distribution obligation
       │
       ▼
Settlement history
       │
       ├── unsettled
       ├── partially settled
       ├── fully settled
       └── over-settled
       │
       ▼
Settlement correction decision
       │
       ├── replacement obligation
       ├── additional obligation
       ├── recovery obligation
       ├── refund obligation
       └── offset obligation
       │
       ▼
Accounting adjustment
       │
       ▼
Partner movement correction
       │
       ▼
Partner position rebuild
       │
       ▼
Corrected snapshot
       │
       ▼
Reclose
```

This closes an important hole: **a Syirkah correction can now safely cross the boundary from accounting allocation into real settlement without rewriting historical money movements.**

/////////////

Yes. At this point I would **stop adding isolated classes and move into implementation hardening + completion of the Syirkah correction lifecycle**.

The roadmap should preserve the existing ERP/accounting implementation and add only the Syirkah/Sharia-specific extensions.

## Syirkah implementation roadmap

### Phase 0 — Baseline & integration inventory
**Goal:** make sure our extensions fit the existing implementation instead of duplicating it.

- Map existing accounting aggregates/services/repositories.
- Identify existing transaction boundaries.
- Identify existing event/outbox infrastructure.
- Identify existing distribution/payment/settlement implementation.
- Identify existing close/reopen APIs.
- Identify existing tests and integration-test conventions.
- Mark proposed classes as:
  - **extend existing**
  - **new capability**
  - **replace unsafe implementation**
  - **adapter only**

**Deliverable:** Syirkah implementation map.

---

# Phase 1 — Accounting transaction hardening

This is the foundation for everything else.

### 1.1 Journal posting
Implement:

- `JournalPostingTransaction`
- `JournalPostingOperations`
- tenant/ledger-aware `PostJournalEntryCommand`
- source idempotency
- fiscal-period validation
- `FOR UPDATE` account locking
- deterministic account lock ordering
- atomic account + journal + event + outbox persistence.

### 1.2 Journal reversal

Fix:

- reversal lineage
- `reversalOfEntryId`
- `reversedByEntryId`
- reversal source identity
- idempotency
- correction-period validation
- atomic reversal/account movement.

### 1.3 Journal source integrity

Add:

- source uniqueness
- source lookup
- source metadata on `JournalEntry`
- duplicate-post protection.

**Milestone:** ordinary accounting posting and reversal are transaction-safe.

---

# Phase 2 — Syirkah partner movement ledger

Make partner movements a proper append-only financial subledger.

Implement:

- immutable `PartnerAccountMovement`
- `CORRECTION_REVERSAL`
- `CORRECTION_ADJUSTMENT`
- `correctionId`
- `supersedesMovementId`
- movement locking
- source idempotency
- movement ↔ journal atomicity
- movement accounting reconciliation.

Then establish the invariant:

```text
Partner Movement
       ↕
Accounting Journal
```

must reconcile exactly.

**Milestone:** partner balances are derived from financial history, not mutable balance edits.

---

# Phase 3 — Close, snapshot and locked-period correction

Complete the close lifecycle.

### Normal close

```text
OPEN
 ↓
CLOSING
 ↓
VALIDATED
 ↓
LOCKED
```

### Correction

```text
LOCKED
 ↓
CORRECTION REQUESTED
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

Implement/harden:

- close task ordering
- Syirkah result task
- allocation task
- distribution task
- correction reopen
- correction execution
- correction action ledger
- final validation
- snapshot supersession
- corrected snapshot activation
- fiscal period lock
- atomic `RECLOSED`.

**Milestone:** a locked Syirkah period can be corrected without destroying its audit history.

---

# Phase 4 — Syirkah result and allocation engine

Now make the economic calculation layer production-grade.

### 4.1 Result

Implement:

- financial result calculation
- profit/loss/break-even
- calculation ID
- input provenance
- policy snapshot.

### 4.2 Profit allocation

Implement:

- nisbah resolution
- effective contract version
- partner allocation
- allocation delta calculation
- allocation reconciliation.

### 4.3 Loss responsibility

Implement:

- loss cause
- loss allocation policy
- policy resolver
- responsibility calculation
- recovery implications.

Important:

```text
Result
  ↓
Allocation
  ↓
Responsibility
```

must be independently traceable.

**Milestone:** the same original business facts always reproduce the same historical calculation when using the frozen policy snapshot.

---

# Phase 5 — Distribution & settlement

This is the part we just started.

Complete:

### 5.1 Distribution

- obligation lifecycle
- maker/checker
- posting
- idempotency
- distribution accounting.

### 5.2 Settlement

- immutable settlement records
- partial settlement
- full settlement
- over-settlement detection
- settlement reconciliation.

### 5.3 Correction

Implement:

```text
UNSETTLED
    → replacement

PARTIALLY_SETTLED
    → corrected outstanding/recovery

FULLY_SETTLED
    → additional/recovery/refund/offset

OVER_SETTLED
    → recovery/offset/manual review
```

### 5.4 Accounting bridge

Every correction must reconcile:

```text
Distribution
      =
Settlement
      =
Partner movement
      =
Accounting
```

**Milestone:** a correction remains financially correct even after money has already been paid.

---

# Phase 6 — Partner position & historical reporting

Build the authoritative close position from:

```text
Opening Snapshot
       +
POSTED Partner Movements
       =
Closing Position
```

Implement:

- opening snapshot
- closing snapshot
- capital position
- current account
- profit/loss position
- cash settlement separation
- correction rebuild
- snapshot integrity.

Then reporting becomes:

```text
OPEN PERIOD
    → live data

LOCKED PERIOD
    → frozen snapshot
```

Never:

```text
LOCKED PERIOD
    → current mutable PartnerAccount
```

**Milestone:** historical reports remain stable forever.

---

# Phase 7 — Contract lifecycle / partner transitions

Then implement the lifecycle boundaries from the Syirkah domain.

### Entry

```text
Existing Syirkah
    ↓
Close existing position
    ↓
Freeze
    ↓
New Syirkah
```

### Exit

```text
Existing Syirkah
    ↓
Determine position
    ↓
Settle exiting partner
    ↓
Successor/current Syirkah
```

### Capital change

- additional capital
- capital reduction
- resulting position.

### Partner replacement

- predecessor
- transition
- successor
- opening position.

### Contract renewal

- predecessor closing
- successor contract
- opening snapshot.

**Milestone:** no partner or contract transition can bypass financial boundary determination.

---

# Phase 8 — Full liquidation / termination

After ordinary lifecycle is stable:

```text
ACTIVE
 ↓
LIQUIDATION
 ↓
ASSET REALIZATION
 ↓
LIABILITY SETTLEMENT
 ↓
RESULT DETERMINATION
 ↓
DISTRIBUTION
 ↓
CAPITAL RETURN
 ↓
COMPLETED
```

Implement:

- liquidation aggregate
- asset realization
- liability settlement
- final result
- partner allocation
- capital return obligations
- accounting bridge
- reconciliation.

Then enforce:

```text
FULL_TERMINATION
        ↓
NO SUCCESSOR
```

**Milestone:** complete Syirkah dissolution has a complete financial trail.

---

# Phase 9 — Policy/versioning

This should become mandatory before production historical replay.

Implement:

```text
Tenant
  ↓
Ledger
  ↓
Contract
  ↓
Contract Version
```

policy precedence:

```text
CONTRACT_VERSION
       ↓
CONTRACT
       ↓
LEDGER
       ↓
TENANT
```

Freeze:

```text
SyirkahPolicySnapshot
```

at calculation/close time.

This is especially important for:

- profit sharing
- loss allocation
- result disposition
- distribution accounting
- settlement completion
- liquidation.

**Milestone:** changing today's policy cannot silently rewrite yesterday's result.

---

# Phase 10 — Audit, evidence and compliance

Implement the complete evidence chain:

```text
Contract
   ↓
Calculation
   ↓
Allocation
   ↓
Distribution
   ↓
Settlement
   ↓
Journal
   ↓
Movement
   ↓
Snapshot
   ↓
Correction
```

Add:

- audit evidence
- immutable lineage
- hash chain
- correction evidence
- close evidence
- snapshot evidence
- transition evidence
- liquidation evidence
- maker/checker evidence
- outbox events.

Also build reconstruction:

```text
correctionId
    → all affected artifacts
```

and:

```text
snapshotId
    → all source calculations/movements
```

**Milestone:** an auditor can reconstruct why a historical number exists.

---

# Phase 11 — Reporting & consolidation

Only after the underlying financial model is stable.

Implement:

### Partner statement

```text
Opening
+ Capital Contribution
+ Profit Allocation
- Loss Allocation
- Withdrawal
- Capital Return
+/- Corrections
= Closing
```

### Syirkah statement

- result
- allocation
- responsibility
- distribution
- settlement
- position.

### Locked-period provenance

Every historical report should expose:

```text
sourceType = LOCKED_SNAPSHOT
snapshotId
calculationId
policySnapshotId
policySnapshotHash
```

### Consolidation invariants

```text
Σ partner closing capital
    =
contract closing capital
```

and:

```text
Σ profit allocation
    =
total allocated profit
```

```text
Σ loss allocation
    =
total allocated loss
```

**Milestone:** reporting is a projection of an already-proven financial model.

---

# Phase 12 — Production hardening

This is where we stop thinking primarily in terms of classes and start thinking in terms of failure modes.

### Concurrency

Test:

```text
close vs correction
correction vs correction
distribution vs correction
settlement vs correction
transition vs close
liquidation vs correction
```

### Idempotency

Every financial operation should survive:

```text
request
timeout
retry
duplicate request
worker restart
```

without double posting.

### Database constraints

Use the database for critical invariants:

- unique source
- unique active snapshot
- unique active contract
- unique correction action
- unique settlement reference
- tenant/ledger isolation.

### Failure recovery

Every long-running correction action must be:

```text
PENDING
IN_PROGRESS
COMPLETED
FAILED
```

and resumable.

---

# Phase 13 — Test architecture

I would divide tests into five layers.

### A. Domain tests

Fast tests for:

- nisbah
- profit allocation
- loss allocation
- result
- correction delta
- settlement decisions
- lifecycle transitions.

### B. Repository tests

Verify:

- tenant isolation
- unique constraints
- `FOR UPDATE`
- source idempotency
- snapshot uniqueness.

### C. Transaction tests

Most important.

For example:

```text
account mutation succeeds
journal persistence fails
→ everything rolled back
```

```text
journal succeeds
movement persistence fails
→ everything rolled back
```

```text
correction action fails halfway
→ no false READY_TO_RECLOSE
```

### D. Concurrency tests

Run competing transactions and verify exactly one valid final state.

### E. End-to-end Syirkah scenarios

Eventually test complete stories.

---

# Phase 14 — Golden scenarios

I recommend building a small set of **golden financial scenarios** that become permanent regression tests.

### Scenario 1 — Normal profit

```text
Capital
 → profit
 → nisbah
 → allocation
 → distribution
 → settlement
 → close
```

### Scenario 2 — Loss

```text
Capital
 → loss
 → loss responsibility
 → allocation
 → close
```

### Scenario 3 — Locked-period correction

```text
Closed
 → correction
 → reversal
 → recalculation
 → corrected allocation
 → corrected distribution
 → corrected position
 → new snapshot
 → reclose
```

### Scenario 4 — Correction after partial payment

```text
100 obligation
60 paid
80 corrected
→ 20 outstanding
```

### Scenario 5 — Correction after full payment

```text
100 obligation
100 paid
80 corrected
→ 20 recovery/offset/refund
```

### Scenario 6 — Partner exit

```text
Existing Syirkah
 → close position
 → settlement
 → successor state
```

### Scenario 7 — Full termination

```text
Assets
 → realization
 → liabilities
 → result
 → allocation
 → capital return
 → terminated
```

These become the **Syirkah financial compatibility suite**.

---

# Recommended implementation order

If we're continuing coding incrementally, I recommend this exact order:

```text
NOW
 │
 ├─ 1. Distribution settlement correction
 │
 ├─ 2. Partner position correction/rebuild
 │
 ├─ 3. Complete correction reconciliation
 │
 ├─ 4. Atomic final reclose
 │
 ├─ 5. End-to-end locked-period correction test
 │
 ├─ 6. Contract/partner transition
 │
 ├─ 7. Full liquidation
 │
 ├─ 8. Policy/version snapshot
 │
 ├─ 9. Audit/evidence
 │
 ├─ 10. Reporting/provenance
 │
 └─ 11. Concurrency + failure hardening
```

### One important architectural rule

We should **not** jump into liquidation, reporting, or UI yet.

The next few implementation slices should finish this chain first:

```text
Result
  ↓
Allocation
  ↓
Distribution
  ↓
Settlement
  ↓
Partner Movement
  ↓
Accounting Journal
  ↓
Partner Position
  ↓
Close Snapshot
  ↓
Reclose
```

Once that chain is transactionally sound, the later features become considerably safer because they are consuming a stable Syirkah financial core rather than introducing more independent state.

**So the immediate next implementation slice is: _Partner Position Correction/Rebuild_**, followed by the final reconciliation and atomic reclose path.

////////////

## Next implementation slice: Partner Position Correction/Rebuild

The key rule is:

> **Never “fix” `PartnerAccount` directly after a correction. Rebuild the corrected closing position from the frozen opening position + immutable POSTED movements.**

That gives us deterministic replay and prevents historical corruption.

### 1. Add a correction-aware position calculator

```java
package tech.kayys.syirkah.accounting.domain.syirkah.position;

import tech.kayys.syirkah.accounting.domain.syirkah.movement.PartnerAccountMovement;

import java.math.BigDecimal;
import java.util.List;

public final class SyirkahPartnerPositionCalculator {

    public PartnerPosition calculate(
            PartnerPosition openingPosition,
            List<PartnerAccountMovement> movements
    ) {
        if (openingPosition == null) {
            throw new IllegalArgumentException("Opening position is required");
        }

        BigDecimal capital = openingPosition.closingCapital();
        BigDecimal currentAccount = openingPosition.closingCurrentAccount();

        for (PartnerAccountMovement movement : movements) {
            if (!movement.isPosted()) {
                continue;
            }

            switch (movement.getType()) {
                case CAPITAL_CONTRIBUTION ->
                        capital = capital.add(movement.getAmount());

                case CAPITAL_RETURN ->
                        capital = capital.subtract(movement.getAmount());

                case CAPITAL_IMPAIRMENT ->
                        capital = capital.subtract(movement.getAmount());

                case PROFIT_ALLOCATION ->
                        currentAccount = currentAccount.add(movement.getAmount());

                case LOSS_ALLOCATION ->
                        currentAccount = currentAccount.subtract(movement.getAmount());

                case PARTNER_WITHDRAWAL ->
                        currentAccount = currentAccount.subtract(movement.getAmount());

                case CORRECTION_REVERSAL,
                     CORRECTION_ADJUSTMENT ->
                        currentAccount = applyCorrectionMovement(
                                capital,
                                currentAccount,
                                movement
                        ).currentAccount();
            }
        }

        return new PartnerPosition(
                openingPosition.partnerId(),
                capital,
                currentAccount
        );
    }

    private PartnerPosition applyCorrectionMovement(
            BigDecimal capital,
            BigDecimal currentAccount,
            PartnerAccountMovement movement
    ) {
        /*
         * Correction movements must retain their semantic movement type
         * through the movement metadata. They must not be inferred merely
         * from the fact that they are corrections.
         */
        return switch (movement.getCorrectionMovementType()) {
            case CAPITAL_CONTRIBUTION ->
                    new PartnerPosition(
                            movement.getPartnerId(),
                            capital.add(movement.getAmount()),
                            currentAccount
                    );

            case CAPITAL_RETURN, CAPITAL_IMPAIRMENT ->
                    new PartnerPosition(
                            movement.getPartnerId(),
                            capital.subtract(movement.getAmount()),
                            currentAccount
                    );

            case PROFIT_ALLOCATION ->
                    new PartnerPosition(
                            movement.getPartnerId(),
                            capital,
                            currentAccount.add(movement.getAmount())
                    );

            case LOSS_ALLOCATION, PARTNER_WITHDRAWAL ->
                    new PartnerPosition(
                            movement.getPartnerId(),
                            capital,
                            currentAccount.subtract(movement.getAmount())
                    );
        };
    }
}
```

I would actually make one refinement before committing this exact implementation: **do not encode correction semantics into the generic movement type**. The cleaner model is to retain the original economic movement category explicitly.

So the movement should carry:

```java
private MovementType type;
private MovementType economicType;
private String correctionId;
private String supersedesMovementId;
```

Then a correction reversal can say:

```text
type           = CORRECTION_REVERSAL
economicType   = PROFIT_ALLOCATION
amount         = 1,000,000
```

and the corrected movement:

```text
type           = CORRECTION_ADJUSTMENT
economicType   = PROFIT_ALLOCATION
amount         = 850,000
```

This is much safer than making the calculator guess what a correction means.

---

# 2. Corrected movement model

Add:

```java
public enum PartnerMovementType {
    CAPITAL_CONTRIBUTION,
    PROFIT_ALLOCATION,
    LOSS_ALLOCATION,
    CAPITAL_RETURN,
    CAPITAL_IMPAIRMENT,
    PARTNER_WITHDRAWAL,
    CORRECTION_REVERSAL,
    CORRECTION_ADJUSTMENT
}
```

Then:

```java
public final class PartnerAccountMovement {

    private PartnerMovementType type;

    /**
     * Economic movement represented by this record.
     *
     * For ordinary movements this is equal to type.
     * For correction movements it identifies what is being corrected.
     */
    private PartnerMovementType economicType;

    private String correctionId;
    private String supersedesMovementId;

    public PartnerMovementType getEconomicType() {
        return economicType != null ? economicType : type;
    }

    public boolean isCorrection() {
        return type == PartnerMovementType.CORRECTION_REVERSAL
                || type == PartnerMovementType.CORRECTION_ADJUSTMENT;
    }
}
```

This gives us a clean audit chain:

```text
Original movement
       │
       ▼
CORRECTION_REVERSAL
       │
       ▼
CORRECTION_ADJUSTMENT
```

No mutation of the original movement.

---

# 3. Position result

Introduce a value object rather than returning mutable `PartnerAccount`.

```java
package tech.kayys.syirkah.accounting.domain.syirkah.position;

import java.math.BigDecimal;

public record PartnerPosition(
        String partnerId,
        BigDecimal closingCapital,
        BigDecimal closingCurrentAccount
) {

    public PartnerPosition {
        if (partnerId == null || partnerId.isBlank()) {
            throw new IllegalArgumentException("Partner ID is required");
        }

        if (closingCapital == null) {
            throw new IllegalArgumentException("Closing capital is required");
        }

        if (closingCurrentAccount == null) {
            throw new IllegalArgumentException(
                    "Closing current account is required"
            );
        }
    }

    public BigDecimal totalPosition() {
        return closingCapital.add(closingCurrentAccount);
    }
}
```

This makes the calculation completely side-effect free.

---

# 4. Rebuild service

Now the important application service:

```java
public interface SyirkahPartnerPositionRebuildService {

    Uni<PartnerPosition> rebuild(
            String tenantId,
            String ledgerId,
            String contractId,
            String partnerId,
            String snapshotId
    );

    Uni<PartnerPosition> rebuildForCorrection(
            String tenantId,
            String ledgerId,
            String contractId,
            String partnerId,
            String originalSnapshotId,
            String correctionId
    );
}
```

Implementation:

```java
@ApplicationScoped
public class DefaultSyirkahPartnerPositionRebuildService
        implements SyirkahPartnerPositionRebuildService {

    private final PartnerClosePositionSnapshotRepository snapshotRepository;
    private final PartnerAccountMovementRepository movementRepository;
    private final SyirkahPartnerPositionCalculator calculator;

    public DefaultSyirkahPartnerPositionRebuildService(
            PartnerClosePositionSnapshotRepository snapshotRepository,
            PartnerAccountMovementRepository movementRepository,
            SyirkahPartnerPositionCalculator calculator
    ) {
        this.snapshotRepository = snapshotRepository;
        this.movementRepository = movementRepository;
        this.calculator = calculator;
    }

    @Override
    public Uni<PartnerPosition> rebuild(
            String tenantId,
            String ledgerId,
            String contractId,
            String partnerId,
            String snapshotId
    ) {
        return snapshotRepository
                .findById(
                        tenantId,
                        ledgerId,
                        snapshotId
                )
                .onItem()
                .ifNull()
                .failWith(() ->
                        new IllegalStateException(
                                "Opening position snapshot was not found"
                        ))
                .flatMap(snapshot ->
                        movementRepository.findPostedMovements(
                                tenantId,
                                ledgerId,
                                contractId,
                                partnerId,
                                snapshot.getSnapshotDate()
                        )
                        .map(movements ->
                                calculator.calculate(
                                        snapshot.openingPosition(partnerId),
                                        movements
                                )
                        )
                );
    }

    @Override
    public Uni<PartnerPosition> rebuildForCorrection(
            String tenantId,
            String ledgerId,
            String contractId,
            String partnerId,
            String originalSnapshotId,
            String correctionId
    ) {
        return rebuild(
                tenantId,
                ledgerId,
                contractId,
                partnerId,
                originalSnapshotId
        );
    }
}
```

The important distinction is that the correction path **doesn't calculate from the old closing position**.

It calculates:

```text
original locked opening snapshot
        +
all POSTED movements applicable to correction
        =
corrected partner position
```

---

# 5. Repository contract

Extend the movement repository:

```java
public interface PartnerAccountMovementRepository {

    Uni<List<PartnerAccountMovement>> findPostedMovements(
            String tenantId,
            String ledgerId,
            String contractId,
            String partnerId,
            LocalDate effectiveFrom
    );

    Uni<List<PartnerAccountMovement>> findByCorrection(
            String tenantId,
            String ledgerId,
            String correctionId
    );

    Uni<Optional<PartnerAccountMovement>> findByIdForUpdate(
            String tenantId,
            String ledgerId,
            String movementId
    );

    Uni<Void> save(PartnerAccountMovement movement);
}
```

The SQL should explicitly restrict the calculation to accounting-posted movements:

```sql
SELECT *
FROM partner_account_movement
WHERE tenant_id = ?
  AND ledger_id = ?
  AND contract_id = ?
  AND partner_id = ?
  AND status = 'POSTED'
  AND effective_date >= ?
ORDER BY effective_date, id;
```

The ordering must be deterministic.

If two movements have the same effective date:

```text
effective_date
id
```

gives us a stable replay order.

---

# 6. Build the correction delta

We should not only produce the corrected position. We need the difference between the old and new positions.

```java
public record PartnerPositionDelta(
        String partnerId,
        BigDecimal capitalDelta,
        BigDecimal currentAccountDelta
) {

    public boolean isZero() {
        return capitalDelta.signum() == 0
                && currentAccountDelta.signum() == 0;
    }

    public BigDecimal totalDelta() {
        return capitalDelta.add(currentAccountDelta);
    }
}
```

Calculator:

```java
public final class PartnerPositionDeltaCalculator {

    public PartnerPositionDelta calculate(
            PartnerPosition original,
            PartnerPosition corrected
    ) {
        return new PartnerPositionDelta(
                corrected.partnerId(),
                corrected.closingCapital()
                        .subtract(original.closingCapital()),
                corrected.closingCurrentAccount()
                        .subtract(original.closingCurrentAccount())
        );
    }
}
```

Example:

```text
Original:
capital          10,000
current account   2,000

Corrected:
capital          10,000
current account   1,700

Delta:
capital               0
current account     -300
```

That `-300` becomes the basis for:

```text
partner movement correction
        +
accounting adjustment
        +
distribution/settlement impact
```

---

# 7. Correction artifact

Add a dedicated immutable artifact:

```java
public record SyirkahPartnerPositionCorrection(
        String correctionId,
        String tenantId,
        String ledgerId,
        String contractId,
        String partnerId,
        String originalSnapshotId,
        String correctedSnapshotId,
        PartnerPosition originalPosition,
        PartnerPosition correctedPosition,
        PartnerPositionDelta delta,
        Instant calculatedAt
) {}
```

This becomes important for audit and reconciliation.

---

# 8. Reconciliation rule

The correction engine should now require:

```text
corrected position
        =
original position
+
POSTED movement deltas
```

and separately:

```text
partner position delta
        =
partner movement delta
        =
accounting journal delta
```

So add:

```java
public record PartnerPositionCorrectionReconciliation(
        String correctionId,
        String partnerId,
        PartnerPositionDelta positionDelta,
        Money movementDelta,
        Money accountingDelta,
        List<String> errors
) {

    public boolean isBalanced() {
        return errors.isEmpty()
                && movementDelta.equals(accountingDelta)
                && movementDelta.amount()
                        .equals(positionDelta.totalDelta());
    }
}
```

For a production implementation I would make this **per currency and per economic bucket**, rather than comparing one aggregate amount.

For example:

```text
CAPITAL
CURRENT_ACCOUNT
```

rather than allowing:

```text
capital +100
current account -100
```

to incorrectly appear balanced.

---

# 9. Extend the correction impact graph

This completes an important missing edge:

```text
Financial Result
       ↓
Partner Allocation
       ↓
Partner Movement
       ↓
Partner Position
       ↓
Close Snapshot
```

Therefore add:

```java
public enum SyirkahImpactDependency {

    RESULT_TO_ALLOCATION,
    RESULT_TO_RESPONSIBILITY,
    ALLOCATION_TO_DISTRIBUTION,
    ALLOCATION_TO_PARTNER_MOVEMENT,
    PARTNER_MOVEMENT_TO_POSITION,
    DISTRIBUTION_TO_SETTLEMENT,
    SETTLEMENT_TO_PARTNER_POSITION,
    ACCOUNTING_TO_ARTIFACT,
    ARTIFACT_TO_SNAPSHOT,
    SNAPSHOT_TO_REPORTING
}
```

This is preferable to jumping directly:

```text
allocation → partner position
```

because the movement ledger is the historical accounting bridge.

---

# 10. Correction executor action

Add:

```java
public enum SyirkahCorrectionActionType {

    REVERSE_JOURNAL,
    RECALCULATE_RESULT,
    RECALCULATE_ALLOCATION,
    RECALCULATE_RESPONSIBILITY,

    REVERSE_PARTNER_MOVEMENT,
    CREATE_CORRECTED_PARTNER_MOVEMENT,

    CANCEL_DISTRIBUTION,
    CREATE_CORRECTED_DISTRIBUTION,

    RECALCULATE_SETTLEMENT,
    CORRECT_PARTNER_POSITION,

    SUPERSEDE_SNAPSHOT,
    CREATE_CORRECTED_SNAPSHOT,

    REBUILD_REPORTING
}
```

The position action should execute only after the movement corrections have successfully posted.

```text
REVERSE_PARTNER_MOVEMENT
          ↓
CREATE_CORRECTED_PARTNER_MOVEMENT
          ↓
CORRECT_PARTNER_POSITION
          ↓
CREATE_CORRECTED_SNAPSHOT
```

---

# 11. Do not update `PartnerAccount` during correction

This is especially important.

`PartnerAccount` should remain:

```text
Projection
```

not:

```text
Source of truth
```

After successful correction:

```text
POSTED movement history
        ↓
position rebuild
        ↓
corrected snapshot
        ↓
projection update
```

The projection can therefore be rebuilt at any time.

If the projection becomes corrupted:

```text
DROP projection
      ↓
REPLAY movements
      ↓
RECREATE projection
```

without losing financial history.

---

# 12. Final corrected snapshot

The corrected snapshot should contain provenance:

```java
public record PartnerClosePositionSnapshot(
        String snapshotId,
        String tenantId,
        String ledgerId,
        String contractId,
        String fiscalPeriodId,
        String partnerId,

        BigDecimal openingCapital,
        BigDecimal closingCapital,

        BigDecimal openingCurrentAccount,
        BigDecimal closingCurrentAccount,

        String calculationId,
        String policySnapshotId,

        String correctionId,
        String supersedesSnapshotId,

        SnapshotStatus status
) {}
```

For a correction:

```text
correctionId            = CORR-001
supersedesSnapshotId    = SNAP-001
snapshotId              = SNAP-001-CORR-001
```

This gives us explicit historical lineage.

---

## Resulting correction flow

We now have:

```text
                 LOCKED PERIOD
                      │
                      ▼
              Correction Request
                      │
                      ▼
              Impact Calculation
                      │
          ┌───────────┴───────────┐
          ▼                       ▼
   Original Result          Original Allocation
                                  │
                                  ▼
                         Original Movements
                                  │
                         reverse + compensate
                                  │
                                  ▼
                       Corrected Movements
                                  │
                                  ▼
                       Partner Position
                            Rebuild
                                  │
                                  ▼
                     Position Delta
                                  │
              ┌───────────────────┼──────────────────┐
              ▼                   ▼                  ▼
        Distribution        Accounting         Settlement
              │                   │                  │
              └───────────────────┼──────────────────┘
                                  ▼
                         Reconciliation
                                  │
                                  ▼
                    Corrected Close Snapshot
                                  │
                                  ▼
                       Atomic Final Reclose
                                  │
             ┌────────────────────┼──────────────────┐
             ▼                    ▼                  ▼
        Snapshot ACTIVE      Period LOCKED     Correction RECLOSED
```

### Next slice

With partner-position rebuilding in place, the next implementation is the **complete correction reconciliation coordinator**: it will compare **result → allocation → movement → position → distribution → settlement → journal**, produce one deterministic reconciliation report, and make that report the mandatory gate before `READY_TO_RECLOSE`.

///////////

## Correction reconciliation coordinator

The next piece should become the **single source of truth for deciding whether a correction is safe to re-close**.

We should not let individual gates independently decide that the correction is complete. They should contribute evidence to one reconciliation result.

### 1. Reconciliation result

```java id="48271"
package tech.kayys.syirkah.accounting.domain.syirkah.correction;

import java.util.List;

public record SyirkahCorrectionReconciliation(
        String correctionId,
        String tenantId,
        String ledgerId,
        String fiscalPeriodId,

        SyirkahReconciliationSection result,
        SyirkahReconciliationSection allocation,
        SyirkahReconciliationSection partnerMovement,
        SyirkahReconciliationSection partnerPosition,
        SyirkahReconciliationSection distribution,
        SyirkahReconciliationSection settlement,
        SyirkahReconciliationSection accounting,
        SyirkahReconciliationSection snapshot,

        List<String> errors
) {

    public boolean isBalanced() {
        return errors.isEmpty()
                && result.passed()
                && allocation.passed()
                && partnerMovement.passed()
                && partnerPosition.passed()
                && distribution.passed()
                && settlement.passed()
                && accounting.passed()
                && snapshot.passed();
    }

    public boolean readyToReclose() {
        return isBalanced();
    }
}
```

Supporting value:

```java id="73164"
public record SyirkahReconciliationSection(
        String section,
        boolean passed,
        List<String> errors
) {

    public static SyirkahReconciliationSection passed(String section) {
        return new SyirkahReconciliationSection(
                section,
                true,
                List.of()
        );
    }

    public static SyirkahReconciliationSection failed(
            String section,
            List<String> errors
    ) {
        return new SyirkahReconciliationSection(
                section,
                false,
                List.copyOf(errors)
        );
    }
}
```

---

# 2. Reconciliation service

```java id="91543"
public interface SyirkahCorrectionReconciliationService {

    Uni<SyirkahCorrectionReconciliation> reconcile(
            String tenantId,
            String ledgerId,
            String correctionId
    );
}
```

The implementation should orchestrate the existing specialized reconciliation services rather than duplicate their logic.

```java id="60418"
@ApplicationScoped
public class DefaultSyirkahCorrectionReconciliationService
        implements SyirkahCorrectionReconciliationService {

    private final SyirkahResultReconciliationService resultService;
    private final SyirkahAllocationReconciliationService allocationService;
    private final PartnerMovementAccountingReconciliationService movementService;
    private final SyirkahPartnerPositionReconciliationService positionService;
    private final SyirkahDistributionReconciliationService distributionService;
    private final SyirkahSettlementReconciliationService settlementService;
    private final SyirkahAccountingReconciliationService accountingService;
    private final SyirkahSnapshotIntegrityService snapshotService;

    public DefaultSyirkahCorrectionReconciliationService(
            SyirkahResultReconciliationService resultService,
            SyirkahAllocationReconciliationService allocationService,
            PartnerMovementAccountingReconciliationService movementService,
            SyirkahPartnerPositionReconciliationService positionService,
            SyirkahDistributionReconciliationService distributionService,
            SyirkahSettlementReconciliationService settlementService,
            SyirkahAccountingReconciliationService accountingService,
            SyirkahSnapshotIntegrityService snapshotService
    ) {
        this.resultService = resultService;
        this.allocationService = allocationService;
        this.movementService = movementService;
        this.positionService = positionService;
        this.distributionService = distributionService;
        this.settlementService = settlementService;
        this.accountingService = accountingService;
        this.snapshotService = snapshotService;
    }

    @Override
    public Uni<SyirkahCorrectionReconciliation> reconcile(
            String tenantId,
            String ledgerId,
            String correctionId
    ) {
        return Uni.combine().all().unis(
                resultService.reconcile(
                        tenantId, ledgerId, correctionId
                ),
                allocationService.reconcile(
                        tenantId, ledgerId, correctionId
                ),
                movementService.reconcile(
                        tenantId, ledgerId, correctionId
                ),
                positionService.reconcile(
                        tenantId, ledgerId, correctionId
                ),
                distributionService.reconcile(
                        tenantId, ledgerId, correctionId
                ),
                settlementService.reconcile(
                        tenantId, ledgerId, correctionId
                ),
                accountingService.reconcile(
                        tenantId, ledgerId, correctionId
                ),
                snapshotService.reconcile(
                        tenantId, ledgerId, correctionId
                )
        ).with(tuple -> {

            var errors = new java.util.ArrayList<String>();

            addErrors(errors, tuple.getItem1());
            addErrors(errors, tuple.getItem2());
            addErrors(errors, tuple.getItem3());
            addErrors(errors, tuple.getItem4());
            addErrors(errors, tuple.getItem5());
            addErrors(errors, tuple.getItem6());
            addErrors(errors, tuple.getItem7());
            addErrors(errors, tuple.getItem8());

            return new SyirkahCorrectionReconciliation(
                    correctionId,
                    tenantId,
                    ledgerId,
                    null,
                    tuple.getItem1(),
                    tuple.getItem2(),
                    tuple.getItem3(),
                    tuple.getItem4(),
                    tuple.getItem5(),
                    tuple.getItem6(),
                    tuple.getItem7(),
                    tuple.getItem8(),
                    List.copyOf(errors)
            );
        });
    }

    private void addErrors(
            List<String> target,
            SyirkahReconciliationSection section
    ) {
        if (!section.passed()) {
            target.addAll(section.errors());
        }
    }
}
```

One important implementation detail: if the existing project is on an older Mutiny API, use the equivalent `Uni.combine()` form already used by the accounting modules rather than introducing a new reactive pattern.

---

# 3. The reconciliation relationships

The individual services should validate these exact relationships.

### Result

```text
corrected financial result
=
recalculation(original business inputs, frozen policy snapshot)
```

Not:

```text
old result + arbitrary adjustment
```

### Allocation

```text
sum(partner allocations)
=
corrected financial result
```

subject to the applicable Syirkah result/allocation policy.

### Partner movements

```text
original movement
+
reversal movement
+
corrected movement
=
corrected economic movement
```

### Partner position

```text
opening position
+
POSTED movements
=
corrected closing position
```

### Distribution

```text
original distribution
+
reversal
+
corrected distribution
=
corrected distribution obligation
```

### Settlement

```text
settlement history
+
correction obligation
=
correct settlement state
```

### Accounting

```text
economic correction delta
=
journal correction delta
```

### Snapshot

```text
snapshot contents
=
reconciled corrected state
```

That is the core invariant chain.

---

# 4. Add cross-layer delta reconciliation

Checking each layer independently is not enough.

For example:

```text
Result delta       = +100
Allocation delta   = +100
Movement delta     = +100
Journal delta      = +90
```

Every individual subsystem could appear valid, but the overall correction is wrong.

Add:

```java id="15739"
public record SyirkahCorrectionDelta(
        String currency,

        java.math.BigDecimal resultDelta,
        java.math.BigDecimal allocationDelta,
        java.math.BigDecimal movementDelta,
        java.math.BigDecimal distributionDelta,
        java.math.BigDecimal settlementDelta,
        java.math.BigDecimal accountingDelta
) {

    public boolean isConsistent() {
        return resultDelta.compareTo(allocationDelta) == 0
                && allocationDelta.compareTo(movementDelta) == 0
                && movementDelta.compareTo(accountingDelta) == 0;
    }
}
```

Distribution and settlement deliberately remain separate because settlement timing can differ from distribution obligation.

---

# 5. Make the correction gate authoritative

The existing `CorrectionReconciliationGate` should now become very small:

```java id="82410"
@ApplicationScoped
public class CorrectionReconciliationGate
        implements CloseValidationGate {

    private final SyirkahCorrectionReconciliationService reconciliationService;

    public CorrectionReconciliationGate(
            SyirkahCorrectionReconciliationService reconciliationService
    ) {
        this.reconciliationService = reconciliationService;
    }

    @Override
    public Uni<CloseGateResult> validate(
            CloseValidationContext context
    ) {
        if (!context.isCorrection()) {
            return Uni.createFrom().item(
                    CloseGateResult.passed()
            );
        }

        return reconciliationService
                .reconcile(
                        context.tenantId(),
                        context.ledgerId(),
                        context.correctionId()
                )
                .map(reconciliation -> {
                    if (reconciliation.readyToReclose()) {
                        return CloseGateResult.passed();
                    }

                    return CloseGateResult.failed(
                            reconciliation.errors()
                    );
                });
    }
}
```

This makes the architecture much cleaner:

```text
specialized reconciliation
        ↓
central reconciliation
        ↓
CorrectionReconciliationGate
        ↓
READY_TO_RECLOSE
```

---

# 6. Prevent `READY_TO_RECLOSE` from being manually forced

The correction aggregate should not have:

```java
correction.markReadyToReclose();
```

available as an unconditional command.

Instead:

```java id="30695"
public void markReadyToReclose(
        SyirkahCorrectionReconciliation reconciliation
) {
    if (!reconciliation.readyToReclose()) {
        throw new IllegalStateException(
                "Correction is not reconciled and cannot be reclosed"
        );
    }

    if (status != SyirkahCorrectionStatus.CORRECTING) {
        throw new IllegalStateException(
                "Correction must be in CORRECTING state"
        );
    }

    status = SyirkahCorrectionStatus.READY_TO_RECLOSE;
}
```

That closes an important bypass.

---

# 7. Persist the reconciliation evidence

Do not calculate it only in memory.

Add:

```java id="94820"
public record SyirkahCorrectionReconciliationRecord(
        String id,
        String tenantId,
        String ledgerId,
        String correctionId,
        String result,
        String allocation,
        String partnerMovement,
        String partnerPosition,
        String distribution,
        String settlement,
        String accounting,
        String snapshot,
        boolean passed,
        String evidenceHash,
        java.time.Instant reconciledAt,
        String reconciledBy
) {}
```

The serialized evidence should be immutable.

This matters because after a correction is reclosed, someone must be able to answer:

> Why was this correction considered safe to reclose?

The answer should be persisted evidence, not a recalculation using today's code and today's policy.

---

# 8. Reconciliation evidence hash

Use the same audit/evidence approach already planned.

Canonical input:

```text id="51372"
correctionId
result delta
allocation delta
movement delta
position delta
distribution delta
settlement delta
accounting delta
snapshotId
policySnapshotId
```

Then:

```java id="69104"
public interface ReconciliationEvidenceHasher {

    String hash(
            SyirkahCorrectionReconciliation reconciliation
    );
}
```

SHA-256 is sufficient for tamper-evident evidence here.

The hash is **evidence integrity**, not authorization.

---

# 9. Final reclose flow

Now the entire correction lifecycle becomes:

```text id="72184"
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
    ├── recalculate result
    ├── recalculate allocation
    ├── correct responsibility
    ├── reverse movements
    ├── create corrected movements
    ├── correct distributions
    ├── correct settlements
    ├── rebuild partner positions
    └── create corrected snapshot
    │
    ▼
CENTRAL RECONCILIATION
    │
    ├── result ✓
    ├── allocation ✓
    ├── movements ✓
    ├── position ✓
    ├── distribution ✓
    ├── settlement ✓
    ├── accounting ✓
    └── snapshot ✓
    │
    ▼
READY_TO_RECLOSE
    │
    ▼
ATOMIC FINALIZATION
    │
    ├── original snapshot → SUPERSEDED
    ├── corrected snapshot → ACTIVE
    ├── fiscal period → LOCKED
    ├── correction → RECLOSED
    ├── reconciliation evidence persisted
    └── completion outbox
    │
    ▼
RECONCILED HISTORY
```

---

## 10. Most important integration rule

The finalization transaction must **re-run the reconciliation while holding the relevant locks**.

Do not do:

```text
reconcile
   ↓
wait
   ↓
finalize
```

because another worker could change something between those operations.

Instead:

```text
BEGIN TRANSACTION

lock correction
lock fiscal period
lock original snapshot
lock corrected snapshot
lock affected movements
lock affected obligations

FINAL RECONCILIATION

if failed:
    ROLLBACK

if passed:
    supersede original snapshot
    activate corrected snapshot
    lock fiscal period
    mark correction RECLOSED
    persist evidence
    write outbox

COMMIT
```

This is the real **reclose barrier**.

---

# 11. Concurrency test

Add this integration test as a mandatory scenario:

```java id="42157"
@Test
void onlyOneConcurrentRecloseCanWin() {
    // Arrange
    String correctionId = createApprovedCorrection();

    executeCorrection(correctionId);

    // Act
    Uni<Void> first = finalizeCorrection(correctionId, "worker-1");
    Uni<Void> second = finalizeCorrection(correctionId, "worker-2");

    // Assert
    Uni.combine().all().unis(first, second)
            .discardItems()
            .await()
            .indefinitely();

    assertEquals(
            1,
            snapshotRepository.countActiveSnapshots(
                    TENANT_ID,
                    LEDGER_ID,
                    FISCAL_PERIOD_ID
            )
    );

    assertEquals(
            SyirkahCorrectionStatus.RECLOSED,
            correctionRepository
                    .findById(CORRECTION_ID)
                    .await()
                    .indefinitely()
                    .status()
    );

    assertEquals(
            1,
            outboxRepository.countByEventType(
                    "CORRECTION_COMPLETION",
                    CORRECTION_ID
            )
    );
}
```

One worker succeeds; the other should receive an idempotent/already-finalized result or a concurrency conflict. It must **never create a second active snapshot or second correction completion event**.

---

## What this gives us now

The correction architecture has crossed an important boundary:

```text
Business correction
       ↓
Economic artifacts
       ↓
Accounting artifacts
       ↓
Historical position
       ↓
Settlement
       ↓
Snapshot
       ↓
Central reconciliation
       ↓
Atomic reclose
```

And, importantly for the Syirkah-specific extension, the existing accounting implementation remains intact. We are adding a **Syirkah correction and historical-integrity layer around it**, rather than replacing the existing accounting engine.

### Next implementation slice

The natural next step is **the end-to-end locked-period correction integration test suite**. That should exercise the complete path with a concrete scenario such as:

```text
Original net profit     100,000,000
Partner A allocation     65,000,000
Partner B allocation     35,000,000

Correction:
net profit                90,000,000
Partner A                 58,500,000
Partner B                 31,500,000

Then verify:
- original journals remain immutable
- reversal journals exist
- corrected journals exist
- movements reconcile
- partner positions reconcile
- distribution obligations reconcile
- settlement correction is handled
- original snapshot becomes SUPERSEDED
- corrected snapshot becomes ACTIVE
- period becomes LOCKED again
- correction becomes RECLOSED
- exactly one completion event exists
```

This test becomes the golden integration scenario before moving on to **Syirkah contract/partner transitions**.

////////////

Next is the **end-to-end locked-period correction integration test suite**. This is the point where we prove the correction architecture as one transactionally consistent workflow before moving into contract/partner transitions.

I’ll build the golden scenario around:

```text
Original result
→ allocation
→ partner movements
→ distribution
→ settlement
→ journal
→ locked snapshot
→ correction
→ reversal + corrected artifacts
→ reconciliation
→ corrected snapshot
→ period re-lock
```

The key acceptance criteria will be **immutability, double-entry consistency, partner-position consistency, settlement correctness, idempotency, and exactly one final active snapshot**.

/////////

## Golden integration test: locked-period correction

The next implementation should establish one canonical end-to-end scenario and then add failure/concurrency variants around it.

One important finding from the existing framework: the current `GeneralLedgerService` still mutates accounts before journal persistence, and the current reversal service does the same. syirkah-framework(1) syirkah-framework(1)

So the tests below should target the **new transactional posting/reversal boundary**, not the legacy services directly.

---

### 1. Golden scenario

Use a concrete Syirkah case:

```text
Tenant:       TENANT-001
Ledger:       LEDGER-001
Contract:     SYIRKAH-001
Period:       2026-09

Partner A:    65%
Partner B:    35%

Original result:
  Profit = 100,000,000

Original allocation:
  Partner A = 65,000,000
  Partner B = 35,000,000

Original distributions:
  Partner A = 65,000,000
  Partner B = 35,000,000

Correction:
  Profit = 90,000,000

Corrected allocation:
  Partner A = 58,500,000
  Partner B = 31,500,000
```

The total correction is:

```text
Original profit       100,000,000
Corrected profit       90,000,000
                         ---------
Delta                  -10,000,000
```

---

# 2. Golden test

```java id="63842"
@Test
void shouldCorrectLockedSyirkahPeriodEndToEnd() {

    // Arrange
    LockedSyirkahScenario scenario =
            lockedScenario()
                    .withOriginalProfit("100000000")
                    .withPartnerAllocation("PARTNER-A", "65000000")
                    .withPartnerAllocation("PARTNER-B", "35000000")
                    .closeAndLock();

    String originalSnapshotId =
            scenario.originalSnapshotId();

    String originalJournalId =
            scenario.originalJournalId();

    // Act
    String correctionId =
            scenario.requestCorrection()
                    .withCorrectedProfit("90000000")
                    .approve()
                    .reopen()
                    .execute();

    // Assert: original journal is immutable
    JournalEntry original =
            journalRepository
                    .findById(
                            TENANT_ID,
                            LEDGER_ID,
                            JournalEntryId.of(originalJournalId)
                    )
                    .await()
                    .indefinitely()
                    .orElseThrow();

    assertEquals(
            JournalStatus.POSTED,
            original.getStatus()
    );

    assertNull(original.getReversalOfEntryId());

    assertNotNull(original.getReversedByEntryId());

    // Assert: reversal exists
    JournalEntry reversal =
            journalRepository
                    .findById(
                            TENANT_ID,
                            LEDGER_ID,
                            original.getReversedByEntryId()
                    )
                    .await()
                    .indefinitely()
                    .orElseThrow();

    assertEquals(
            original.getId(),
            reversal.getReversalOfEntryId()
    );

    assertEquals(
            JournalStatus.POSTED,
            reversal.getStatus()
    );

    // Assert: corrected journal exists
    List<JournalEntry> correctedJournals =
            journalRepository
                    .findBySource(
                            TenantId.of(TENANT_ID),
                            LedgerId.of(LEDGER_ID),
                            "SYIRKAH_CORRECTION_ADJUSTMENT",
                            correctionId
                    )
                    .await()
                    .indefinitely();

    assertFalse(correctedJournals.isEmpty());

    // Assert: partner movements
    List<PartnerAccountMovement> movements =
            movementRepository
                    .findByCorrection(
                            TENANT_ID,
                            LEDGER_ID,
                            correctionId
                    )
                    .await()
                    .indefinitely();

    assertTrue(
            movements.stream()
                    .allMatch(PartnerAccountMovement::isPosted)
    );

    // Assert: partner positions
    PartnerPosition partnerA =
            positionRepository
                    .findCorrectedPosition(
                            TENANT_ID,
                            LEDGER_ID,
                            correctionId,
                            "PARTNER-A"
                    )
                    .await()
                    .indefinitely()
                    .orElseThrow();

    PartnerPosition partnerB =
            positionRepository
                    .findCorrectedPosition(
                            TENANT_ID,
                            LEDGER_ID,
                            correctionId,
                            "PARTNER-B"
                    )
                    .await()
                    .indefinitely()
                    .orElseThrow();

    assertEquals(
            new BigDecimal("58500000"),
            partnerA.closingCurrentAccount()
    );

    assertEquals(
            new BigDecimal("31500000"),
            partnerB.closingCurrentAccount()
    );

    // Assert: distribution
    SyirkahCorrectionReconciliation reconciliation =
            reconciliationService
                    .reconcile(
                            TENANT_ID,
                            LEDGER_ID,
                            correctionId
                    )
                    .await()
                    .indefinitely();

    assertTrue(reconciliation.readyToReclose());

    // Assert: snapshot lineage
    SyirkahCloseSnapshot originalSnapshot =
            snapshotRepository
                    .findById(
                            TENANT_ID,
                            LEDGER_ID,
                            originalSnapshotId
                    )
                    .await()
                    .indefinitely()
                    .orElseThrow();

    assertEquals(
            SnapshotStatus.SUPERSEDED,
            originalSnapshot.status()
    );

    assertNotNull(
            originalSnapshot.supersededBySnapshotId()
    );

    SyirkahCloseSnapshot correctedSnapshot =
            snapshotRepository
                    .findById(
                            TENANT_ID,
                            LEDGER_ID,
                            originalSnapshot.supersededBySnapshotId()
                    )
                    .await()
                    .indefinitely()
                    .orElseThrow();

    assertEquals(
            SnapshotStatus.ACTIVE,
            correctedSnapshot.status()
    );

    assertEquals(
            correctionId,
            correctedSnapshot.correctionId()
    );

    // Assert: fiscal period is locked again
    FiscalPeriod period =
            fiscalPeriodRepository
                    .findById(
                            TENANT_ID,
                            LEDGER_ID,
                            FISCAL_PERIOD_ID
                    )
                    .await()
                    .indefinitely()
                    .orElseThrow();

    assertEquals(
            FiscalPeriodStatus.LOCKED,
            period.status()
    );

    // Assert: correction lifecycle
    SyirkahPeriodCorrection correction =
            correctionRepository
                    .findById(
                            TENANT_ID,
                            LEDGER_ID,
                            correctionId
                    )
                    .await()
                    .indefinitely()
                    .orElseThrow();

    assertEquals(
            SyirkahCorrectionStatus.RECLOSED,
            correction.status()
    );
}
```

The exact repository method names should follow the actual adapters; the test is establishing the required behavior rather than forcing new APIs unnecessarily.

---

# 3. Stronger accounting invariant

The test should not stop at checking the expected partner numbers.

Add:

```java id="50217"
assertTrue(
        accountingBalanceVerifier
                .verify(
                        TENANT_ID,
                        LEDGER_ID,
                        correctionId
                )
                .await()
                .indefinitely()
);
```

The verifier should establish:

```text
Total debit
    =
Total credit
```

for:

```text
original
+
reversal
+
correction adjustment
```

and separately:

```text
economic correction
=
accounting correction
```

---

# 4. Original history must remain untouched

This deserves an explicit test:

```java id="74193"
@Test
void shouldNeverModifyOriginalCorrectionArtifacts() {

    CorrectionFixture fixture =
            createClosedCorrectionFixture();

    OriginalState before =
            fixture.captureOriginalState();

    fixture.executeCorrection();

    OriginalState after =
            fixture.captureOriginalState();

    assertEquals(
            before.journalHash(),
            after.journalHash()
    );

    assertEquals(
            before.movementHash(),
            after.movementHash()
    );

    assertEquals(
            before.distributionHash(),
            after.distributionHash()
    );

    assertEquals(
            before.settlementHash(),
            after.settlementHash()
    );
}
```

This enforces the central rule:

> Corrections create compensating history; they do not rewrite history.

---

# 5. Failed reconciliation must block reclose

```java id="91426"
@Test
void shouldBlockRecloseWhenAccountingDeltaDoesNotMatchBusinessDelta() {

    CorrectionFixture fixture =
            createClosedCorrectionFixture();

    String correctionId =
            fixture.requestCorrection()
                    .withCorrectedProfit("90000000")
                    .approve()
                    .reopen()
                    .executeWithoutFinalization();

    fixture.corruptAccountingAdjustment(
            correctionId,
            new BigDecimal("89999999")
    );

    SyirkahCorrectionReconciliation result =
            reconciliationService
                    .reconcile(
                            TENANT_ID,
                            LEDGER_ID,
                            correctionId
                    )
                    .await()
                    .indefinitely();

    assertFalse(result.readyToReclose());

    assertTrue(
            result.errors()
                    .stream()
                    .anyMatch(error ->
                            error.contains("accounting")
                    )
    );

    assertThrows(
            IllegalStateException.class,
            () -> finalizationService
                    .finalize(
                            closeContext(correctionId),
                            "operator"
                    )
                    .await()
                    .indefinitely()
    );
}
```

The period must remain:

```text
OPEN / REOPENED
```

and the correction:

```text
CORRECTING
```

not `RECLOSED`.

---

# 6. Idempotency test

Running the same correction twice must not duplicate economic effects.

```java id="28631"
@Test
void shouldBeIdempotentWhenCorrectionExecutionIsRetried() {

    CorrectionFixture fixture =
            createClosedCorrectionFixture();

    String correctionId =
            fixture.requestCorrection()
                    .withCorrectedProfit("90000000")
                    .approve()
                    .reopen()
                    .id();

    correctionExecutor.execute(
            correctionId,
            "worker-1"
    ).await().indefinitely();

    correctionExecutor.execute(
            correctionId,
            "worker-1"
    ).await().indefinitely();

    assertEquals(
            1,
            movementRepository
                    .countByCorrection(
                            TENANT_ID,
                            LEDGER_ID,
                            correctionId
                    )
                    .await()
                    .indefinitely()
    );

    assertEquals(
            1,
            correctionActionRepository
                    .countCompleted(
                            TENANT_ID,
                            LEDGER_ID,
                            correctionId,
                            SyirkahCorrectionActionType.RECALCULATE_RESULT
                    )
                    .await()
                    .indefinitely()
    );
}
```

The database unique key remains the final protection:

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

---

# 7. Concurrent finalization test

This is the most important concurrency case.

```java id="15386"
@Test
void shouldAllowOnlyOneConcurrentFinalization() {

    CorrectionFixture fixture =
            createFullyReconciledCorrection();

    String correctionId = fixture.correctionId();

    Uni<FinalizationResult> workerOne =
            finalizationService.finalize(
                    fixture.context(),
                    "worker-1"
            );

    Uni<FinalizationResult> workerTwo =
            finalizationService.finalize(
                    fixture.context(),
                    "worker-2"
            );

    List<FinalizationResult> results =
            Uni.combine()
                    .all()
                    .unis(workerOne, workerTwo)
                    .combinedWith(list -> list)
                    .await()
                    .indefinitely();

    assertEquals(2, results.size());

    assertEquals(
            1,
            snapshotRepository
                    .countActive(
                            TENANT_ID,
                            LEDGER_ID,
                            FISCAL_PERIOD_ID
                    )
                    .await()
                    .indefinitely()
    );

    assertEquals(
            1,
            outboxRepository
                    .countBySource(
                            "CORRECTION_COMPLETION",
                            correctionId
                    )
                    .await()
                    .indefinitely()
    );

    assertEquals(
            1,
            correctionRepository
                    .countReclosed(
                            TENANT_ID,
                            LEDGER_ID,
                            correctionId
                    )
                    .await()
                    .indefinitely()
    );
}
```

The second worker may return an idempotent success, an already-finalized result, or a concurrency conflict depending on the application contract.

What is **not** acceptable:

```text
2 active snapshots
2 completion events
2 corrected distributions
2 sets of correction journals
```

---

# 8. Settlement scenario

We also need one explicit scenario where settlement already occurred.

```text
Original obligation:       65,000,000
Already settled:           60,000,000
Corrected obligation:      58,500,000

Difference:                -6,500,000
```

The system must not change the original settlement record.

Expected outcome:

```text
Original obligation
        65,000,000
             │
             ├── settlement 60,000,000
             │
             ▼
Correction
        corrected obligation
             58,500,000
             │
             ▼
settlement correction
        1,500,000 excess
```

The exact disposition—recovery, offset, refund, or manual review—must come from the configured settlement policy. The accounting engine should not silently invent a Sharia/legal treatment.

Test:

```java id="67325"
@Test
void shouldPreserveSettlementHistoryWhenCorrectedObligationIsLower() {

    CorrectionFixture fixture =
            createFixtureWithSettlement(
                    new BigDecimal("65000000"),
                    new BigDecimal("60000000")
            );

    String correctionId =
            fixture.correctObligation(
                    new BigDecimal("58500000")
            );

    SyirkahSettlementCorrectionDecision decision =
            settlementCorrectionService
                    .analyze(
                            fixture.context(correctionId)
                    )
                    .await()
                    .indefinitely();

    assertEquals(
            SyirkahSettlementCorrectionState.RECOVERY_REQUIRED,
            decision.correctionState()
    );

    assertEquals(
            new BigDecimal("60000000"),
            decision.settledAmount()
    );

    assertEquals(
            new BigDecimal("-6500000"),
            decision.deltaAmount()
    );

    assertEquals(
            1,
            settlementRepository
                    .countOriginalSettlements(
                            fixture.obligationId()
                    )
                    .await()
                    .indefinitely()
    );
}
```

---

# 9. Golden invariant test

Finally, add one test that expresses the whole architecture in mathematical form:

```java id="80416"
@Test
void correctedSyirkahHistoryMustReconcileAcrossAllLayers() {

    CorrectionFixture fixture =
            createGoldenCorrectionFixture();

    SyirkahCorrectionReconciliation reconciliation =
            fixture.executeAndReconcile();

    assertTrue(reconciliation.result().passed());
    assertTrue(reconciliation.allocation().passed());
    assertTrue(reconciliation.partnerMovement().passed());
    assertTrue(reconciliation.partnerPosition().passed());
    assertTrue(reconciliation.distribution().passed());
    assertTrue(reconciliation.settlement().passed());
    assertTrue(reconciliation.accounting().passed());
    assertTrue(reconciliation.snapshot().passed());

    assertTrue(
            reconciliation.readyToReclose()
    );
}
```

This becomes the **Syirkah correction golden test**.

---

## Test matrix

We should end this phase with at least these cases:

| Scenario | Expected |
|---|---|
| Correct unlocked data | Normal processing |
| Correct locked period | Reopen → correct → reclose |
| Result decreases | Compensating allocation |
| Result increases | Additional allocation |
| Distribution unsettled | Corrected obligation |
| Distribution partially settled | Additional/recovery handling |
| Distribution fully settled | Recovery/offset/manual policy |
| Original journal | Never mutated |
| Original movement | Never mutated |
| Retry correction | Idempotent |
| Retry finalization | Idempotent |
| Concurrent finalization | One active snapshot |
| Accounting mismatch | Cannot reclose |
| Position mismatch | Cannot reclose |
| Snapshot mismatch | Cannot reclose |
| Missing reconciliation evidence | Cannot reclose |
| Transaction failure | Entire finalization rolls back |

### Phase completion criterion

We can consider this phase complete only when this invariant is enforced:

```text
                 corrected result
                       │
                       ▼
                  allocation
                       │
                       ▼
                partner movement
                       │
                       ▼
                 partner position
                       │
                       ▼
                  distribution
                       │
                       ▼
                   settlement
                       │
                       ▼
                 accounting
                       │
                       ▼
             corrected snapshot
                       │
                       ▼
              reconciliation = PASS
                       │
                       ▼
                 period LOCKED
                       │
                       ▼
               correction RECLOSED
```

At that point, the next roadmap phase is **Syirkah contract and partner transitions**: partner entry, exit, capital change, replacement, contract renewal, and full termination—using the same close/snapshot boundary rather than allowing a new partner or successor contract to appear inside an unclosed predecessor position.

///////////

We continue with the **contract/partner transition boundary**. The key rule is:

> A partner/capital change must not mutate an already-active Syirkah position. The predecessor must first reach a frozen financial position; the successor then opens from that frozen position.

### 1. Transition aggregate

```java
package tech.kayys.syirkah.syirkah.domain.transition;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class SyirkahTransition {

    private final String id;
    private final String tenantId;
    private final String ledgerId;
    private final String previousContractId;
    private final String successorContractId;
    private final SyirkahTransitionType type;
    private final SyirkahTransitionReason reason;
    private final LocalDate effectiveDate;

    private SyirkahTransitionStatus status;

    private String frozenSnapshotId;
    private String openingSnapshotId;

    private Instant completedAt;
    private String completedBy;

    private SyirkahTransition(
            String id,
            String tenantId,
            String ledgerId,
            String previousContractId,
            String successorContractId,
            SyirkahTransitionType type,
            SyirkahTransitionReason reason,
            LocalDate effectiveDate
    ) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.ledgerId = Objects.requireNonNull(ledgerId);
        this.previousContractId = Objects.requireNonNull(previousContractId);
        this.successorContractId = successorContractId;
        this.type = Objects.requireNonNull(type);
        this.reason = Objects.requireNonNull(reason);
        this.effectiveDate = Objects.requireNonNull(effectiveDate);
        this.status = SyirkahTransitionStatus.DRAFT;
    }

    public static SyirkahTransition create(
            String id,
            String tenantId,
            String ledgerId,
            String previousContractId,
            String successorContractId,
            SyirkahTransitionType type,
            SyirkahTransitionReason reason,
            LocalDate effectiveDate
    ) {
        if (type == SyirkahTransitionType.FULL_TERMINATION
                && successorContractId != null) {
            throw new IllegalArgumentException(
                    "Full termination cannot have a successor contract"
            );
        }

        if (type != SyirkahTransitionType.FULL_TERMINATION
                && successorContractId == null) {
            throw new IllegalArgumentException(
                    "A non-terminal transition requires a successor contract"
            );
        }

        return new SyirkahTransition(
                id,
                tenantId,
                ledgerId,
                previousContractId,
                successorContractId,
                type,
                reason,
                effectiveDate
        );
    }

    public void requireClosing() {
        requireStatus(SyirkahTransitionStatus.DRAFT);
        status = SyirkahTransitionStatus.CLOSING_REQUIRED;
    }

    public void freezeSnapshot(String snapshotId) {
        requireStatus(SyirkahTransitionStatus.CLOSING_REQUIRED);

        this.frozenSnapshotId = Objects.requireNonNull(snapshotId);
        status = SyirkahTransitionStatus.SNAPSHOT_FROZEN;
    }

    public void requireSettlement() {
        requireStatus(SyirkahTransitionStatus.SNAPSHOT_FROZEN);
        status = SyirkahTransitionStatus.SETTLEMENT_REQUIRED;
    }

    public void markSuccessorPending() {
        if (type == SyirkahTransitionType.FULL_TERMINATION) {
            throw new IllegalStateException(
                    "Full termination has no successor"
            );
        }

        if (status != SyirkahTransitionStatus.SNAPSHOT_FROZEN
                && status != SyirkahTransitionStatus.SETTLEMENT_REQUIRED) {
            throw new IllegalStateException(
                    "Successor can only become pending after predecessor snapshot is frozen"
            );
        }

        status = SyirkahTransitionStatus.SUCCESSOR_PENDING;
    }

    public void attachOpeningSnapshot(String snapshotId) {
        if (type == SyirkahTransitionType.FULL_TERMINATION) {
            throw new IllegalStateException(
                    "Full termination cannot have an opening snapshot"
            );
        }

        if (status != SyirkahTransitionStatus.SUCCESSOR_PENDING) {
            throw new IllegalStateException(
                    "Successor must be pending before opening snapshot is attached"
            );
        }

        this.openingSnapshotId = Objects.requireNonNull(snapshotId);
    }

    public void complete(String operator) {
        Objects.requireNonNull(operator);

        if (type == SyirkahTransitionType.FULL_TERMINATION) {
            if (frozenSnapshotId == null) {
                throw new IllegalStateException(
                        "Termination requires a frozen predecessor snapshot"
                );
            }
        } else {
            if (openingSnapshotId == null) {
                throw new IllegalStateException(
                        "Successor requires an opening snapshot"
                );
            }
        }

        if (status != SyirkahTransitionStatus.SUCCESSOR_PENDING
                && status != SyirkahTransitionStatus.SETTLEMENT_REQUIRED
                && status != SyirkahTransitionStatus.SNAPSHOT_FROZEN) {
            throw new IllegalStateException(
                    "Transition cannot be completed from " + status
            );
        }

        status = SyirkahTransitionStatus.COMPLETED;
        completedAt = Instant.now();
        completedBy = operator;
    }

    public void cancel() {
        if (status == SyirkahTransitionStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Completed transitions cannot be cancelled"
            );
        }

        status = SyirkahTransitionStatus.CANCELLED;
    }

    private void requireStatus(SyirkahTransitionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Expected " + expected + " but was " + status
            );
        }
    }

    public String id() {
        return id;
    }

    public String tenantId() {
        return tenantId;
    }

    public String ledgerId() {
        return ledgerId;
    }

    public String previousContractId() {
        return previousContractId;
    }

    public String successorContractId() {
        return successorContractId;
    }

    public SyirkahTransitionType type() {
        return type;
    }

    public SyirkahTransitionReason reason() {
        return reason;
    }

    public LocalDate effectiveDate() {
        return effectiveDate;
    }

    public SyirkahTransitionStatus status() {
        return status;
    }

    public String frozenSnapshotId() {
        return frozenSnapshotId;
    }

    public String openingSnapshotId() {
        return openingSnapshotId;
    }
}
```

Enums:

```java
package tech.kayys.syirkah.syirkah.domain.transition;

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

```java
package tech.kayys.syirkah.syirkah.domain.transition;

public enum SyirkahTransitionReason {
    NEW_PARTNER,
    PARTNER_WITHDRAWAL,
    CAPITAL_CHANGE,
    PARTNER_REPLACEMENT,
    CONTRACT_EXPIRY,
    MUTUAL_DISSOLUTION,
    CONTRACT_BREACH
}
```

```java
package tech.kayys.syirkah.syirkah.domain.transition;

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

### 2. Successor contract activation

The successor must **not** become active merely because it exists.

```java
public enum SyirkahContractActivationStatus {
    DRAFT,
    PENDING_PREDECESSOR,
    READY,
    ACTIVE,
    SUSPENDED,
    TERMINATED,
    CANCELLED
}
```

Add to `SyirkahContract`:

```java
public void markPendingPredecessor() {
    if (activationStatus != SyirkahContractActivationStatus.DRAFT) {
        throw new IllegalStateException(
                "Only DRAFT contracts can become pending"
        );
    }

    activationStatus =
            SyirkahContractActivationStatus.PENDING_PREDECESSOR;
}

public void markReady() {
    if (activationStatus
            != SyirkahContractActivationStatus.PENDING_PREDECESSOR) {
        throw new IllegalStateException(
                "Contract must wait for predecessor transition"
        );
    }

    activationStatus = SyirkahContractActivationStatus.READY;
}

public void activate() {
    if (activationStatus != SyirkahContractActivationStatus.READY) {
        throw new IllegalStateException(
                "Only READY contracts can become ACTIVE"
        );
    }

    activationStatus = SyirkahContractActivationStatus.ACTIVE;
}

public void terminate() {
    if (activationStatus == SyirkahContractActivationStatus.TERMINATED) {
        return;
    }

    if (activationStatus != SyirkahContractActivationStatus.ACTIVE
            && activationStatus != SyirkahContractActivationStatus.READY) {
        throw new IllegalStateException(
                "Contract cannot be terminated from "
                        + activationStatus
        );
    }

    activationStatus =
            SyirkahContractActivationStatus.TERMINATED;
}
```

This gives us the important invariant:

```text
DRAFT
   ↓
PENDING_PREDECESSOR
   ↓
READY
   ↓
ACTIVE
```

Never:

```text
DRAFT → ACTIVE
```

for a successor created by a transition.

### 3. Transition command

```java
package tech.kayys.syirkah.syirkah.application.transition;

public record ExecuteSyirkahTransitionCommand(
        String tenantId,
        String ledgerId,
        String transitionId,
        String operator
) {
}
```

### 4. Transition repository

```java
package tech.kayys.syirkah.syirkah.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.syirkah.domain.transition.SyirkahTransition;

import java.util.Optional;

public interface SyirkahTransitionRepository {

    Uni<Optional<SyirkahTransition>> findByIdForUpdate(
            String tenantId,
            String ledgerId,
            String transitionId
    );

    Uni<Void> save(SyirkahTransition transition);
}
```

Contracts need the same locking boundary:

```java
public interface SyirkahContractRepository {

    Uni<Optional<SyirkahContract>> findByIdForUpdate(
            String tenantId,
            String ledgerId,
            String contractId
    );

    Uni<Void> save(SyirkahContract contract);
}
```

### 5. Opening position

The successor's opening position must come from the predecessor's **frozen snapshot**, not from current mutable partner balances.

```java
public record NextContractOpeningPosition(
        String successorContractId,
        String predecessorSnapshotId,
        String currency,
        java.math.BigDecimal openingCapital,
        java.math.BigDecimal openingCurrentAccount
) {
}
```

And the application boundary:

```java
public interface SyirkahOpeningPositionService {

    Uni<String> createOpeningSnapshot(
            String tenantId,
            String ledgerId,
            String transitionId,
            String predecessorSnapshotId,
            String successorContractId,
            String operator
    );
}
```

The important direction is:

```text
LOCKED predecessor snapshot
        │
        ├── partner A closing position
        ├── partner B closing position
        └── contract closing position
                │
                ▼
        successor opening snapshot
```

Not:

```text
current PartnerAccount → successor
```

because `PartnerAccount` is only a materialized projection.

### 6. Executable transition transaction

Introduce a transaction boundary specifically for lifecycle changes:

```java
public interface SyirkahTransitionTransaction {

    Uni<Void> execute(
            ExecuteSyirkahTransitionCommand command
    );
}
```

Implementation flow:

```java
public final class DefaultSyirkahTransitionTransaction
        implements SyirkahTransitionTransaction {

    private final AccountingTransactionManager transactionManager;
    private final SyirkahTransitionRepository transitions;
    private final SyirkahContractRepository contracts;
    private final SyirkahOpeningPositionService openingPositions;
    private final SyirkahTransitionPreconditionService preconditions;
    private final SyirkahTransitionAccountingService accounting;
    private final SyirkahTransitionAuditService audit;

    @Override
    public Uni<Void> execute(
            ExecuteSyirkahTransitionCommand command
    ) {
        return transactionManager.inTransaction(() ->
                transitions.findByIdForUpdate(
                        command.tenantId(),
                        command.ledgerId(),
                        command.transitionId()
                ).onItem().ifNull().failWith(
                        () -> new IllegalStateException(
                                "Transition not found"
                        )
                ).onItem().transformToUni(maybe -> {

                    SyirkahTransition transition = maybe.orElseThrow();

                    return contracts.findByIdForUpdate(
                            command.tenantId(),
                            command.ledgerId(),
                            transition.previousContractId()
                    ).onItem().ifNull().failWith(
                            () -> new IllegalStateException(
                                    "Predecessor contract not found"
                            )
                    ).onItem().transformToUni(predecessor ->
                            executeLocked(
                                    command,
                                    transition,
                                    predecessor.orElseThrow()
                            )
                    );
                })
        );
    }

    private Uni<Void> executeLocked(
            ExecuteSyirkahTransitionCommand command,
            SyirkahTransition transition,
            SyirkahContract predecessor
    ) {
        return preconditions.verifyPredecessorCanTransition(
                command.tenantId(),
                command.ledgerId(),
                predecessor.id(),
                transition.effectiveDate()
        ).onItem().transformToUni(snapshotId -> {

            transition.requireClosing();
            transition.freezeSnapshot(snapshotId);

            if (transition.type()
                    == SyirkahTransitionType.FULL_TERMINATION) {

                return accounting.applyTermination(
                        command.tenantId(),
                        command.ledgerId(),
                        transition,
                        snapshotId,
                        command.operator()
                ).onItem().transformToUni(v -> {
                    predecessor.terminate();
                    return contracts.save(predecessor);
                }).invoke(v ->
                        transition.complete(command.operator())
                ).chain(() -> transitions.save(transition));

            }

            transition.markSuccessorPending();

            return contracts.findByIdForUpdate(
                    command.tenantId(),
                    command.ledgerId(),
                    transition.successorContractId()
            ).onItem().ifNull().failWith(
                    () -> new IllegalStateException(
                            "Successor contract not found"
                    )
            ).onItem().transformToUni(successorMaybe -> {

                SyirkahContract successor =
                        successorMaybe.orElseThrow();

                return openingPositions.createOpeningSnapshot(
                        command.tenantId(),
                        command.ledgerId(),
                        transition.id(),
                        snapshotId,
                        successor.id(),
                        command.operator()
                ).onItem().transformToUni(openingSnapshotId -> {

                    transition.attachOpeningSnapshot(
                            openingSnapshotId
                    );

                    successor.markReady();
                    predecessor.terminate();

                    return accounting.applyTransition(
                            command.tenantId(),
                            command.ledgerId(),
                            transition,
                            snapshotId,
                            openingSnapshotId,
                            command.operator()
                    ).chain(() -> contracts.save(predecessor))
                     .chain(() -> contracts.save(successor))
                     .invoke(() ->
                             successor.activate()
                     )
                     .chain(() ->
                             contracts.save(successor)
                     )
                     .invoke(() ->
                             transition.complete(
                                     command.operator()
                             )
                     )
                     .chain(() ->
                             transitions.save(transition)
                     );
                });
            });
        }).chain(() ->
                audit.recordCompleted(
                        command.tenantId(),
                        command.ledgerId(),
                        transition,
                        command.operator()
                )
        );
    }
}
```

The exact persistence implementation should ensure that **all of this remains one database transaction**.

### 7. The precondition is the critical piece

```java
public interface SyirkahTransitionPreconditionService {

    Uni<String> verifyPredecessorCanTransition(
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate effectiveDate
    );
}
```

It must verify:

```text
predecessor exists
        ↓
all relevant periods closed
        ↓
Syirkah result calculated
        ↓
profit/loss allocation completed
        ↓
partner movements POSTED
        ↓
distribution obligations reconciled
        ↓
settlements reconciled
        ↓
partner positions rebuilt
        ↓
close snapshot exists
        ↓
snapshot is ACTIVE/LOCKED historical boundary
        ↓
return snapshotId
```

If any of these fail, **no transition occurs**.

### 8. Database invariants

For the contract lifecycle:

```sql
CREATE UNIQUE INDEX ux_syirkah_active_contract
ON syirkah_contract (
    tenant_id,
    ledger_id
)
WHERE activation_status = 'ACTIVE';
```

For transitions:

```sql
CREATE UNIQUE INDEX ux_syirkah_pending_transition
ON syirkah_transition (
    tenant_id,
    ledger_id,
    previous_contract_id
)
WHERE status NOT IN ('COMPLETED', 'CANCELLED');
```

And successor linkage should be explicit:

```sql
ALTER TABLE syirkah_contract
    ADD COLUMN predecessor_contract_id VARCHAR(100);

ALTER TABLE syirkah_contract
    ADD COLUMN activation_status VARCHAR(40) NOT NULL
        DEFAULT 'DRAFT';
```

Add an index:

```sql
CREATE INDEX ix_syirkah_contract_predecessor
ON syirkah_contract (
    tenant_id,
    ledger_id,
    predecessor_contract_id
);
```

### 9. Transition accounting

The accounting bridge should **not invent new accounting mechanics**. It should reuse the existing journal posting transaction:

```java
public interface SyirkahTransitionAccountingService {

    Uni<Void> applyTransition(
            String tenantId,
            String ledgerId,
            SyirkahTransition transition,
            String predecessorSnapshotId,
            String openingSnapshotId,
            String operator
    );

    Uni<Void> applyTermination(
            String tenantId,
            String ledgerId,
            SyirkahTransition transition,
            String predecessorSnapshotId,
            String operator
    );
}
```

Stable source types:

```java
public final class SyirkahTransitionSourceTypes {

    public static final String PARTNER_ENTRY =
            "SYIRKAH_PARTNER_ENTRY";

    public static final String PARTNER_EXIT =
            "SYIRKAH_PARTNER_EXIT";

    public static final String CAPITAL_ADDITION =
            "SYIRKAH_CAPITAL_ADDITION";

    public static final String CAPITAL_REDUCTION =
            "SYIRKAH_CAPITAL_REDUCTION";

    public static final String PARTNER_REPLACEMENT =
            "SYIRKAH_PARTNER_REPLACEMENT";

    public static final String CONTRACT_RENEWAL =
            "SYIRKAH_CONTRACT_RENEWAL";

    public static final String FULL_TERMINATION =
            "SYIRKAH_FULL_TERMINATION";

    private SyirkahTransitionSourceTypes() {
    }
}
```

The source ID should always contain the transition identity:

```text
SYIRKAH_PARTNER_ENTRY / TRANSITION-001
SYIRKAH_CAPITAL_ADDITION / TRANSITION-002
...
```

That gives us idempotency through the existing journal source uniqueness.

### 10. Most important lifecycle result

The resulting state becomes:

```text
                 ACTIVE CONTRACT A
                        │
                        │ transition requested
                        ▼
                CLOSE A / SNAPSHOT
                        │
                        ▼
              FROZEN POSITION A
                        │
              ┌─────────┴─────────┐
              │                   │
        successor exists      termination
              │                   │
              ▼                   ▼
     OPENING SNAPSHOT B       LIQUIDATION
              │                   │
              ▼                   ▼
       ACTIVATE B             FINAL RESULT
              │                   │
              ▼                   ▼
         COMPLETE             COMPLETE
```

This also cleanly handles:

- **partner entry** → predecessor closes, successor contains the new partner;
- **partner exit** → predecessor closes, successor excludes the exiting partner and opens from the frozen result;
- **capital addition/reduction** → new contract boundary rather than retroactively changing the closed position;
- **partner replacement** → predecessor closes, successor represents the replacement;
- **contract renewal** → successor contract starts from the frozen predecessor position;
- **full termination** → no successor; proceed through the dedicated liquidation lifecycle.

The next implementation slice should now be the **transition-specific partner/capital delta model and opening snapshot construction**, followed by the **full-termination execution branch**.

////////////

The next slice is the **opening-position engine**. This is where the transition becomes economically explicit instead of letting each transition handler invent its own calculations.

## 1. Transition instruction

Keep the transition itself focused on lifecycle; put economic changes in an immutable instruction.

```java id="48217"
package tech.kayys.syirkah.syirkah.domain.transition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record SyirkahTransitionInstruction(
        List<PartnerTransitionInstruction> partnerChanges,
        BigDecimal totalCapitalChange
) {

    public SyirkahTransitionInstruction {
        partnerChanges = List.copyOf(
                Objects.requireNonNull(partnerChanges)
        );

        Objects.requireNonNull(totalCapitalChange);

        if (totalCapitalChange.signum() < 0) {
            throw new IllegalArgumentException(
                    "Total capital change cannot be negative"
            );
        }
    }

    public static SyirkahTransitionInstruction empty() {
        return new SyirkahTransitionInstruction(
                List.of(),
                BigDecimal.ZERO
        );
    }
}
```

Partner-level instruction:

```java id="72084"
package tech.kayys.syirkah.syirkah.domain.transition;

import java.math.BigDecimal;
import java.util.Objects;

public record PartnerTransitionInstruction(
        String partyId,
        PartnerTransitionAction action,
        BigDecimal capitalChange,
        String replacementPartyId
) {

    public PartnerTransitionInstruction {
        Objects.requireNonNull(partyId);
        Objects.requireNonNull(action);
        Objects.requireNonNull(capitalChange);

        if (capitalChange.signum() < 0) {
            throw new IllegalArgumentException(
                    "Capital change cannot be negative"
            );
        }

        if (action == PartnerTransitionAction.REPLACE
                && (replacementPartyId == null
                || replacementPartyId.isBlank())) {
            throw new IllegalArgumentException(
                    "Replacement party is required for REPLACE"
            );
        }
    }

    public static PartnerTransitionInstruction entry(
            String partyId,
            BigDecimal capital
    ) {
        return new PartnerTransitionInstruction(
                partyId,
                PartnerTransitionAction.ENTRY,
                capital,
                null
        );
    }

    public static PartnerTransitionInstruction exit(
            String partyId,
            BigDecimal capital
    ) {
        return new PartnerTransitionInstruction(
                partyId,
                PartnerTransitionAction.EXIT,
                capital,
                null
        );
    }

    public static PartnerTransitionInstruction capitalAddition(
            String partyId,
            BigDecimal capital
    ) {
        return new PartnerTransitionInstruction(
                partyId,
                PartnerTransitionAction.CAPITAL_ADDITION,
                capital,
                null
        );
    }

    public static PartnerTransitionInstruction capitalReduction(
            String partyId,
            BigDecimal capital
    ) {
        return new PartnerTransitionInstruction(
                partyId,
                PartnerTransitionAction.CAPITAL_REDUCTION,
                capital,
                null
        );
    }

    public static PartnerTransitionInstruction replace(
            String exitingPartyId,
            String replacementPartyId,
            BigDecimal transferredCapital
    ) {
        return new PartnerTransitionInstruction(
                exitingPartyId,
                PartnerTransitionAction.REPLACE,
                transferredCapital,
                replacementPartyId
        );
    }
}
```

```java id="7c37e1"
package tech.kayys.syirkah.syirkah.domain.transition;

public enum PartnerTransitionAction {
    ENTRY,
    EXIT,
    CAPITAL_ADDITION,
    CAPITAL_REDUCTION,
    REPLACE
}
```

---

# 2. Opening position must be derived, not copied

The predecessor snapshot is the immutable boundary.

```java id="k4d2e1"
package tech.kayys.syirkah.syirkah.domain.transition;

import java.math.BigDecimal;
import java.util.List;

public record PartnerOpeningPosition(
        String partyId,
        BigDecimal openingCapital,
        BigDecimal openingCurrentAccount,
        boolean active
) {
}
```

Then:

```java id="g7f2q4"
package tech.kayys.syirkah.syirkah.domain.transition;

import java.math.BigDecimal;
import java.util.List;

public record SyirkahOpeningPosition(
        String successorContractId,
        String predecessorSnapshotId,
        String currency,
        BigDecimal openingCapital,
        BigDecimal openingCurrentAccount,
        List<PartnerOpeningPosition> partners
) {

    public SyirkahOpeningPosition {
        partners = List.copyOf(partners);
    }
}
```

---

# 3. Calculator

The calculator consumes only:

1. frozen predecessor position;
2. transition instruction;
3. successor contract identity.

It does **not** read `PartnerAccount`.

```java id="r7f0x2"
package tech.kayys.syirkah.syirkah.domain.transition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class SyirkahOpeningPositionCalculator {

    public SyirkahOpeningPosition calculate(
            String successorContractId,
            String predecessorSnapshotId,
            String currency,
            Map<String, PartnerOpeningPosition> predecessorPositions,
            SyirkahTransitionInstruction instruction
    ) {
        Objects.requireNonNull(successorContractId);
        Objects.requireNonNull(predecessorSnapshotId);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(predecessorPositions);
        Objects.requireNonNull(instruction);

        Map<String, MutablePosition> result =
                new HashMap<>();

        predecessorPositions.forEach((partyId, position) ->
                result.put(
                        partyId,
                        new MutablePosition(
                                position.openingCapital(),
                                position.openingCurrentAccount(),
                                position.active()
                        )
                )
        );

        for (PartnerTransitionInstruction change
                : instruction.partnerChanges()) {

            applyChange(result, change);
        }

        BigDecimal totalCapital = result.values()
                .stream()
                .filter(MutablePosition::active)
                .map(MutablePosition::capital)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCurrentAccount = result.values()
                .stream()
                .filter(MutablePosition::active)
                .map(MutablePosition::currentAccount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SyirkahOpeningPosition(
                successorContractId,
                predecessorSnapshotId,
                currency,
                totalCapital,
                totalCurrentAccount,
                result.entrySet()
                        .stream()
                        .filter(entry -> entry.getValue().active())
                        .map(entry -> new PartnerOpeningPosition(
                                entry.getKey(),
                                entry.getValue().capital(),
                                entry.getValue().currentAccount(),
                                true
                        ))
                        .toList()
        );
    }

    private void applyChange(
            Map<String, MutablePosition> positions,
            PartnerTransitionInstruction change
    ) {
        switch (change.action()) {
            case ENTRY -> applyEntry(positions, change);

            case EXIT -> applyExit(positions, change);

            case CAPITAL_ADDITION ->
                    applyCapitalAddition(positions, change);

            case CAPITAL_REDUCTION ->
                    applyCapitalReduction(positions, change);

            case REPLACE ->
                    applyReplacement(positions, change);
        }
    }

    private void applyEntry(
            Map<String, MutablePosition> positions,
            PartnerTransitionInstruction change
    ) {
        if (positions.containsKey(change.partyId())
                && positions.get(change.partyId()).active()) {
            throw new IllegalStateException(
                    "Partner is already active: "
                            + change.partyId()
            );
        }

        positions.put(
                change.partyId(),
                new MutablePosition(
                        change.capitalChange(),
                        BigDecimal.ZERO,
                        true
                )
        );
    }

    private void applyExit(
            Map<String, MutablePosition> positions,
            PartnerTransitionInstruction change
    ) {
        MutablePosition position =
                requireActive(positions, change.partyId());

        if (change.capitalChange()
                .compareTo(position.capital()) > 0) {
            throw new IllegalStateException(
                    "Exit capital exceeds partner capital: "
                            + change.partyId()
            );
        }

        position.active = false;
    }

    private void applyCapitalAddition(
            Map<String, MutablePosition> positions,
            PartnerTransitionInstruction change
    ) {
        MutablePosition position =
                requireActive(positions, change.partyId());

        position.capital = position.capital.add(
                change.capitalChange()
        );
    }

    private void applyCapitalReduction(
            Map<String, MutablePosition> positions,
            PartnerTransitionInstruction change
    ) {
        MutablePosition position =
                requireActive(positions, change.partyId());

        if (change.capitalChange()
                .compareTo(position.capital()) > 0) {
            throw new IllegalStateException(
                    "Capital reduction exceeds partner capital: "
                            + change.partyId()
            );
        }

        position.capital = position.capital.subtract(
                change.capitalChange()
        );
    }

    private void applyReplacement(
            Map<String, MutablePosition> positions,
            PartnerTransitionInstruction change
    ) {
        MutablePosition exiting =
                requireActive(positions, change.partyId());

        if (change.capitalChange()
                .compareTo(exiting.capital()) > 0) {
            throw new IllegalStateException(
                    "Replacement capital exceeds exiting partner capital"
            );
        }

        exiting.active = false;

        positions.put(
                change.replacementPartyId(),
                new MutablePosition(
                        change.capitalChange(),
                        BigDecimal.ZERO,
                        true
                )
        );
    }

    private MutablePosition requireActive(
            Map<String, MutablePosition> positions,
            String partyId
    ) {
        MutablePosition position = positions.get(partyId);

        if (position == null || !position.active()) {
            throw new IllegalStateException(
                    "Active partner not found: " + partyId
            );
        }

        return position;
    }

    private static final class MutablePosition {

        private BigDecimal capital;
        private final BigDecimal currentAccount;
        private boolean active;

        private MutablePosition(
                BigDecimal capital,
                BigDecimal currentAccount,
                boolean active
        ) {
            this.capital = capital;
            this.currentAccount = currentAccount;
            this.active = active;
        }

        BigDecimal capital() {
            return capital;
        }

        BigDecimal currentAccount() {
            return currentAccount;
        }

        boolean active() {
            return active;
        }
    }
}
```

There is one important distinction here:

**Exit capital is not automatically an accounting journal.**

The calculator establishes the successor opening position. The actual return/settlement of the exiting partner remains a separate economic/accounting operation.

That prevents this dangerous shortcut:

```text
partner exits
    ↓
reduce capital
    ↓
done
```

Instead:

```text
predecessor frozen position
        │
        ├── determine partner exit position
        │
        ├── settlement/capital return obligation
        │
        └── successor opening position
```

---

# 4. Add explicit transition validation

We should prevent economically ambiguous instructions before any transaction starts.

```java id="v1c7ps"
package tech.kayys.syirkah.syirkah.domain.transition;

import java.util.HashSet;
import java.util.Set;

public final class SyirkahTransitionInstructionValidator {

    public void validate(
            SyirkahTransition transition,
            SyirkahTransitionInstruction instruction
    ) {
        Set<String> affectedParties = new HashSet<>();

        for (PartnerTransitionInstruction change
                : instruction.partnerChanges()) {

            if (!affectedParties.add(change.partyId())) {
                throw new IllegalArgumentException(
                        "Partner appears more than once in transition: "
                                + change.partyId()
                );
            }

            if (change.action()
                    == PartnerTransitionAction.REPLACE
                    && affectedParties.contains(
                            change.replacementPartyId()
                    )) {
                throw new IllegalArgumentException(
                        "Replacement party cannot simultaneously "
                                + "be another affected party"
                );
            }
        }

        validateTransitionType(
                transition.type(),
                instruction
        );
    }

    private void validateTransitionType(
            SyirkahTransitionType type,
            SyirkahTransitionInstruction instruction
    ) {
        switch (type) {
            case PARTNER_ENTRY ->
                    requireAction(
                            instruction,
                            PartnerTransitionAction.ENTRY
                    );

            case PARTNER_EXIT ->
                    requireAction(
                            instruction,
                            PartnerTransitionAction.EXIT
                    );

            case CAPITAL_ADDITION ->
                    requireAction(
                            instruction,
                            PartnerTransitionAction.CAPITAL_ADDITION
                    );

            case CAPITAL_REDUCTION ->
                    requireAction(
                            instruction,
                            PartnerTransitionAction.CAPITAL_REDUCTION
                    );

            case PARTNER_REPLACEMENT ->
                    requireAction(
                            instruction,
                            PartnerTransitionAction.REPLACE
                    );

            case CONTRACT_RENEWAL -> {
                if (!instruction.partnerChanges().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Contract renewal cannot contain partner changes"
                    );
                }
            }

            case FULL_TERMINATION -> {
                if (!instruction.partnerChanges().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Full termination cannot contain "
                                    + "successor partner changes"
                    );
                }
            }
        }
    }

    private void requireAction(
            SyirkahTransitionInstruction instruction,
            PartnerTransitionAction expected
    ) {
        if (instruction.partnerChanges().stream()
                .anyMatch(change -> change.action() != expected)) {
            throw new IllegalArgumentException(
                    "Transition contains an incompatible partner action"
            );
        }
    }
}
```

---

# 5. Critical invariant: capital conservation

For transitions that merely move interests between partners:

```text
predecessor closing capital
        =
successor opening capital
```

For capital additions:

```text
successor opening capital
        =
predecessor closing capital
        + new capital
```

For capital reductions:

```text
successor opening capital
        =
predecessor closing capital
        - reduced capital
```

For replacement:

```text
exiting partner interest
        →
replacement partner opening interest
```

But **settlement cash flows are separate**.

Add a reconciliation result:

```java id="o7k2mz"
package tech.kayys.syirkah.syirkah.domain.transition;

import java.math.BigDecimal;
import java.util.List;

public record SyirkahTransitionReconciliation(
        BigDecimal predecessorCapital,
        BigDecimal successorCapital,
        BigDecimal externalCapitalChange,
        BigDecimal expectedSuccessorCapital,
        List<String> errors
) {

    public boolean passed() {
        return errors.isEmpty()
                && successorCapital.compareTo(
                        expectedSuccessorCapital
                ) == 0;
    }
}
```

Calculator:

```java id="2w9jcf"
public final class SyirkahTransitionReconciliationCalculator {

    public SyirkahTransitionReconciliation reconcile(
            BigDecimal predecessorCapital,
            BigDecimal successorCapital,
            BigDecimal externalCapitalChange
    ) {
        BigDecimal expected =
                predecessorCapital.add(externalCapitalChange);

        if (successorCapital.compareTo(expected) == 0) {
            return new SyirkahTransitionReconciliation(
                    predecessorCapital,
                    successorCapital,
                    externalCapitalChange,
                    expected,
                    List.of()
            );
        }

        return new SyirkahTransitionReconciliation(
                predecessorCapital,
                successorCapital,
                externalCapitalChange,
                expected,
                List.of(
                        "Successor opening capital does not "
                                + "reconcile with predecessor closing capital"
                )
        );
    }
}
```

This gives the transition its own equivalent of the correction reconciliation we already built.

---

# 6. Persist opening snapshots as immutable artifacts

Do not overwrite the predecessor snapshot.

```java id="0q1v8k"
public interface SyirkahOpeningSnapshotRepository {

    Uni<Void> save(SyirkahOpeningPosition position);

    Uni<Optional<SyirkahOpeningPosition>> findByTransition(
            String tenantId,
            String ledgerId,
            String transitionId
    );

    Uni<Optional<SyirkahOpeningPosition>> findByContract(
            String tenantId,
            String ledgerId,
            String contractId
    );
}
```

Recommended lineage:

```text
SYIRKAH-CLOSE-SNAPSHOT-001
        │
        │ predecessorSnapshotId
        ▼
SYIRKAH-TRANSITION-001
        │
        ▼
SYIRKAH-OPENING-SNAPSHOT-002
        │
        ▼
CONTRACT-002
```

That gives historical reporting a clean boundary.

---

# 7. Transition-specific accounting sources

Use the existing transactional journal mechanism rather than introducing a parallel ledger.

```java id="y5q3ns"
public final class SyirkahTransitionSourceTypes {

    public static final String PARTNER_ENTRY =
            "SYIRKAH_PARTNER_ENTRY";

    public static final String PARTNER_EXIT =
            "SYIRKAH_PARTNER_EXIT";

    public static final String CAPITAL_ADDITION =
            "SYIRKAH_CAPITAL_ADDITION";

    public static final String CAPITAL_REDUCTION =
            "SYIRKAH_CAPITAL_REDUCTION";

    public static final String PARTNER_REPLACEMENT =
            "SYIRKAH_PARTNER_REPLACEMENT";

    public static final String CONTRACT_RENEWAL =
            "SYIRKAH_CONTRACT_RENEWAL";

    public static final String FULL_TERMINATION =
            "SYIRKAH_FULL_TERMINATION";

    private SyirkahTransitionSourceTypes() {
    }
}
```

Every journal remains traceable:

```text
JournalEntry
   │
   ├── sourceType = SYIRKAH_CAPITAL_ADDITION
   └── sourceId   = TRANSITION-001
```

And the transition itself points back to:

```text
predecessor snapshot
successor opening snapshot
journal entries
settlement obligations
audit evidence
```

---

# 8. Integration tests to lock the boundary

These are the tests I would add immediately.

```java id="x2w4rf"
@Test
void partnerEntryMustOpenFromFrozenPredecessorPosition() {
    var predecessor = position(
            "PARTNER-A",
            money("100000000"),
            money("10000000")
    );

    var instruction =
            new SyirkahTransitionInstruction(
                    List.of(
                            PartnerTransitionInstruction.entry(
                                    "PARTNER-B",
                                    money("50000000")
                            )
                    ),
                    money("50000000")
            );

    var opening = calculator.calculate(
            "CONTRACT-002",
            "SNAPSHOT-001",
            "IDR",
            Map.of("PARTNER-A", predecessor),
            instruction
    );

    assertThat(opening.openingCapital())
            .isEqualByComparingTo("150000000");

    assertThat(opening.partners())
            .extracting(PartnerOpeningPosition::partyId)
            .containsExactlyInAnyOrder(
                    "PARTNER-A",
                    "PARTNER-B"
            );
}
```

Capital reduction:

```java id="xq3k2m"
@Test
void capitalReductionMustNotMutatePredecessorSnapshot() {
    var predecessor = position(
            "PARTNER-A",
            money("100000000"),
            money("10000000")
    );

    var instruction =
            new SyirkahTransitionInstruction(
                    List.of(
                            PartnerTransitionInstruction
                                    .capitalReduction(
                                            "PARTNER-A",
                                            money("20000000")
                                    )
                    ),
                    money("0")
            );

    var opening = calculator.calculate(
            "CONTRACT-002",
            "SNAPSHOT-001",
            "IDR",
            Map.of("PARTNER-A", predecessor),
            instruction
    );

    assertThat(opening.openingCapital())
            .isEqualByComparingTo("80000000");

    assertThat(predecessor.openingCapital())
            .isEqualByComparingTo("100000000");
}
```

And the most important protection:

```java id="g6m1ds"
@Test
void successorCannotBecomeActiveBeforePredecessorSnapshotIsFrozen() {
    var successor = createSuccessorContract();

    successor.markPendingPredecessor();

    assertThatThrownBy(successor::activate)
            .isInstanceOf(IllegalStateException.class);

    successor.markReady();
    successor.activate();

    assertThat(successor.activationStatus())
            .isEqualTo(
                    SyirkahContractActivationStatus.ACTIVE
            );
}
```

### Resulting boundary

We now have:

```text
                PREDECESSOR
                    │
             financial close
                    │
                    ▼
           FROZEN SNAPSHOT
                    │
          ┌─────────┴─────────┐
          │                   │
       settlement          transition
          │                   │
          └─────────┬─────────┘
                    ▼
          OPENING POSITION
                    │
                    ▼
              SUCCESSOR
                    │
                    ▼
                  ACTIVE
```

This is the important Syirkah extension: **entry, exit, capital changes and replacement become lifecycle events around a determined financial position, rather than retroactive edits to an existing partnership.**

Next is the **full termination/liquidation branch**: asset realization → liability settlement → final result → allocation → capital return → termination, with the same immutable snapshot and accounting reconciliation boundaries.

////////////

## Full liquidation branch

The liquidation model should therefore be a **separate lifecycle from ordinary period close**. Ordinary `FinancialResult` tells us the book result; liquidation determines what remains after realization and liabilities, then applies the applicable Syirkah result/capital policy.

### 1. `SyirkahLiquidation`

```java
package tech.kayys.syirkah.accounting.domain.liquidation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class SyirkahLiquidation {

    public enum Status {
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

    private final String id;
    private final String tenantId;
    private final String ledgerId;
    private final String contractId;
    private final LocalDate liquidationDate;

    private Status status;
    private String reason;
    private String completedBy;
    private Instant completedAt;

    private SyirkahLiquidation(
            String id,
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate liquidationDate,
            String reason
    ) {
        this.id = requireText(id, "id");
        this.tenantId = requireText(tenantId, "tenantId");
        this.ledgerId = requireText(ledgerId, "ledgerId");
        this.contractId = requireText(contractId, "contractId");
        this.liquidationDate = Objects.requireNonNull(
                liquidationDate,
                "liquidationDate must not be null"
        );
        this.reason = requireText(reason, "reason");
        this.status = Status.NOT_STARTED;
    }

    public static SyirkahLiquidation open(
            String id,
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate liquidationDate,
            String reason
    ) {
        return new SyirkahLiquidation(
                id,
                tenantId,
                ledgerId,
                contractId,
                liquidationDate,
                reason
        );
    }

    public void beginAssetRealization() {
        requireStatus(Status.NOT_STARTED);
        status = Status.ASSET_REALIZATION;
    }

    public void beginLiabilitySettlement() {
        requireStatus(Status.ASSET_REALIZATION);
        status = Status.LIABILITY_SETTLEMENT;
    }

    public void beginReconciliation() {
        requireStatus(Status.LIABILITY_SETTLEMENT);
        status = Status.RECONCILIATION;
    }

    public void markResultDetermined() {
        requireStatus(Status.RECONCILIATION);
        status = Status.RESULT_DETERMINED;
    }

    public void beginDistribution() {
        requireStatus(Status.RESULT_DETERMINED);
        status = Status.DISTRIBUTION;
    }

    public void beginCapitalReturn() {
        requireStatus(Status.DISTRIBUTION);
        status = Status.CAPITAL_RETURN;
    }

    public void complete(String operator) {
        requireStatus(Status.CAPITAL_RETURN);

        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("operator is required");
        }

        status = Status.COMPLETED;
        completedBy = operator;
        completedAt = Instant.now();
    }

    public void cancel(String operator) {
        if (status == Status.COMPLETED) {
            throw new IllegalStateException(
                    "Completed liquidation cannot be cancelled"
            );
        }

        if (status == Status.CAPITAL_RETURN) {
            throw new IllegalStateException(
                    "Liquidation cannot be cancelled after capital return has started"
            );
        }

        status = Status.CANCELLED;
        completedBy = operator;
        completedAt = Instant.now();
    }

    private void requireStatus(Status expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Expected liquidation status " + expected
                            + " but was " + status
            );
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getLedgerId() {
        return ledgerId;
    }

    public String getContractId() {
        return contractId;
    }

    public LocalDate getLiquidationDate() {
        return liquidationDate;
    }

    public Status getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public String getCompletedBy() {
        return completedBy;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
```

The key property is that **`COMPLETED` is terminal**. There is no reopening of a completed liquidation. A subsequent accounting correction must be represented through the existing correction machinery.

---

# 2. Asset realization

Do not modify the original asset record. Liquidation creates an immutable realization artifact.

```java
package tech.kayys.syirkah.accounting.domain.liquidation;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record LiquidationRealization(
        String id,
        String tenantId,
        String ledgerId,
        String liquidationId,
        String assetReference,
        Money carryingAmount,
        Money realizedAmount,
        String counterpartyReference,
        LocalDate realizationDate,
        String sourceType,
        String sourceId,
        String recordedBy,
        Instant recordedAt
) {

    public LiquidationRealization {
        requireText(id, "id");
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(liquidationId, "liquidationId");
        requireText(assetReference, "assetReference");
        Objects.requireNonNull(carryingAmount, "carryingAmount");
        Objects.requireNonNull(realizedAmount, "realizedAmount");
        Objects.requireNonNull(realizationDate, "realizationDate");
        requireText(sourceType, "sourceType");
        requireText(sourceId, "sourceId");
        requireText(recordedBy, "recordedBy");

        if (realizedAmount.isNegative()) {
            throw new IllegalArgumentException(
                    "realizedAmount cannot be negative"
            );
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
```

This supports both:

```text
carrying amount = 40,000,000
realized amount = 42,000,000
```

and:

```text
carrying amount = 40,000,000
realized amount = 35,000,000
```

without pretending that realization value is always equal to book value.

---

# 3. Liability settlement

Likewise, liability settlement is an immutable financial fact.

```java
package tech.kayys.syirkah.accounting.domain.liquidation;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record LiquidationLiabilitySettlement(
        String id,
        String tenantId,
        String ledgerId,
        String liquidationId,
        String liabilityReference,
        Money liabilityAmount,
        Money settledAmount,
        Money remainingAmount,
        LocalDate settlementDate,
        String settlementReference,
        String sourceType,
        String sourceId,
        String settledBy,
        Instant settledAt
) {

    public LiquidationLiabilitySettlement {
        requireText(id, "id");
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(liquidationId, "liquidationId");
        requireText(liabilityReference, "liabilityReference");

        Objects.requireNonNull(liabilityAmount, "liabilityAmount");
        Objects.requireNonNull(settledAmount, "settledAmount");
        Objects.requireNonNull(remainingAmount, "remainingAmount");
        Objects.requireNonNull(settlementDate, "settlementDate");

        requireText(settlementReference, "settlementReference");
        requireText(sourceType, "sourceType");
        requireText(sourceId, "sourceId");
        requireText(settledBy, "settledBy");

        if (settledAmount.isNegative()) {
            throw new IllegalArgumentException(
                    "settledAmount cannot be negative"
            );
        }

        if (remainingAmount.isNegative()) {
            throw new IllegalArgumentException(
                    "remainingAmount cannot be negative"
            );
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
```

Importantly, this permits a policy to explicitly represent an unpaid remainder rather than silently assuming every liability was fully settled.

---

# 4. Liquidation result

The core calculation should not be hard-coded to every Syirkah type.

```java
package tech.kayys.syirkah.accounting.domain.liquidation;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

public record SyirkahLiquidationResult(
        Money realizedAssets,
        Money settledLiabilities,
        Money remainingNetAssets,
        Money initialCapital,
        Money liquidationGainOrLoss
) {

    public SyirkahLiquidationResult {
        if (realizedAssets == null
                || settledLiabilities == null
                || remainingNetAssets == null
                || initialCapital == null
                || liquidationGainOrLoss == null) {
            throw new IllegalArgumentException(
                    "All liquidation result amounts are required"
            );
        }
    }

    public boolean isGain() {
        return liquidationGainOrLoss.isPositive();
    }

    public boolean isLoss() {
        return liquidationGainOrLoss.isNegative();
    }

    public boolean isBreakEven() {
        return liquidationGainOrLoss.isZero();
    }
}
```

The basic accounting relationship is:

```text
remaining net assets
    =
realized assets
    -
settled liabilities
```

and:

```text
liquidation gain/loss
    =
remaining net assets
    -
initial capital
```

This is deliberately a **calculation**, not yet a universal Islamic-law allocation rule.

---

# 5. Policy SPI

Different Syirkah types can have different rules for what the result means and who bears the economic consequence.

```java
package tech.kayys.syirkah.accounting.domain.liquidation;

import java.math.BigDecimal;
import java.util.List;

public interface SyirkahLiquidationResultPolicy {

    SyirkahLiquidationResult calculate(
            LiquidationCalculationContext context
    );

    List<LiquidationPartnerResult> allocateResult(
            LiquidationAllocationContext context
    );
}
```

Context:

```java
package tech.kayys.syirkah.accounting.domain.liquidation;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.List;

public record LiquidationCalculationContext(
        String liquidationId,
        String syirkahType,
        Money realizedAssets,
        Money settledLiabilities,
        Money initialCapital
) {}
```

Partner result:

```java
package tech.kayys.syirkah.accounting.domain.liquidation;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

public record LiquidationPartnerResult(
        String partyId,
        Money capitalBeforeReturn,
        Money resultAllocation,
        Money capitalReturn,
        Money finalSettlement
) {}
```

The policy implementation can therefore reuse the already-existing:

- `LossAllocationPolicy`
- `ProfitSharingRatio`
- `SyirkahProfitSharingEngine`
- `SyirkahResultCalculator`

rather than introducing another competing allocation framework.

---

# 6. Capital-return obligation

Capital return should be its own artifact.

```java
package tech.kayys.syirkah.accounting.domain.liquidation;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.Instant;

public final class PartnerCapitalReturnObligation {

    public enum Status {
        CALCULATED,
        APPROVED,
        POSTED,
        SETTLED,
        CANCELLED
    }

    private final String id;
    private final String tenantId;
    private final String ledgerId;
    private final String liquidationId;
    private final String partyId;
    private final Money amount;
    private final String currency;

    private Status status;
    private String journalEntryId;
    private String settlementReference;
    private Instant settledAt;

    public PartnerCapitalReturnObligation(
            String id,
            String tenantId,
            String ledgerId,
            String liquidationId,
            String partyId,
            Money amount,
            String currency
    ) {
        this.id = requireText(id, "id");
        this.tenantId = requireText(tenantId, "tenantId");
        this.ledgerId = requireText(ledgerId, "ledgerId");
        this.liquidationId = requireText(liquidationId, "liquidationId");
        this.partyId = requireText(partyId, "partyId");

        if (amount == null || amount.isNegative()) {
            throw new IllegalArgumentException(
                    "amount must not be negative"
            );
        }

        this.amount = amount;
        this.currency = requireText(currency, "currency");
        this.status = Status.CALCULATED;
    }

    public void approve() {
        requireStatus(Status.CALCULATED);
        status = Status.APPROVED;
    }

    public void attachJournal(String journalEntryId) {
        requireStatus(Status.APPROVED);
        this.journalEntryId = requireText(
                journalEntryId,
                "journalEntryId"
        );
        status = Status.POSTED;
    }

    public void settle(
            String settlementReference,
            Instant settledAt
    ) {
        requireStatus(Status.POSTED);

        this.settlementReference = requireText(
                settlementReference,
                "settlementReference"
        );
        this.settledAt = settledAt;
        status = Status.SETTLED;
    }

    private void requireStatus(Status expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Expected " + expected + " but was " + status
            );
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getLedgerId() {
        return ledgerId;
    }

    public String getLiquidationId() {
        return liquidationId;
    }

    public String getPartyId() {
        return partyId;
    }

    public Money getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Status getStatus() {
        return status;
    }

    public String getJournalEntryId() {
        return journalEntryId;
    }

    public String getSettlementReference() {
        return settlementReference;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}
```

---

# 7. Stable accounting sources

Add these alongside the existing Syirkah correction/distribution source types:

```java
public final class SyirkahLiquidationSourceTypes {

    private SyirkahLiquidationSourceTypes() {
    }

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
}
```

Use deterministic source IDs:

```text
LIQ-001:REALIZATION:ASSET-001
LIQ-001:LIABILITY:AP-001
LIQ-001:RESULT
LIQ-001:PARTY:PARTNER-A:PROFIT
LIQ-001:PARTY:PARTNER-A:CAPITAL_RETURN
```

That gives the existing journal idempotency mechanism a stable identity.

---

# 8. Liquidation reconciliation

This is the critical gate.

```java
package tech.kayys.syirkah.accounting.domain.liquidation;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.List;

public record SyirkahLiquidationReconciliation(
        String liquidationId,
        Money realizedAssets,
        Money settledLiabilities,
        Money calculatedNetAssets,
        Money allocatedResult,
        Money capitalReturns,
        Money accountingNetMovement,
        List<String> errors
) {

    public boolean isBalanced() {
        return errors.isEmpty()
                && calculatedNetAssets.equals(
                        realizedAssets.subtract(settledLiabilities)
                )
                && capitalReturns.equals(accountingNetMovement);
    }

    public boolean passed() {
        return isBalanced();
    }
}
```

I would make the actual production version more granular:

```java
public record LiquidationReconciliation(
        String liquidationId,
        LiquidationSection assetRealization,
        LiquidationSection liabilitySettlement,
        LiquidationSection result,
        LiquidationSection allocation,
        LiquidationSection capitalReturn,
        LiquidationSection accounting,
        List<String> errors
) {

    public boolean passed() {
        return errors.isEmpty()
                && assetRealization.passed()
                && liabilitySettlement.passed()
                && result.passed()
                && allocation.passed()
                && capitalReturn.passed()
                && accounting.passed();
    }
}
```

This aligns with the correction reconciliation architecture already established.

---

# 9. Repository boundaries

```java
public interface SyirkahLiquidationRepository {

    Uni<Optional<SyirkahLiquidation>> findByIdForUpdate(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<Void> save(SyirkahLiquidation liquidation);
}
```

```java
public interface LiquidationRealizationRepository {

    Uni<List<LiquidationRealization>> findByLiquidation(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<Void> save(LiquidationRealization realization);
}
```

```java
public interface LiquidationLiabilitySettlementRepository {

    Uni<List<LiquidationLiabilitySettlement>> findByLiquidation(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<Void> save(LiquidationLiabilitySettlement settlement);
}
```

```java
public interface PartnerCapitalReturnRepository {

    Uni<List<PartnerCapitalReturnObligation>> findByLiquidation(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<Optional<PartnerCapitalReturnObligation>> findByIdForUpdate(
            String tenantId,
            String ledgerId,
            String obligationId
    );

    Uni<Void> save(PartnerCapitalReturnObligation obligation);
}
```

Add uniqueness:

```sql
CREATE UNIQUE INDEX ux_syirkah_liquidation_source
ON syirkah_liquidation_source (
    tenant_id,
    ledger_id,
    liquidation_id,
    source_type,
    source_id
);
```

And:

```sql
CREATE UNIQUE INDEX ux_syirkah_liquidation_active_contract
ON syirkah_contract (
    tenant_id,
    ledger_id
)
WHERE activation_status = 'ACTIVE';
```

The second index is only appropriate if the existing business scope is truly one active Syirkah contract per ledger.

---

# 10. Application orchestration

The application service should compose the existing components rather than implement accounting itself.

```java
public interface SyirkahLiquidationService {

    Uni<String> open(
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate liquidationDate,
            String reason,
            String operator
    );

    Uni<Void> realizeAsset(
            LiquidationRealization realization
    );

    Uni<Void> settleLiability(
            LiquidationLiabilitySettlement settlement
    );

    Uni<SyirkahLiquidationResult> determineResult(
            String tenantId,
            String ledgerId,
            String liquidationId,
            String operator
    );

    Uni<Void> calculateDistribution(
            String tenantId,
            String ledgerId,
            String liquidationId,
            String operator
    );

    Uni<Void> postCapitalReturns(
            String tenantId,
            String ledgerId,
            String liquidationId,
            String operator
    );

    Uni<SyirkahLiquidationReconciliation> reconcile(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<Void> complete(
            String tenantId,
            String ledgerId,
            String liquidationId,
            String operator
    );
}
```

---

# 11. Atomic completion

Completion is the most important transaction.

```java
public interface SyirkahLiquidationCompletionTransaction {

    Uni<Void> complete(
            String tenantId,
            String ledgerId,
            String liquidationId,
            String operator
    );
}
```

Implementation flow:

```text
BEGIN TRANSACTION

  lock liquidation

  verify status = CAPITAL_RETURN

  lock all capital-return obligations

  verify:
      every required realization exists
      every required liability settlement is reconciled
      liquidation result exists
      partner allocation exists
      capital returns are POSTED/SETTLED
      reconciliation PASSED

  lock predecessor Syirkah contract

  verify contract is still ACTIVE

  complete liquidation

  contract -> TERMINATED

  persist liquidation completion audit

  persist:
      LIQUIDATION_COMPLETED
      CONTRACT_TERMINATED

  persist outbox records

COMMIT
```

Do **not** call external payment systems or event consumers inside this transaction.

---

# 12. Completion implementation

```java
@ApplicationScoped
public final class DefaultSyirkahLiquidationCompletionTransaction
        implements SyirkahLiquidationCompletionTransaction {

    private final AccountingTransactionManager transactionManager;
    private final SyirkahLiquidationRepository liquidationRepository;
    private final SyirkahContractRepository contractRepository;
    private final PartnerCapitalReturnRepository capitalReturnRepository;
    private final SyirkahLiquidationReconciliationService reconciliationService;

    @Override
    public Uni<Void> complete(
            String tenantId,
            String ledgerId,
            String liquidationId,
            String operator
    ) {
        return transactionManager.inTransaction(() ->
                liquidationRepository.findByIdForUpdate(
                        tenantId,
                        ledgerId,
                        liquidationId
                ).onItem().ifNull().failWith(
                        () -> new IllegalStateException(
                                "Liquidation not found: " + liquidationId
                        )
                ).chain(liquidation ->
                        reconciliationService.reconcileInsideTransaction(
                                tenantId,
                                ledgerId,
                                liquidationId
                        ).chain(reconciliation -> {

                            if (!reconciliation.passed()) {
                                return Uni.createFrom().failure(
                                        new IllegalStateException(
                                                "Liquidation reconciliation failed: "
                                                        + reconciliation.errors()
                                        )
                                );
                            }

                            return contractRepository
                                    .findByIdForUpdate(
                                            tenantId,
                                            ledgerId,
                                            liquidation.getContractId()
                                    )
                                    .onItem().ifNull().failWith(
                                            () -> new IllegalStateException(
                                                    "Syirkah contract not found"
                                            )
                                    )
                                    .chain(contract -> {

                                        if (!contract.isActive()) {
                                            return Uni.createFrom().failure(
                                                    new IllegalStateException(
                                                            "Syirkah contract is not active"
                                                    )
                                            );
                                        }

                                        liquidation.complete(operator);

                                        contract.terminate(
                                                liquidationId,
                                                operator
                                        );

                                        return contractRepository
                                                .save(contract)
                                                .chain(() ->
                                                        liquidationRepository
                                                                .save(liquidation)
                                                );
                                    });
                        })
                )
        );
    }
}
```

The actual implementation should use the project's existing `Uni` conventions and transaction adapter, but the transaction boundary should remain exactly here.

---

# 13. Important distinction: liquidation result vs final distribution

The flow should now be:

```text
Existing accounting
       │
       ▼
Period close / final result
       │
       ▼
Liquidation decision
       │
       ├── asset realization
       │
       ├── liability settlement
       │
       ▼
Remaining net assets
       │
       ▼
Liquidation result
       │
       ▼
Syirkah-specific result allocation
       │
       ├── profit distribution
       └── loss responsibility
       │
       ▼
Capital return obligations
       │
       ▼
Settlement
       │
       ▼
Reconciliation
       │
       ▼
Contract TERMINATED
```

This is especially important because the source states that, after the Syirkah is closed, the parties must determine whether assets are liquidated or distributed in kind, and that the business assets to be distributed are considered after business liabilities. Hukum-muamalah Hukum-muamalah

So **do not simply take the last `ProfitSharingResult` and pay it out**.

---

# 14. Tests

The first integration suite should cover at least these cases:

```java
@Test
void shouldLiquidateProfitableSyirkah() {
    // initial capital
    // realize assets above capital
    // settle liabilities
    // determine positive liquidation result
    // allocate result
    // create capital returns
    // reconcile
    // terminate contract
}
```

```java
@Test
void shouldLiquidateAtBreakEven() {
    // remaining net assets == initial capital
    // no liquidation gain/loss
    // return capital
    // terminate contract
}
```

```java
@Test
void shouldLiquidateAtLoss() {
    // remaining net assets < initial capital
    // policy determines loss responsibility
    // capital return reflects applicable policy
    // terminate only after reconciliation
}
```

```java
@Test
void shouldBlockCompletionWhenLiabilityRemainsUnreconciled() {
    // unpaid/unreconciled liability
    // reconciliation fails
    // contract remains ACTIVE
    // liquidation remains CAPITAL_RETURN
}
```

```java
@Test
void shouldNotCompleteLiquidationWhenCapitalReturnIsMissing() {
    // result exists
    // distribution exists
    // one required capital return missing
    // completion rejected
}
```

```java
@Test
void shouldNotCompleteSameLiquidationTwice() {
    // first completion succeeds
    // second completion fails
    // no duplicate accounting
    // no duplicate termination
}
```

```java
@Test
void shouldBeIdempotentForAssetRealizationSource() {
    // same liquidation + source identity
    // second attempt does not create another accounting artifact
}
```

```java
@Test
void shouldKeepOriginalRealizationAndSettlementArtifactsImmutable() {
    // corrections create compensating artifacts
    // original realization remains unchanged
}
```

And the concurrency test:

```java
@Test
void shouldAllowOnlyOneConcurrentLiquidationCompletion() {
    // two workers attempt completion simultaneously
    // exactly one successful terminal transition
    // exactly one TERMINATED contract
    // exactly one completion event
}
```

### One additional guard

A liquidation should not be allowed to start against a contract whose predecessor transition is still unresolved:

```text
ACTIVE contract
    +
pending partner transition
    =
BLOCK liquidation
```

That prevents the lifecycle branches from producing contradictory terminal states.

The next implementation slice after this is **liquidation accounting policy + concrete Mudharabah/Musyarakah result allocation adapters**, then the **liquidation-to-close/fiscal-period finalization gate** so termination becomes part of the same atomic financial finalization model.

/////////////

## Next implementation slice: liquidation accounting policy and close-finalization integration

The next step is to connect the liquidation lifecycle to the existing Syirkah result engine and accounting posting boundary. The important separation is:

- Accounting calculation: establish realized assets, settled liabilities, and remaining net assets.
- Syirkah policy: determine how the liquidation result is allocated for the relevant contract type.
- Accounting execution: post the approved journal entries idempotently.
- Finalization: terminate the contract only after settlement and reconciliation gates pass.

The liquidation policy should reuse existing result and loss-allocation abstractions rather than introducing a competing profit-sharing engine. The source material specifically distinguishes the liquidation of assets from the ordinary book result, and describes distributing business assets after business liabilities.&#x20;

Hukum-muamalah.docx



## 1. Accounting policy boundary

Keep account selection and journal construction out of the domain aggregate. The accounting policy translates liquidation artifacts into journal commands, while the existing posting transaction handles idempotency, fiscal-period checks, account locking, and outbox persistence.

```
package tech.kayys.syirkah.accounting.domain.liquidation;

import tech.kayys.syirkah.accounting.application.command.PostJournalEntryCommand;

public interface SyirkahLiquidationAccountingPolicy {

    PostJournalEntryCommand assetRealization(
            LiquidationRealization realization,
            String operator
    );

    PostJournalEntryCommand liabilitySettlement(
            LiquidationLiabilitySettlement settlement,
            String operator
    );

    PostJournalEntryCommand resultAllocation(
            String liquidationId,
            LiquidationPartnerResult allocation,
            String operator
    );

    PostJournalEntryCommand capitalReturn(
            PartnerCapitalReturnObligation obligation,
            String operator
    );
}
```

The package for `PostJournalEntryCommand` should match the existing command location in the project. Each generated command must carry tenant and ledger identity and a stable source identity.

Example source identity:

```
String sourceId = liquidationId
        + ":PARTY:"
        + allocation.partyId()
        + ":CAPITAL_RETURN";
```

The policy should obtain account mappings from configured accounting rules, not hard-code account numbers into the liquidation aggregate.

## 2. Explicit result policies for Mudharabah and Musyarakah

Use the existing Syirkah type and loss-allocation infrastructure. Do not implement a single universal `remainingNetAssets / partnerCount` algorithm.

A small resolver can select a policy:

```
package tech.kayys.syirkah.accounting.domain.liquidation;

import java.util.Map;
import java.util.Objects;

public final class SyirkahLiquidationResultPolicyResolver {

    private final Map<String, SyirkahLiquidationResultPolicy> policies;

    public SyirkahLiquidationResultPolicyResolver(
            Map<String, SyirkahLiquidationResultPolicy> policies
    ) {
        this.policies = Map.copyOf(policies);
    }

    public SyirkahLiquidationResultPolicy resolve(String syirkahType) {
        Objects.requireNonNull(syirkahType, "syirkahType");

        SyirkahLiquidationResultPolicy policy = policies.get(syirkahType);

        if (policy == null) {
            throw new IllegalStateException(
                    "No liquidation result policy configured for "
                            + syirkahType
            );
        }

        return policy;
    }
}
```

Register separate implementations for `MUDHARABAH` and `MUSYARAKAH`. Each should delegate to the existing profit-sharing and loss-allocation policies wherever applicable, and require explicit configuration for liquidation-specific treatment.

Important: the source material describes a Mudharabah example in which capital is returned to the capital provider and the profit is allocated according to the agreed ratio; it also discusses loss and breach separately. That should inform the Mudharabah policy, but the system should not silently apply those rules to every Syirkah type.&#x20;

Hukum-muamalah.docx



## 3. A finalization gate for liquidation

Liquidation must join the same gate-based validation approach used by period close and correction reclose.

```
package tech.kayys.syirkah.accounting.application.liquidation;

import io.smallrye.mutiny.Uni;

public interface SyirkahLiquidationFinalizationGate {

    Uni<LiquidationGateResult> validate(
            LiquidationFinalizationContext context
    );
}
```

```
package tech.kayys.syirkah.accounting.application.liquidation;

import java.util.List;

public record LiquidationGateResult(
        boolean passed,
        List<String> errors
) {
    public LiquidationGateResult {
        errors = List.copyOf(errors == null ? List.of() : errors);

        if (passed && !errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "A passed gate cannot contain errors"
            );
        }

        if (!passed && errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "A failed gate must explain why it failed"
            );
        }
    }

    public static LiquidationGateResult success() {
        return new LiquidationGateResult(true, List.of());
    }

    public static LiquidationGateResult failure(List<String> errors) {
        return new LiquidationGateResult(false, errors);
    }
}
```

The context should include the identifiers needed to verify the complete financial chain:

```
package tech.kayys.syirkah.accounting.application.liquidation;

public record LiquidationFinalizationContext(
        String tenantId,
        String ledgerId,
        String contractId,
        String liquidationId,
        String fiscalPeriodId
) {}
```

The gate should block completion when any of the following is true:

- Asset realization artifacts are missing or their accounting postings do not reconcile.
- Liability settlement records have unexplained outstanding amounts.
- The liquidation result has not been approved.
- Partner result allocations do not reconcile to the calculated result.
- Capital-return obligations are missing, incorrectly posted, or unsettled where settlement is required.
- The accounting journal totals differ from the liquidation artifacts.
- The contract has a conflicting pending partner transition.
- The liquidation has already completed or the fiscal period is in an incompatible state.

Do not treat an unpaid liability as settled merely because its accounting journal exists. Financial settlement evidence and journal posting are separate checks.

## 4. Atomic termination transaction

The final transaction should lock all relevant records and perform the final gate inside the transaction. Avoid a pattern where reconciliation passes in one transaction and the contract is terminated in another without revalidation.

```
package tech.kayys.syirkah.accounting.application.liquidation;

import io.smallrye.mutiny.Uni;

public interface SyirkahLiquidationFinalizationTransaction {

    Uni<Void> finalizeLiquidation(
            LiquidationFinalizationContext context,
            String operator
    );
}
```

Implementation sequence:

```
BEGIN TRANSACTION

  lock liquidation
  lock fiscal period
  lock Syirkah contract
  lock capital-return obligations
  lock relevant settlement and accounting records

  verify liquidation is at CAPITAL_RETURN
  verify contract is still ACTIVE
  run final liquidation reconciliation
  run finalization gates

  mark liquidation COMPLETED
  mark contract TERMINATED

  persist audit records
  persist transactional outbox events

COMMIT
```

The completion path should use the project's existing transaction manager and the same internal posting operations used by other financial workflows. Avoid starting a nested transaction for each journal.

## 5. Tests to add before connecting the full flow

| Test                                       | Expected result                          |
| ------------------------------------------ | ---------------------------------------- |
| Missing asset realization posting          | Completion blocked                       |
| Unreconciled liability                     | Completion blocked                       |
| No applicable liquidation policy           | Calculation rejected                     |
| Allocation differs from calculated result  | Completion blocked                       |
| Capital-return journal missing             | Completion blocked                       |
| Contract has unresolved partner transition | Completion blocked                       |
| Concurrent completion requests             | One terminal transition                  |
| Retry after successful completion          | No duplicate journal or completion event |

The next coding step is to implement the concrete policy adapters against the exact existing `SyirkahProfitSharingEngine`, `LossAllocationPolicyResolver`, and journal command packages, then wire the liquidation gate into the current finalization transaction. This preserves the existing platform and adds the Islamic-business-specific behavior where it belongs rather than duplicating the accounting core.
Continue with the persistence layer. One correction from the previous step: the framework snapshot does **not establish a Flyway/Liquibase convention for accounting**, so I would not invent one. The repository contract should stay infrastructure-neutral; the concrete adapter can use the project's existing database mechanism.

## 11. Keep the snapshot repository in the application port layer

Because the existing accounting application already owns ports such as `JournalEntryRepository`, put the new repository alongside them:

```text
finance/accounting/syirkah-accounting-application/
└── src/main/java/
    └── tech/kayys/syirkah/accounting/application/port/
        └── SyirkahCloseSnapshotRepository.java
```

The existing application module already depends on the accounting domain and the Islamic capability, so this fits the current layering. syirkah-framework(1)

```java id="48317"
package tech.kayys.syirkah.accounting.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.islamic.syirkah.SyirkahCloseSnapshot;

import java.util.Optional;

public interface SyirkahCloseSnapshotRepository {

    Uni<Optional<SyirkahCloseSnapshot>> findByCalculationId(
            String tenantId,
            String ledgerId,
            String calculationId);

    Uni<Optional<SyirkahCloseSnapshot>> findByCloseCycleId(
            String tenantId,
            String ledgerId,
            String closeCycleId);

    Uni<Void> save(SyirkahCloseSnapshot snapshot);
}
```

I prefer this over putting persistence interfaces directly into the domain package because the current accounting application already follows this port structure.

---

# 12. Do not persist the allocation list as a mutable JSON blob

For this particular accounting use case, I recommend normalized tables:

```text
syirkah_close_snapshot
        |
        +---- syirkah_close_snapshot_party
        |
        +---- syirkah_close_snapshot_allocation
```

That gives us proper auditability and allows reporting without reconstructing JSON.

### Header

```sql id="3f6b1d"
CREATE TABLE syirkah_close_snapshot (
    calculation_id        VARCHAR(100) NOT NULL,
    close_cycle_id        VARCHAR(100) NOT NULL,

    tenant_id             VARCHAR(100) NOT NULL,
    ledger_id             VARCHAR(100) NOT NULL,
    fiscal_period_id      VARCHAR(100) NOT NULL,

    period_start          DATE NOT NULL,
    period_end            DATE NOT NULL,

    contract_id           VARCHAR(100) NOT NULL,
    contract_version      INTEGER NOT NULL,

    revenue               DECIMAL(19, 4) NOT NULL,
    direct_costs          DECIMAL(19, 4) NOT NULL,
    operating_costs       DECIMAL(19, 4) NOT NULL,
    other_costs           DECIMAL(19, 4) NOT NULL,
    net_result            DECIMAL(19, 4) NOT NULL,

    result_type            VARCHAR(30) NOT NULL,
    calculation_reference  VARCHAR(200) NOT NULL,

    created_at             TIMESTAMP NOT NULL,

    CONSTRAINT pk_syirkah_close_snapshot
        PRIMARY KEY (calculation_id),

    CONSTRAINT uq_syirkah_close_snapshot_cycle
        UNIQUE (tenant_id, ledger_id, close_cycle_id),

    CONSTRAINT uq_syirkah_close_snapshot_calc_scope
        UNIQUE (tenant_id, ledger_id, calculation_id),

    CONSTRAINT ck_syirkah_close_snapshot_period
        CHECK (period_end >= period_start),

    CONSTRAINT ck_syirkah_close_snapshot_contract_version
        CHECK (contract_version > 0)
);
```

The important uniqueness rule is:

```text
tenant + ledger + closeCycle
```

A retry for the same close cannot create another snapshot.

---

# 13. Snapshot party table

This captures the **contract state actually used**.

```sql id="6w6n2z"
CREATE TABLE syirkah_close_snapshot_party (
    calculation_id        VARCHAR(100) NOT NULL,
    party_id              VARCHAR(100) NOT NULL,

    role                  VARCHAR(50) NOT NULL,
    capital_contribution  DECIMAL(19, 4) NOT NULL,
    profit_sharing_ratio  DECIMAL(7, 4) NOT NULL,

    CONSTRAINT pk_syirkah_close_snapshot_party
        PRIMARY KEY (calculation_id, party_id),

    CONSTRAINT fk_syirkah_snapshot_party_snapshot
        FOREIGN KEY (calculation_id)
        REFERENCES syirkah_close_snapshot(calculation_id),

    CONSTRAINT ck_syirkah_snapshot_party_capital
        CHECK (capital_contribution >= 0),

    CONSTRAINT ck_syirkah_snapshot_party_ratio
        CHECK (
            profit_sharing_ratio >= 0
            AND profit_sharing_ratio <= 100
        )
);
```

This is crucial.

Do **not** resolve the current `SyirkahContract` when somebody later opens the historical close.

The historical record already says:

```text
contract_id = CONTRACT-001
contract_version = 3
party A = 40%
party B = 60%
```

That is the accounting fact.

---

# 14. Snapshot allocations

```sql id="a4d2u8"
CREATE TABLE syirkah_close_snapshot_allocation (
    calculation_id        VARCHAR(100) NOT NULL,
    allocation_type       VARCHAR(30) NOT NULL,
    party_id              VARCHAR(100) NOT NULL,

    amount                DECIMAL(19, 4) NOT NULL,
    allocation_basis      VARCHAR(100) NOT NULL,

    CONSTRAINT pk_syirkah_close_snapshot_allocation
        PRIMARY KEY (
            calculation_id,
            allocation_type,
            party_id
        ),

    CONSTRAINT fk_syirkah_snapshot_allocation_snapshot
        FOREIGN KEY (calculation_id)
        REFERENCES syirkah_close_snapshot(calculation_id),

    CONSTRAINT ck_syirkah_snapshot_allocation_amount
        CHECK (amount >= 0)
);
```

`allocation_type`:

```text
PROFIT_SHARING
LOSS_ALLOCATION
```

This avoids abusing a positive number to represent two completely different business meanings.

---

# 15. Repository implementation

The exact persistence framework isn't established by the framework source, so keep the implementation behind the SPI.

Conceptually:

```java id="m8g2s1"
public final class SqlSyirkahCloseSnapshotRepository
        implements SyirkahCloseSnapshotRepository {

    private final SnapshotDatabase database;

    public SqlSyirkahCloseSnapshotRepository(
            SnapshotDatabase database) {

        this.database = Objects.requireNonNull(database);
    }

    @Override
    public Uni<Optional<SyirkahCloseSnapshot>> findByCalculationId(
            String tenantId,
            String ledgerId,
            String calculationId) {

        return database.findSnapshot(
                tenantId,
                ledgerId,
                calculationId);
    }

    @Override
    public Uni<Optional<SyirkahCloseSnapshot>> findByCloseCycleId(
            String tenantId,
            String ledgerId,
            String closeCycleId) {

        return database.findSnapshotByCloseCycle(
                tenantId,
                ledgerId,
                closeCycleId);
    }

    @Override
    public Uni<Void> save(
            SyirkahCloseSnapshot snapshot) {

        return database.insertSnapshot(snapshot);
    }
}
```

The important thing is that `save()` should be **insert-only**.

There should not be:

```java
update(snapshot);
```

for this aggregate.

---

# 16. Make duplicate insertion an explicit domain/application outcome

Define:

```java id="9v3kzq"
package tech.kayys.syirkah.accounting.application.port;

public class SyirkahCloseSnapshotAlreadyExistsException
        extends RuntimeException {

    public SyirkahCloseSnapshotAlreadyExistsException(
            String message) {
        super(message);
    }
}
```

Then the application service handles the database unique-key conflict:

```java id="5w8f3c"
private Uni<SyirkahCloseSnapshot> persistIdempotently(
        SyirkahCloseSnapshot snapshot) {

    return snapshotRepository
            .save(snapshot)
            .replaceWith(snapshot)
            .onFailure(this::isDuplicate)
            .recoverWithUni(() ->
                    snapshotRepository.findByCalculationId(
                            snapshot.tenantId(),
                            snapshot.ledgerId(),
                            snapshot.calculationId().value())
                            .flatMap(existing -> existing
                                    .map(Uni.createFrom()::item)
                                    .orElseGet(() ->
                                            Uni.createFrom().failure(
                                                    new IllegalStateException(
                                                            "Snapshot insertion conflict without existing snapshot")))));
}

private boolean isDuplicate(Throwable error) {
    return error instanceof SyirkahCloseSnapshotAlreadyExistsException
            || isUniqueConstraintViolation(error);
}
```

The exact SQL exception type belongs in the adapter, not the domain.

---

# 17. Add a snapshot hash

For financial close, I would add one more field:

```sql id="4m3qj9"
ALTER TABLE syirkah_close_snapshot
ADD COLUMN snapshot_hash VARCHAR(128) NOT NULL;
```

The hash represents the canonical serialized contents of the snapshot.

For example:

```text
SHA-256(
    tenant
    ledger
    closeCycle
    period
    contract
    contractVersion
    financialResult
    parties
    allocations
)
```

Domain model:

```java id="7f2v1n"
private final String snapshotHash;

public String snapshotHash() {
    return snapshotHash;
}
```

Factory:

```java id="f0j2ce"
String snapshotHash =
        SyirkahSnapshotHasher.sha256(
                tenantId,
                ledgerId,
                closeCycleId,
                fiscalPeriodId,
                periodStart,
                periodEnd,
                contractId,
                contractVersion,
                revenue,
                directCosts,
                operatingCosts,
                otherCosts,
                netResult,
                resultType,
                parties,
                allocations,
                lossAllocations);
```

This gives us an additional audit invariant:

```text
stored data
     ↓
recalculate canonical representation
     ↓
SHA-256
     ↓
must equal snapshot_hash
```

If it does not, the persisted snapshot has been altered.

---

# 18. Do not hash unordered collections

The hasher must canonicalize ordering.

For example, parties:

```java id="x3y5nz"
parties.stream()
        .sorted(Comparator.comparing(
                SyirkahCloseSnapshot.PartySnapshot::partyId))
```

Allocations:

```java id="7v0w1s"
allocations.stream()
        .sorted(
                Comparator.comparing(
                        SyirkahCloseSnapshot.AllocationSnapshot::partyId)
                .thenComparing(
                        SyirkahCloseSnapshot.AllocationSnapshot::basis))
```

Otherwise this:

```text
A, B
```

and:

```text
B, A
```

could produce different hashes even though they represent the same accounting snapshot.

---

# 19. Add a verification service

```java id="d6q1xt"
public final class SyirkahCloseSnapshotIntegrityService {

    private final SyirkahSnapshotHasher hasher;

    public SyirkahCloseSnapshotIntegrityService(
            SyirkahSnapshotHasher hasher) {

        this.hasher = Objects.requireNonNull(hasher);
    }

    public void verify(SyirkahCloseSnapshot snapshot) {

        String expected = hasher.hash(snapshot);

        if (!expected.equals(snapshot.snapshotHash())) {
            throw new IllegalStateException(
                    "Syirkah close snapshot integrity check failed: "
                            + snapshot.calculationId().value());
        }
    }
}
```

This will later be useful for:

- audit
- historical reports
- partner statements
- regulatory reporting
- dispute resolution
- external integrations

---

# 20. Critical test: changed contract cannot alter close

This is the test I want in the accounting module before moving forward:

```java id="w1n8fz"
@Test
void distributionMustUseFrozenSnapshot() {

    SyirkahCloseSnapshot snapshot =
            validSnapshot(
                    3,
                    List.of(
                            allocation(
                                    "PARTY-A",
                                    "2000000",
                                    "PROFIT_SHARING"),
                            allocation(
                                    "PARTY-B",
                                    "3000000",
                                    "PROFIT_SHARING")));

    when(snapshotRepository.findByCalculationId(
            "TENANT-1",
            "LEDGER-1",
            "CALC-1"))
            .thenReturn(Uni.createFrom()
                    .item(Optional.of(snapshot)));

    // Current contract has since changed to 50/50.
    when(contractRepository.findEffectiveContract(
            "TENANT-1",
            "LEDGER-1",
            LocalDate.of(2026, 9, 30)))
            .thenReturn(Uni.createFrom()
                    .item(Optional.of(contractWith50_50Ratio())));

    List<PartnerDistributionObligation> obligations =
            service.prepare(
                    "TENANT-1",
                    "LEDGER-1",
                    "CALC-1")
            .await().indefinitely();

    assertEquals(
            new BigDecimal("2000000"),
            amountFor(obligations, "PARTY-A"));

    assertEquals(
            new BigDecimal("3000000"),
            amountFor(obligations, "PARTY-B"));
}
```

Ideally, `contractRepository` should not even be called during distribution preparation.

That makes the architectural rule testable:

```text
distribution preparation
        |
        X current contract
        |
        ✓ frozen close snapshot
```

---

# 21. Another important invariant: one close → one snapshot

```java id="p8s5y0"
@Test
void closeCycleMustHaveOnlyOneSnapshot() {

    SyirkahCloseSnapshot first =
            validSnapshotFor(
                    "CLOSE-001",
                    "CALC-001");

    repository.save(first)
            .await().indefinitely();

    SyirkahCloseSnapshot second =
            validSnapshotFor(
                    "CLOSE-001",
                    "CALC-002");

    assertThrows(
            SyirkahCloseSnapshotAlreadyExistsException.class,
            () -> repository.save(second)
                    .await().indefinitely());
}
```

This protects against a particularly dangerous situation:

```text
CLOSE-001
   |
   +-- CALC-001 -> 40/60
   |
   +-- CALC-002 -> 50/50
```

For a single close cycle, we don't want ambiguity.

If recalculation is legitimately required, the close cycle should explicitly be reset/reopened or a new calculation workflow should be created according to the eventual business policy—not silently overwrite the accounting snapshot.

---

# 22. One refinement to the earlier design

I would now change the relationship slightly:

```text
CloseCycle
   |
   +---- SyirkahCloseSnapshot
             |
             +---- SnapshotParty
             |
             +---- SnapshotAllocation
             |
             +---- DistributionObligation
```

rather than:

```text
CloseCycle
   |
   +---- mutable CloseCalculation
```

`CloseCalculation` can remain as an **application calculation DTO**, but it should not become the historical accounting record.

So:

```text
SyirkahCloseProcessor
        ↓
CloseCalculation
        ↓
SnapshotFactory
        ↓
SyirkahCloseSnapshot
        ↓
database
```

Once persisted, the snapshot is authoritative.

---

## Where we are now

The Syirkah close architecture is becoming:

```text
                 GENERAL LEDGER
                       |
                       v
             Period financial result
                       |
                       v
              Syirkah contract
              effective version
                       |
                       v
             +-------------------+
             | CLOSE SNAPSHOT    |
             |-------------------|
             | Financial result  |
             | Contract version  |
             | Party agreements  |
             | Profit allocation |
             | Loss allocation   |
             | Integrity hash    |
             +-------------------+
                       |
             +---------+---------+
             |                   |
             v                   v
       Partner obligations   Historical report
             |
             v
        Maker / checker
             |
             v
      Distribution journal
             |
             v
            POSTED
             |
             v
      Close distribution task
             |
             v
        Accounting lock
```

This gives `tutup buku` a proper accounting boundary rather than treating it as simply a `CloseCycle.lock()` operation.

**Next tranche:** I would implement the **Syirkah loss semantics per contract type**. The current capital-proportion policy is acceptable as a first policy, but `MUDHARABAH` cannot safely share the exact same loss algorithm as every `MUSYARAKAH`/`INAN` arrangement. We should introduce a contract-aware loss policy resolver before the close engine becomes production-critical.

/////////////

The attached **Hukum-muamalah** source supports the distinction we need: ordinary Mudharabah business loss is borne by the capital provider, while the manager bears the loss of their effort/time; if the manager causes the loss through negligence, intentional misconduct, or violating agreed conditions, responsibility changes. Hukum-muamalah

So I would **not** encode all Syirkah losses as capital-proportion losses. The next extension should introduce an explicit loss-policy resolver.

### 1. Add loss classification

```java
package tech.kayys.syirkah.accounting.domain.islamic.syirkah;

public enum LossCause {
    NORMAL_BUSINESS_LOSS,
    MANAGER_NEGLIGENCE,
    CONTRACT_BREACH,
    MISAPPROPRIATION
}
```

This is important because the financial result alone cannot tell us whether a loss was an ordinary business loss or caused by a responsible party.

---

### 2. Add a loss allocation context

```java
package tech.kayys.syirkah.accounting.domain.islamic.syirkah;

import java.util.Objects;
import java.util.Optional;

public record LossAllocationContext(
        LossCause cause,
        String responsiblePartyId
) {

    public LossAllocationContext {
        Objects.requireNonNull(cause, "cause cannot be null");

        if (responsiblePartyId != null && responsiblePartyId.isBlank()) {
            throw new IllegalArgumentException(
                    "responsiblePartyId cannot be blank");
        }

        if (requiresResponsibleParty() && responsiblePartyId == null) {
            throw new IllegalArgumentException(
                    "responsiblePartyId is required for loss cause: " + cause);
        }
    }

    public static LossAllocationContext normalBusinessLoss() {
        return new LossAllocationContext(
                LossCause.NORMAL_BUSINESS_LOSS,
                null
        );
    }

    public static LossAllocationContext causedBy(String partyId) {
        return new LossAllocationContext(
                LossCause.MANAGER_NEGLIGENCE,
                partyId
        );
    }

    public boolean requiresResponsibleParty() {
        return cause == LossCause.MANAGER_NEGLIGENCE
                || cause == LossCause.CONTRACT_BREACH
                || cause == LossCause.MISAPPROPRIATION;
    }

    public Optional<String> responsibleParty() {
        return Optional.ofNullable(responsiblePartyId);
    }
}
```

This keeps responsibility explicit instead of hiding it inside an allocation algorithm.

---

### 3. Change the policy interface slightly

The current:

```java
List<ProfitSharingResult.PartyAllocation> allocate(
    SyirkahContract.Version contractVersion,
    BigDecimal loss
);
```

should become:

```java
package tech.kayys.syirkah.accounting.domain.islamic.syirkah;

import java.math.BigDecimal;
import java.util.List;

public interface LossAllocationPolicy {

    List<ProfitSharingResult.PartyAllocation> allocate(
            SyirkahContract.Version contractVersion,
            BigDecimal loss,
            LossAllocationContext context
    );
}
```

Then the existing capital-proportion implementation becomes one policy rather than the universal rule.

---

## 4. Preserve the existing capital-proportion policy

```java
package tech.kayys.syirkah.accounting.domain.islamic.syirkah;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class CapitalProportionLossAllocationPolicy
        implements LossAllocationPolicy {

    private static final int SCALE = 2;

    @Override
    public List<ProfitSharingResult.PartyAllocation> allocate(
            SyirkahContract.Version contractVersion,
            BigDecimal loss,
            LossAllocationContext context) {

        if (loss == null || loss.signum() < 0) {
            throw new IllegalArgumentException("Loss must be non-negative");
        }

        if (context == null) {
            throw new IllegalArgumentException("Loss context is required");
        }

        if (context.cause() != LossCause.NORMAL_BUSINESS_LOSS) {
            throw new IllegalArgumentException(
                    "Capital proportion policy only supports normal business losses");
        }

        BigDecimal totalCapital = contractVersion.parties().stream()
                .map(SyirkahPartyAgreement::capitalContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalCapital.signum() <= 0) {
            throw new IllegalStateException(
                    "Capital proportion loss allocation requires positive total capital");
        }

        List<ProfitSharingResult.PartyAllocation> allocations =
                new ArrayList<>();

        BigDecimal allocated = BigDecimal.ZERO;

        for (int i = 0; i < contractVersion.parties().size(); i++) {
            SyirkahPartyAgreement agreement =
                    contractVersion.parties().get(i);

            BigDecimal amount;

            if (i == contractVersion.parties().size() - 1) {
                amount = loss.subtract(allocated)
                        .setScale(SCALE, RoundingMode.HALF_UP);
            } else {
                amount = loss
                        .multiply(agreement.capitalContribution())
                        .divide(totalCapital, SCALE, RoundingMode.HALF_UP);
            }

            allocated = allocated.add(amount);

            allocations.add(
                    new ProfitSharingResult.PartyAllocation(
                            agreement.party().partyId(),
                            amount
                    )
            );
        }

        return List.copyOf(allocations);
    }
}
```

---

## 5. Add the Mudharabah policy

For ordinary business loss, this policy allocates monetary loss to capital-contributing parties. A working-only Mudharib receives zero monetary loss allocation.

```java
package tech.kayys.syirkah.accounting.domain.islamic.syirkah;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class MudharabahLossAllocationPolicy
        implements LossAllocationPolicy {

    private static final int SCALE = 2;

    @Override
    public List<ProfitSharingResult.PartyAllocation> allocate(
            SyirkahContract.Version contractVersion,
            BigDecimal loss,
            LossAllocationContext context) {

        if (loss == null || loss.signum() < 0) {
            throw new IllegalArgumentException("Loss must be non-negative");
        }

        if (context == null) {
            throw new IllegalArgumentException("Loss context is required");
        }

        return switch (context.cause()) {
            case NORMAL_BUSINESS_LOSS ->
                    allocateNormalBusinessLoss(contractVersion, loss);

            case MANAGER_NEGLIGENCE,
                 CONTRACT_BREACH,
                 MISAPPROPRIATION ->
                    allocateResponsiblePartyLoss(
                            contractVersion,
                            loss,
                            context.responsiblePartyId()
                    );
        };
    }

    private List<ProfitSharingResult.PartyAllocation> allocateNormalBusinessLoss(
            SyirkahContract.Version contractVersion,
            BigDecimal loss) {

        BigDecimal totalCapital = contractVersion.parties().stream()
                .map(SyirkahPartyAgreement::capitalContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalCapital.signum() <= 0) {
            throw new IllegalStateException(
                    "Mudharabah loss allocation requires capital providers");
        }

        List<ProfitSharingResult.PartyAllocation> result =
                new ArrayList<>();

        BigDecimal allocated = BigDecimal.ZERO;

        for (int i = 0; i < contractVersion.parties().size(); i++) {
            SyirkahPartyAgreement agreement =
                    contractVersion.parties().get(i);

            BigDecimal capital = agreement.capitalContribution();

            BigDecimal amount;

            if (capital.signum() == 0) {
                amount = BigDecimal.ZERO.setScale(SCALE);
            } else if (i == contractVersion.parties().size() - 1) {
                amount = loss.subtract(allocated)
                        .setScale(SCALE, RoundingMode.HALF_UP);
            } else {
                amount = loss
                        .multiply(capital)
                        .divide(totalCapital, SCALE, RoundingMode.HALF_UP);
            }

            allocated = allocated.add(amount);

            result.add(
                    new ProfitSharingResult.PartyAllocation(
                            agreement.party().partyId(),
                            amount
                    )
            );
        }

        return List.copyOf(result);
    }

    private List<ProfitSharingResult.PartyAllocation> allocateResponsiblePartyLoss(
            SyirkahContract.Version contractVersion,
            BigDecimal loss,
            String responsiblePartyId) {

        boolean exists = contractVersion.parties().stream()
                .anyMatch(p ->
                        p.party().partyId().equals(responsiblePartyId));

        if (!exists) {
            throw new IllegalArgumentException(
                    "Responsible party is not part of the contract: "
                            + responsiblePartyId);
        }

        return contractVersion.parties().stream()
                .map(p -> new ProfitSharingResult.PartyAllocation(
                        p.party().partyId(),
                        p.party().partyId().equals(responsiblePartyId)
                                ? loss
                                : BigDecimal.ZERO
                ))
                .toList();
    }
}
```

There is one subtlety here: **"loss allocation" and "accounting liability/compensation" should eventually be separate concepts**. We should not automatically turn a responsibility allocation into a receivable or journal entry. That belongs to the next accounting-policy layer.

---

# 6. Introduce the resolver

This is the key extension.

```java
package tech.kayys.syirkah.accounting.domain.islamic.syirkah;

public interface LossAllocationPolicyResolver {

    LossAllocationPolicy resolve(
            SyirkahContract.Version contractVersion,
            LossAllocationContext context
    );
}
```

Default implementation:

```java
package tech.kayys.syirkah.accounting.domain.islamic.syirkah;

import java.util.Objects;

public final class DefaultLossAllocationPolicyResolver
        implements LossAllocationPolicyResolver {

    private final LossAllocationPolicy capitalProportionPolicy;
    private final LossAllocationPolicy mudharabahPolicy;

    public DefaultLossAllocationPolicyResolver(
            LossAllocationPolicy capitalProportionPolicy,
            LossAllocationPolicy mudharabahPolicy) {

        this.capitalProportionPolicy =
                Objects.requireNonNull(
                        capitalProportionPolicy,
                        "capitalProportionPolicy cannot be null");

        this.mudharabahPolicy =
                Objects.requireNonNull(
                        mudharabahPolicy,
                        "mudharabahPolicy cannot be null");
    }

    @Override
    public LossAllocationPolicy resolve(
            SyirkahContract.Version contractVersion,
            LossAllocationContext context) {

        Objects.requireNonNull(contractVersion, "contractVersion cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        return switch (contractVersion.type()) {
            case MUDHARABAH -> mudharabahPolicy;

            case MUSYARAKAH,
                 INAN -> capitalProportionPolicy;

            case ABDAN,
                 WUJUH ->
                    throw new IllegalStateException(
                            "No default loss policy is configured for "
                                    + contractVersion.type());
        };
    }
}
```

**Why deliberately fail for `ABدان` / `WUJUH` instead of guessing?**

Because the source itself distinguishes forms of Wujuh, including one form that is effectively treated as Mudharabah. Hukum-muamalah

Therefore a generic `WUJUH -> capital proportion` rule would be dangerous.

---

# 7. Modify `SyirkahResultCalculator`

Current concept:

```java
public final class SyirkahResultCalculator {

    private final SyirkahProfitSharingEngine profitSharingEngine;
    private final LossAllocationPolicy lossAllocationPolicy;

    ...
}
```

Change it to:

```java
public final class SyirkahResultCalculator {

    private final SyirkahProfitSharingEngine profitSharingEngine;
    private final LossAllocationPolicyResolver lossAllocationPolicyResolver;

    public SyirkahResultCalculator(
            SyirkahProfitSharingEngine profitSharingEngine,
            LossAllocationPolicyResolver lossAllocationPolicyResolver) {

        this.profitSharingEngine =
                Objects.requireNonNull(
                        profitSharingEngine,
                        "profitSharingEngine cannot be null");

        this.lossAllocationPolicyResolver =
                Objects.requireNonNull(
                        lossAllocationPolicyResolver,
                        "lossAllocationPolicyResolver cannot be null");
    }

    public SyirkahCalculation calculate(
            SyirkahContract contract,
            LocalDate effectiveDate,
            FinancialResult financialResult,
            LossAllocationContext lossContext) {

        Objects.requireNonNull(contract, "contract cannot be null");
        Objects.requireNonNull(effectiveDate, "effectiveDate cannot be null");
        Objects.requireNonNull(financialResult, "financialResult cannot be null");
        Objects.requireNonNull(lossContext, "lossContext cannot be null");

        SyirkahContract.Version version =
                contract.effectiveVersion(effectiveDate);

        ProfitSharingResult result =
                profitSharingEngine.calculate(
                        contract,
                        effectiveDate,
                        financialResult
                );

        if (result.resultType() != ProfitSharingResult.ResultType.LOSS) {
            return new SyirkahCalculation(
                    result,
                    List.of()
            );
        }

        LossAllocationPolicy policy =
                lossAllocationPolicyResolver.resolve(
                        version,
                        lossContext
                );

        List<ProfitSharingResult.PartyAllocation> lossAllocations =
                policy.allocate(
                        version,
                        result.netResult().abs(),
                        lossContext
                );

        return new SyirkahCalculation(
                result,
                lossAllocations
        );
    }
}
```

---

# 8. Do not silently default responsibility during close

For ordinary `tutup buku`, the close process should use:

```java
LossAllocationContext.normalBusinessLoss()
```

That means:

```text
Normal period loss
        |
        +--> Mudharabah
        |      |
        |      +--> capital providers absorb monetary capital loss
        |      +--> working-only manager gets 0
        |
        +--> Musyarakah / Inan
               |
               +--> capital-proportion allocation
```

If there is evidence of negligence or breach:

```text
Loss
 |
 +--> MANAGER_NEGLIGENCE
 |
 +--> CONTRACT_BREACH
 |
 +--> MISAPPROPRIATION
```

the close calculation must receive an explicit responsible party.

This prevents the system from making an **Islamic-law judgment merely because an accounting loss exists**.

---

# 9. One important change to the snapshot

The immutable close snapshot we designed earlier should also preserve the loss basis.

Add:

```java
private final LossCause lossCause;
private final String lossResponsiblePartyId;
```

and expose:

```java
public LossCause getLossCause() {
    return lossCause;
}

public Optional<String> getLossResponsiblePartyId() {
    return Optional.ofNullable(lossResponsiblePartyId);
}
```

The snapshot then records not just:

```text
Net loss = 20,000,000
```

but:

```text
Net loss                  20,000,000
Loss cause                NORMAL_BUSINESS_LOSS
Contract                  MUDHARABAH v3
Capital allocation        ...
```

That is much stronger for auditability.

---

## 10. Tests we should add now

### Mudharabah normal loss

```java
@Test
void normalMudharabahLossShouldBeAllocatedToCapitalProviders() {
    // capital provider = 100,000
    // working manager = 0
    // loss = 20,000

    assertEquals(
            new BigDecimal("20000.00"),
            allocationFor("capital-provider")
    );

    assertEquals(
            new BigDecimal("0.00"),
            allocationFor("manager")
    );
}
```

### Mudharabah manager negligence

```java
@Test
void managerNegligenceShouldAllocateLossToResponsibleManager() {
    // loss = 20,000
    // responsible manager = manager-1

    assertEquals(
            new BigDecimal("20000.00"),
            allocationFor("manager-1")
    );

    assertEquals(
            new BigDecimal("0.00"),
            allocationFor("capital-provider")
    );
}
```

### Musyarakah

```java
@Test
void musyarakahNormalLossShouldFollowCapitalProportion() {
    // partner A = 40,000
    // partner B = 60,000
    // loss = 20,000

    assertEquals(
            new BigDecimal("8000.00"),
            allocationFor("partner-a")
    );

    assertEquals(
            new BigDecimal("12000.00"),
            allocationFor("partner-b")
    );
}
```

### Unsupported Wujuh

```java
@Test
void wujuhWithoutExplicitPolicyShouldFailClosed() {
    assertThrows(
            IllegalStateException.class,
            () -> resolver.resolve(
                    wujuhVersion,
                    LossAllocationContext.normalBusinessLoss()
            )
    );
}
```

That last test is intentional: **when Sharia semantics are ambiguous, the accounting engine should stop rather than invent a rule.**

---

## Resulting architecture

The Syirkah close path is now becoming:

```text
                         Financial Result
                                |
                                v
                    +-----------------------+
                    | SyirkahResultCalculator|
                    +-----------+-----------+
                                |
                    +-----------+-----------+
                    |                       |
                  PROFIT                   LOSS
                    |                       |
                    v                       v
             Nisbah allocation       Loss context
                                            |
                                            v
                                  Policy Resolver
                                            |
                    +-----------------------+------------------+
                    |                       |                  |
               Mudharabah             Musyarakah/Inan     Explicit policy
                    |                       |                  |
                    v                       v                  v
             Mudharabah policy       Capital policy       Configured
                    |                       |              policy
                    +-----------------------+------------------+
                                            |
                                            v
                                  Immutable Close Snapshot
                                            |
                                            v
                                  Distribution Obligations
                                            |
                                            v
                                      Maker / Checker
                                            |
                                            v
                                      General Ledger
                                            |
                                            v
                                   Complete Close Task
```

This is a better foundation than simply adding more `if (type == MUDHARABAH)` branches.

The source also explicitly defines the business result as **net profit** after revenue, direct costs, operating costs, and other costs, which matches the `FinancialResult` structure already introduced. Hukum-muamalah

**Next tranche should be the accounting treatment of a loss/responsibility event**: distinguish ordinary capital loss from a recoverable amount caused by negligence/breach, so the close engine does not incorrectly post a partner's responsibility as ordinary profit-sharing or expense.

//////////////

The next extension should be the **responsibility/recovery layer**.

The important boundary is:

> **A Sharia loss allocation is not automatically an accounting receivable, expense, or journal entry.**

We should model the underlying event first, then let accounting policy decide its treatment.

### 1. Add a responsibility event

```java
package tech.kayys.syirkah.accounting.domain.islamic.syirkah;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public final class SyirkahLossResponsibility {

    private final String responsibilityId;
    private final String partyId;
    private final LossCause cause;
    private final BigDecimal amount;
    private final LocalDate determinationDate;
    private final String determinedBy;
    private final String evidenceReference;

    public SyirkahLossResponsibility(
            String responsibilityId,
            String partyId,
            LossCause cause,
            BigDecimal amount,
            LocalDate determinationDate,
            String determinedBy,
            String evidenceReference) {

        this.responsibilityId = requireText(
                responsibilityId, "responsibilityId");

        this.partyId = requireText(
                partyId, "partyId");

        this.cause = Objects.requireNonNull(
                cause, "cause cannot be null");

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "amount must be positive");
        }

        this.amount = amount;

        this.determinationDate = Objects.requireNonNull(
                determinationDate,
                "determinationDate cannot be null");

        this.determinedBy = requireText(
                determinedBy, "determinedBy");

        this.evidenceReference = requireText(
                evidenceReference, "evidenceReference");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank");
        }
        return value;
    }

    public String responsibilityId() {
        return responsibilityId;
    }

    public String partyId() {
        return partyId;
    }

    public LossCause cause() {
        return cause;
    }

    public BigDecimal amount() {
        return amount;
    }

    public LocalDate determinationDate() {
        return determinationDate;
    }

    public String determinedBy() {
        return determinedBy;
    }

    public String evidenceReference() {
        return evidenceReference;
    }
}
```

The key idea is that this object records a **determination**, not merely an accounting calculation.

---

## 2. Add a recovery obligation

Do not reuse `PartnerDistributionObligation`.

They have different meanings:

```text
PartnerDistributionObligation
    -> partner is entitled to distribution

LossRecoveryObligation
    -> partner is responsible for restoring an amount
```

```java
package tech.kayys.syirkah.accounting.domain.islamic.syirkah;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public final class LossRecoveryObligation {

    public enum Status {
        DETERMINED,
        APPROVED,
        POSTED,
        PARTIALLY_SETTLED,
        SETTLED,
        WAIVED,
        CANCELLED
    }

    private final String obligationId;
    private final String responsibilityId;
    private final String calculationId;
    private final String partyId;
    private final BigDecimal amount;

    private Status status;
    private String approvedBy;
    private Instant approvedAt;
    private String postedJournalEntryId;
    private Instant postedAt;

    public LossRecoveryObligation(
            String obligationId,
            String responsibilityId,
            String calculationId,
            String partyId,
            BigDecimal amount) {

        this.obligationId = requireText(
                obligationId, "obligationId");

        this.responsibilityId = requireText(
                responsibilityId, "responsibilityId");

        this.calculationId = requireText(
                calculationId, "calculationId");

        this.partyId = requireText(
                partyId, "partyId");

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "amount must be positive");
        }

        this.amount = amount;
        this.status = Status.DETERMINED;
    }

    public void approve(String approver) {
        requireStatus(Status.DETERMINED);

        if (approver == null || approver.isBlank()) {
            throw new IllegalArgumentException(
                    "approver cannot be blank");
        }

        this.approvedBy = approver;
        this.approvedAt = Instant.now();
        this.status = Status.APPROVED;
    }

    public void markPosted(
            String journalEntryId,
            String postedBy) {

        requireStatus(Status.APPROVED);

        if (journalEntryId == null || journalEntryId.isBlank()) {
            throw new IllegalArgumentException(
                    "journalEntryId cannot be blank");
        }

        if (postedBy == null || postedBy.isBlank()) {
            throw new IllegalArgumentException(
                    "postedBy cannot be blank");
        }

        this.postedJournalEntryId = journalEntryId;
        this.postedAt = Instant.now();
        this.status = Status.POSTED;
    }

    public void markSettled() {
        if (status != Status.POSTED
                && status != Status.PARTIALLY_SETTLED) {
            throw new IllegalStateException(
                    "Only posted recovery obligations can be settled");
        }

        this.status = Status.SETTLED;
    }

    private void requireStatus(Status expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Expected status " + expected
                            + " but was " + status);
        }
    }

    private static String requireText(
            String value,
            String field) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank");
        }

        return value;
    }

    public String obligationId() {
        return obligationId;
    }

    public String responsibilityId() {
        return responsibilityId;
    }

    public String calculationId() {
        return calculationId;
    }

    public String partyId() {
        return partyId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public Status status() {
        return status;
    }

    public String postedJournalEntryId() {
        return postedJournalEntryId;
    }
}
```

---

# 3. Separate calculation from responsibility determination

The close calculator should **not** decide that somebody was negligent.

Instead:

```text
Accounting result
      |
      v
Loss detected
      |
      +----------------------+
      |                      |
normal business       suspected responsibility
      |                      |
      v                      v
loss policy            responsibility workflow
      |                      |
      v                      v
loss allocation        determination
                             |
                             v
                       recovery obligation
```

This is particularly important because the source material explicitly distinguishes ordinary Mudharabah loss from loss caused by negligence or violation of the capital provider's conditions. Hukum-muamalah

---

# 4. Add a determination port

```java
package tech.kayys.syirkah.accounting.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.islamic.syirkah.SyirkahLossResponsibility;

import java.util.Optional;

public interface SyirkahLossResponsibilityRepository {

    Uni<Optional<SyirkahLossResponsibility>> findById(
            String tenantId,
            String ledgerId,
            String responsibilityId);

    Uni<Optional<SyirkahLossResponsibility>> findByCalculationId(
            String tenantId,
            String ledgerId,
            String calculationId);

    Uni<Void> save(
            String tenantId,
            String ledgerId,
            SyirkahLossResponsibility responsibility);
}
```

Notice that it is tenant + ledger scoped, consistent with the existing accounting architecture.

---

# 5. Add an explicit responsibility service

```java
package tech.kayys.syirkah.accounting.application.syirkah;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.port.SyirkahLossResponsibilityRepository;
import tech.kayys.syirkah.accounting.domain.islamic.syirkah.LossCause;
import tech.kayys.syirkah.accounting.domain.islamic.syirkah.SyirkahLossResponsibility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public final class SyirkahLossResponsibilityService {

    private final SyirkahLossResponsibilityRepository repository;

    public SyirkahLossResponsibilityService(
            SyirkahLossResponsibilityRepository repository) {

        this.repository = Objects.requireNonNull(
                repository,
                "repository cannot be null");
    }

    public Uni<SyirkahLossResponsibility> determine(
            String tenantId,
            String ledgerId,
            String responsibilityId,
            String calculationId,
            String partyId,
            LossCause cause,
            BigDecimal amount,
            LocalDate determinationDate,
            String determinedBy,
            String evidenceReference) {

        SyirkahLossResponsibility responsibility =
                new SyirkahLossResponsibility(
                        responsibilityId,
                        partyId,
                        cause,
                        amount,
                        determinationDate,
                        determinedBy,
                        evidenceReference
                );

        return repository.save(
                        tenantId,
                        ledgerId,
                        responsibility)
                .replaceWith(responsibility);
    }
}
```

This should later be protected by a proper approval workflow rather than allowing arbitrary direct creation from a REST endpoint.

---

# 6. Accounting policy decides the journal

This is the critical piece.

```java
package tech.kayys.syirkah.accounting.domain.islamic.syirkah;

import java.math.BigDecimal;
import java.util.List;

public interface SyirkahLossRecoveryAccountingPolicy {

    JournalMapping map(
            SyirkahLossResponsibility responsibility);

    record JournalMapping(
            List<JournalLineMapping> lines,
            String description) {

        public JournalMapping {
            if (lines == null || lines.isEmpty()) {
                throw new IllegalArgumentException(
                        "Journal mapping must contain lines");
            }
        }
    }

    record JournalLineMapping(
            String accountId,
            LineType type,
            BigDecimal amount,
            String currencyCode,
            String description) {
    }

    enum LineType {
        DEBIT,
        CREDIT
    }
}
```

This deliberately avoids assuming:

```text
Dr Expense
Cr Partner Payable
```

or:

```text
Dr Partner Receivable
Cr Asset
```

because the correct accounting treatment depends on the organization's accounting policy and the underlying event.

---

# 7. Do not put recovery into the profit-sharing calculation

This is a major invariant.

Suppose:

```text
Revenue             100
Operating costs      80
Normal net profit    20

Manager negligence   10
```

We should **not** blindly transform that into:

```text
Net profit = 30
```

The responsibility event happened after/differently from the operating result.

Instead preserve:

```text
Operating result     20
Responsibility       10
```

Then accounting policy determines whether that responsibility becomes a recovery asset, receivable, compensation, adjustment, etc.

This keeps the Syirkah calculation mathematically and legally auditable.

---

# 8. Extend the close snapshot

The snapshot should therefore have three distinct sections:

```text
Financial result
    revenue
    direct costs
    operating costs
    other costs
    net result

Syirkah allocation
    profit allocations
    normal loss allocations

Responsibility
    responsibility determinations
    recovery obligations
```

For example:

```java
public record ResponsibilitySnapshot(
        String responsibilityId,
        String partyId,
        String cause,
        BigDecimal amount,
        String evidenceReference
) {
}
```

and:

```java
private final List<ResponsibilitySnapshot> responsibilities;
```

This means a historical close remains reproducible even after:

- the contract changes,
- the partner changes role,
- the accounting policy changes,
- the current Syirkah configuration changes.

---

# 9. Close lifecycle becomes clearer

I recommend extending the current lifecycle to:

```text
CALCULATE RESULT
       |
       v
ALLOCATE RESULT
       |
       +---- PROFIT
       |       |
       |       v
       |   DISTRIBUTION
       |
       +---- NORMAL LOSS
       |       |
       |       v
       |   LOSS ALLOCATION
       |
       +---- RESPONSIBILITY CASE
               |
               v
       RESPONSIBILITY REVIEW
               |
               v
       RECOVERY DETERMINATION
               |
               v
       RECOVERY ACCOUNTING
```

But **do not immediately add four more mandatory `CloseTaskKind` values**.

That would make ordinary closes unnecessarily complex.

Instead, initially keep:

```text
SYIRKAH_RESULT
SYIRKAH_ALLOCATION
SYIRKAH_DISTRIBUTION
```

and make responsibility/recovery a conditional workflow when required.

Later, if the business requires formal close approval around disputed losses, we can add a dedicated task such as:

```java
SYIRKAH_RESPONSIBILITY
```

without forcing it onto every Syirkah close.

---

# 10. Database model

Add two tables rather than mixing recovery into distribution:

```sql
CREATE TABLE syirkah_loss_responsibility (
    responsibility_id     VARCHAR(100) NOT NULL,
    tenant_id             VARCHAR(100) NOT NULL,
    ledger_id             VARCHAR(100) NOT NULL,
    calculation_id        VARCHAR(100) NOT NULL,
    party_id              VARCHAR(100) NOT NULL,
    cause                 VARCHAR(50) NOT NULL,
    amount                DECIMAL(19, 4) NOT NULL,
    determination_date    DATE NOT NULL,
    determined_by         VARCHAR(100) NOT NULL,
    evidence_reference    VARCHAR(200) NOT NULL,
    created_at             TIMESTAMP NOT NULL,

    CONSTRAINT pk_syirkah_loss_responsibility
        PRIMARY KEY (responsibility_id),

    CONSTRAINT uq_syirkah_loss_responsibility
        UNIQUE (
            tenant_id,
            ledger_id,
            responsibility_id
        ),

    CONSTRAINT ck_syirkah_loss_responsibility_amount
        CHECK (amount > 0)
);
```

And:

```sql
CREATE TABLE syirkah_loss_recovery_obligation (
    obligation_id          VARCHAR(100) NOT NULL,
    tenant_id              VARCHAR(100) NOT NULL,
    ledger_id              VARCHAR(100) NOT NULL,
    responsibility_id      VARCHAR(100) NOT NULL,
    calculation_id         VARCHAR(100) NOT NULL,
    party_id               VARCHAR(100) NOT NULL,
    amount                 DECIMAL(19, 4) NOT NULL,
    status                 VARCHAR(40) NOT NULL,
    approved_by            VARCHAR(100),
    approved_at            TIMESTAMP,
    posted_journal_entry_id VARCHAR(100),
    posted_at              TIMESTAMP,
    created_at              TIMESTAMP NOT NULL,

    CONSTRAINT pk_syirkah_loss_recovery
        PRIMARY KEY (obligation_id),

    CONSTRAINT uq_syirkah_loss_recovery
        UNIQUE (
            tenant_id,
            ledger_id,
            responsibility_id
        ),

    CONSTRAINT ck_syirkah_loss_recovery_amount
        CHECK (amount > 0)
);
```

Again, use the project's existing migration mechanism when implementing this; don't introduce a new migration technology just for Syirkah.

---

## The important architectural result

We now have a clean separation:

```text
                 GENERAL LEDGER
                       |
                       v
                Financial Result
                       |
                       v
               Syirkah Calculation
                  /          \
                 /            \
             Profit           Loss
               |                |
          Nisbah policy     Loss policy
                                |
                       +--------+--------+
                       |                 |
                    Normal          Responsibility
                     loss                |
                       |                 v
                       |          Determination
                       |                 |
                       |                 v
                       |          Recovery obligation
                       |                 |
                       +--------+--------+
                                |
                                v
                         Accounting Policy
                                |
                                v
                         General Ledger
```

This is the right direction for **Syirkah as a reusable platform capability**, because Islamic-law semantics remain in the domain layer while actual accounting treatment remains policy-driven.

The next piece I would implement after this is **capital/current-account settlement**: distinguish *profit distribution*, *return of capital*, *capital impairment*, *partner withdrawal*, and *cash settlement*. That is the layer that will make `tutup buku` behave correctly beyond simply calculating a profit-sharing number.

///////////
Yes. The next layer should be **partner capital/current-account settlement**, but it should remain distinct from profit-sharing calculation and from cash payment.

The source material gives us a useful separation: it treats *Prive/Bagi Hasil* as a closing movement, while the examples separately return the remaining capital to the partner when the Syirkah ends. Hukum-muamalah Hukum-muamalah

## 1. The model I recommend

Do **not** make `PartnerDistributionObligation` responsible for everything.

Use four layers:

```text
FinancialResult
      │
      ▼
SyirkahAllocation
      │
      ├── Profit allocation
      └── Loss allocation
              │
              ▼
PartnerAccount
      │
      ├── Capital account
      └── Current account
              │
              ▼
Settlement
      │
      ├── Internal accounting settlement
      └── Actual cash settlement
```

The distinction is important:

| Operation | Changes capital? | Changes current account? | Requires cash? |
|---|---:|---:|---:|
| Capital contribution | Yes | No | Usually |
| Profit allocation | No | Yes | No |
| Profit distribution | No | Yes | Not necessarily |
| Partner withdrawal/drawing | No | Yes | Yes |
| Capital return | Yes | No | Yes |
| Capital impairment | Yes | No | Not necessarily |
| Loss allocation | Policy-dependent | No | No |
| Cash settlement | No | Yes | Yes |

This prevents the common mistake of treating:

```text
profit = cash
distribution = capital return
withdrawal = profit
```

as equivalent operations.

---

# 2. Add a partner account aggregate

I would put this under the existing Syirkah Islamic capability/domain rather than creating another accounting module.

```text
finance/accounting/accounting-capabilities/
    syirkah-accounting-islamic/
        src/main/java/
            tech/kayys/syirkah/accounting/domain/syirkah/
                account/
```

First:

```java
package tech.kayys.syirkah.accounting.domain.syirkah.account;

public enum PartnerAccountMovementType {

    CAPITAL_CONTRIBUTION,

    PROFIT_ALLOCATION,

    LOSS_ALLOCATION,

    CAPITAL_RETURN,

    CAPITAL_IMPAIRMENT,

    PARTNER_WITHDRAWAL,

    CASH_SETTLEMENT
}
```

The important point is that `PARTNER_WITHDRAWAL` is **not automatically capital withdrawal**.

A partner can withdraw an amount that has already become payable through the current account.

---

# 3. Model capital and current account separately

I would use one aggregate with two balances rather than two unrelated aggregates.

```java
package tech.kayys.syirkah.accounting.domain.syirkah.account;

import java.math.BigDecimal;
import java.util.Objects;

public final class PartnerAccount {

    private final String accountId;
    private final String tenantId;
    private final String ledgerId;
    private final String contractId;
    private final String partyId;

    private BigDecimal capitalBalance;
    private BigDecimal currentAccountBalance;

    private PartnerAccount(
            String accountId,
            String tenantId,
            String ledgerId,
            String contractId,
            String partyId,
            BigDecimal capitalBalance,
            BigDecimal currentAccountBalance) {

        this.accountId = requireText(accountId, "accountId");
        this.tenantId = requireText(tenantId, "tenantId");
        this.ledgerId = requireText(ledgerId, "ledgerId");
        this.contractId = requireText(contractId, "contractId");
        this.partyId = requireText(partyId, "partyId");

        if (capitalBalance.signum() < 0) {
            throw new IllegalArgumentException("capitalBalance cannot be negative");
        }

        this.capitalBalance = capitalBalance;
        this.currentAccountBalance = currentAccountBalance;
    }

    public static PartnerAccount open(
            String accountId,
            String tenantId,
            String ledgerId,
            String contractId,
            String partyId) {

        return new PartnerAccount(
                accountId,
                tenantId,
                ledgerId,
                contractId,
                partyId,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    public void contributeCapital(BigDecimal amount) {
        requirePositive(amount, "amount");
        capitalBalance = capitalBalance.add(amount);
    }

    public void allocateProfit(BigDecimal amount) {
        requirePositive(amount, "amount");
        currentAccountBalance = currentAccountBalance.add(amount);
    }

    public void allocateLoss(BigDecimal amount) {
        requirePositive(amount, "amount");
        currentAccountBalance = currentAccountBalance.subtract(amount);
    }

    public void returnCapital(BigDecimal amount) {
        requirePositive(amount, "amount");

        if (amount.compareTo(capitalBalance) > 0) {
            throw new IllegalStateException(
                    "Capital return exceeds available capital");
        }

        capitalBalance = capitalBalance.subtract(amount);
    }

    public void impairCapital(BigDecimal amount) {
        requirePositive(amount, "amount");

        if (amount.compareTo(capitalBalance) > 0) {
            throw new IllegalStateException(
                    "Capital impairment exceeds available capital");
        }

        capitalBalance = capitalBalance.subtract(amount);
    }

    public void withdrawFromCurrentAccount(BigDecimal amount) {
        requirePositive(amount, "amount");

        if (amount.compareTo(currentAccountBalance) > 0) {
            throw new IllegalStateException(
                    "Partner withdrawal exceeds current account balance");
        }

        currentAccountBalance = currentAccountBalance.subtract(amount);
    }

    public String accountId() {
        return accountId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String ledgerId() {
        return ledgerId;
    }

    public String contractId() {
        return contractId;
    }

    public String partyId() {
        return partyId;
    }

    public BigDecimal capitalBalance() {
        return capitalBalance;
    }

    public BigDecimal currentAccountBalance() {
        return currentAccountBalance;
    }

    private static void requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);

        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);

        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }

        return value;
    }
}
```

However, I would **not yet allow arbitrary callers to invoke these methods**. The application layer should create typed movements and enforce the workflow.

---

# 4. Introduce an explicit capital movement

This gives us an audit trail independent from the resulting balance.

```java
package tech.kayys.syirkah.accounting.domain.syirkah.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record PartnerAccountMovement(
        String movementId,
        String tenantId,
        String ledgerId,
        String contractId,
        String partyId,
        PartnerAccountMovementType type,
        BigDecimal amount,
        LocalDate effectiveDate,
        String referenceType,
        String referenceId,
        String createdBy) {

    public PartnerAccountMovement {
        requireText(movementId, "movementId");
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(contractId, "contractId");
        requireText(partyId, "partyId");

        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(effectiveDate, "effectiveDate");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        requireText(referenceType, "referenceType");
        requireText(referenceId, "referenceId");
        requireText(createdBy, "createdBy");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
    }
}
```

Now every movement can be traced to something such as:

```text
SYIRKAH_CALCULATION
SYIRKAH_DISTRIBUTION
SYIRKAH_CAPITAL_CONTRIBUTION
SYIRKAH_CAPITAL_RETURN
SYIRKAH_WITHDRAWAL
```

---

# 5. Do not automatically turn loss allocation into capital impairment

This is particularly important given the loss-policy work we just added.

We currently have:

```text
Financial loss
     ↓
Loss allocation
     ↓
Partner responsibility / capital allocation
```

But:

```text
LOSS_ALLOCATION
```

does not necessarily mean:

```text
CAPITAL_IMPAIRMENT
```

The accounting treatment should remain policy-driven.

For example:

```java
public interface SyirkahLossAccountingPolicy {

    LossAccountingMapping map(
            SyirkahCloseSnapshot snapshot,
            PartnerAllocation allocation);

    record LossAccountingMapping(
            boolean reducesCapital,
            String accountId,
            String description) {
    }
}
```

That keeps the Islamic/business rule separate from the chart-of-accounts treatment.

---

# 6. Profit allocation versus profit distribution

This is where the current `PartnerDistributionObligation` should be clarified.

Suppose:

```text
Net profit       100
Partner A         40%
Partner B         60%
```

After calculation:

```text
A current account +40
B current account +60
```

Nothing has necessarily been paid yet.

Then:

```text
A requests withdrawal 20
```

we get:

```text
A current account: 40 -> 20
cash:              -20
```

The remaining 20 can stay payable/available to the partner.

Therefore `PartnerDistributionObligation` should represent the **approved distribution entitlement**, while the new partner account represents its effect on the partner's financial position.

---

# 7. Capital return is a completely different operation

Suppose:

```text
Initial capital A = 100
Profit allocation = 40
```

Partner position:

```text
Capital account       100
Current account        40
```

If the partner receives 40:

```text
Capital account       100
Current account         0
Cash                  -40
```

That is profit settlement.

If instead the Syirkah terminates and 100 capital is returned:

```text
Capital account       100 -> 0
Current account         0
Cash                  -100
```

That is **capital return**, not profit distribution.

This distinction is especially relevant to the source's example where, after the profit calculation, the remaining cash/assets are returned and the Syirkah ends. Hukum-muamalah

---

# 8. Add a settlement instruction

Cash settlement should not directly manipulate `PartnerAccount`.

```java
package tech.kayys.syirkah.accounting.domain.syirkah.settlement;

public enum PartnerSettlementType {

    PROFIT_DISTRIBUTION,

    CAPITAL_RETURN,

    PARTNER_WITHDRAWAL,

    CAPITAL_RECONCILIATION
}
```

Then:

```java
package tech.kayys.syirkah.accounting.domain.syirkah.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PartnerSettlementInstruction(
        String settlementId,
        String tenantId,
        String ledgerId,
        String contractId,
        String partyId,
        PartnerSettlementType type,
        BigDecimal amount,
        String currencyCode,
        LocalDate settlementDate,
        String sourceType,
        String sourceId,
        String requestedBy) {

    public PartnerSettlementInstruction {
        if (settlementId == null || settlementId.isBlank()) {
            throw new IllegalArgumentException("settlementId cannot be blank");
        }

        if (partyId == null || partyId.isBlank()) {
            throw new IllegalArgumentException("partyId cannot be blank");
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("currencyCode cannot be blank");
        }

        if (settlementDate == null) {
            throw new IllegalArgumentException("settlementDate cannot be null");
        }

        if (sourceType == null || sourceType.isBlank()) {
            throw new IllegalArgumentException("sourceType cannot be blank");
        }

        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId cannot be blank");
        }
    }
}
```

This gives us a clean boundary:

```text
PartnerAccount
    ↓
SettlementInstruction
    ↓
Payment subsystem
    ↓
Actual cash settlement
```

The payment subsystem can therefore be bank transfer, cash, wallet, payment gateway, etc.

---

# 9. Accounting must remain policy-driven

Similar to the existing:

```java
SyirkahDistributionAccountingPolicy
```

add:

```java
public interface SyirkahCapitalAccountingPolicy {

    JournalMapping mapCapitalContribution(
            PartnerAccountMovement movement);

    JournalMapping mapCapitalReturn(
            PartnerAccountMovement movement);

    JournalMapping mapCapitalImpairment(
            PartnerAccountMovement movement);

    JournalMapping mapPartnerWithdrawal(
            PartnerAccountMovement movement);

    record JournalMapping(
            List<JournalLineMapping> lines,
            String description) {
    }

    record JournalLineMapping(
            String accountId,
            LineType type,
            String amount,
            String currencyCode,
            String description) {
    }

    enum LineType {
        DEBIT,
        CREDIT
    }
}
```

Do not hardcode:

```text
Capital Return -> Account X
```

because the actual chart of accounts is tenant/configuration dependent.

---

# 10. Idempotency is mandatory

Every capital movement needs the same source discipline we added to journal posting.

For example:

```text
tenant_id
ledger_id
source_type
source_id
party_id
movement_type
```

with a uniqueness rule such as:

```sql
UNIQUE (
    tenant_id,
    ledger_id,
    source_type,
    source_id,
    party_id,
    movement_type
)
```

So if the close worker retries:

```text
POST /syirkah/close/CLOSE-2026-09/distribution
```

we don't accidentally:

```text
A + 10
A + 10
A + 10
```

because the same distribution is processed three times.

---

# 11. Close-cycle behavior becomes much cleaner

The lifecycle should now become:

```text
ACCOUNTING CLOSE
│
├── calculate financial result
│
├── calculate Syirkah allocation
│
├── freeze Syirkah snapshot
│
├── prepare partner distribution
│
├── approve distribution
│
├── post distribution accounting
│
├── update partner current accounts
│
└── complete SYIRKAH_DISTRIBUTION
```

But:

```text
CAPITAL RETURN
PARTNER WITHDRAWAL
CASH SETTLEMENT
```

should **not automatically become mandatory close tasks**.

They are downstream transactions.

For termination:

```text
close result
    ↓
final allocation
    ↓
capital reconciliation
    ↓
capital return instruction
    ↓
approval
    ↓
journal posting
    ↓
cash settlement
    ↓
Syirkah termination
```

That is much safer.

---

# 12. The really important addition: capital snapshot

Our existing `SyirkahCloseSnapshot` should be extended.

Add:

```java
public record PartnerCapitalSnapshot(
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
        BigDecimal closingCurrentAccount) {
}
```

Then the close snapshot becomes a genuine historical representation:

```text
SyirkahCloseSnapshot
│
├── Contract version
├── Financial result
│
├── Profit allocations
├── Loss allocations
│
├── Loss responsibilities
│
└── Partner capital snapshots
      │
      ├── Partner A
      ├── Partner B
      └── ...
```

This is important because the contract may later change.

The source explicitly describes a situation where a new investor enters and the old Syirkah arrangement is closed before a new Syirkah agreement/bookkeeping arrangement begins. Hukum-muamalah

Therefore:

> **Never reconstruct an old close from today's contract.**

Use the frozen snapshot.

---

# 13. One more domain concept: capital lifecycle

I would add:

```java
public enum PartnerCapitalStatus {

    ACTIVE,

    PARTIALLY_RETURNED,

    IMPAIRED,

    FULLY_RETURNED,

    CLOSED
}
```

And eventually:

```java
public enum SyirkahLifecycleStatus {

    ACTIVE,

    CLOSING,

    CLOSED
}
```

This lets us distinguish:

```text
period close
```

from:

```text
Syirkah termination
```

They are related, but not the same event.

A business can have:

```text
December accounting close
```

while the Syirkah continues into January.

---

# 14. Tests I would add immediately

The most valuable tests are behavioral.

### Profit

```text
opening capital = 100
profit allocation = 40
withdrawal = 15

closing capital = 100
closing current account = 25
```

### Capital return

```text
opening capital = 100
capital return = 60

closing capital = 40
```

### Prevent excessive capital return

```text
capital = 100
return = 101

=> rejected
```

### Prevent excessive withdrawal

```text
current account = 40
withdrawal = 41

=> rejected
```

### Profit does not automatically increase capital

```text
capital = 100
profit = 40

=> capital = 100
=> current account = 40
```

### Capital return does not become profit distribution

```text
capital = 100
capital return = 100

=> current account unchanged
=> capital = 0
```

### Idempotency

```text
same sourceId submitted twice

=> one movement
```

### Contract version isolation

```text
close uses contract version 3

contract later changes to version 4

=> close snapshot remains version 3
```

---

## Where this leaves the architecture

We now have a fairly complete Syirkah accounting chain:

```text
                 ┌──────────────────────┐
                 │   Syirkah Contract   │
                 │ version + parties    │
                 │ capital + nisbah     │
                 └──────────┬───────────┘
                            │
                            ▼
                 ┌──────────────────────┐
                 │ Financial Result     │
                 │ revenue/cost/net     │
                 └──────────┬───────────┘
                            │
                            ▼
                 ┌──────────────────────┐
                 │ Allocation Engine    │
                 │ profit/loss policy   │
                 └──────────┬───────────┘
                            │
             ┌──────────────┴──────────────┐
             ▼                             ▼
    Profit Allocation              Loss Allocation
             │                             │
             ▼                             ▼
    Partner Current A/C         Capital / Responsibility
             │                             │
             └──────────────┬──────────────┘
                            ▼
                  Immutable Close Snapshot
                            │
                            ▼
                 Distribution / Settlement
                            │
                  ┌─────────┴─────────┐
                  ▼                   ▼
             GL Journal          Payment System
```

This is the right foundation for the next major piece: **Syirkah termination / akad transition** — especially *tutup buku*, partner replacement, additional capital, withdrawal of a partner, valuation of remaining assets, and creation of a new contract/version without corrupting the previous period's accounting. The source specifically calls for closing the old arrangement and books before a new Syirkah arrangement when new capital/members are introduced. Hukum-muamalah

///////////

Good. The source gives us a stronger rule than simply “terminate the contract”:

> When a new partner/capital enters during an existing Syirkah, the existing Syirkah must first be closed and its financial position determined. Only afterward is the new Syirkah agreement established. The same applies when an existing partner exits. Hukum-muamalah Hukum-muamalah

So I would now add an explicit **Syirkah Transition** bounded concept.

## 1. Do not mutate the old contract

The current `SyirkahContract` is versioned, which is useful, but partner replacement should not merely be:

```java
contract.addVersion(...)
```

A transition represents a business event:

```text
OLD SYIRKAH
    │
    ├── close books
    ├── determine result
    ├── allocate profit/loss
    ├── reconcile capital/current accounts
    └── freeze snapshot
             │
             ▼
      TRANSITION
             │
             ├── partner exits
             ├── partner enters
             ├── capital changes
             └── retained result
             │
             ▼
NEW SYIRKAH
    │
    └── new contract/version
```

That is much safer for audit.

---

# 2. Transition type

```java
package tech.kayys.syirkah.accounting.domain.syirkah.transition;

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

And the reason:

```java
package tech.kayys.syirkah.accounting.domain.syirkah.transition;

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

I would keep `FULL_TERMINATION` distinct from an ordinary accounting period close.

---

# 3. Transition aggregate

```java
package tech.kayys.syirkah.accounting.domain.syirkah.transition;

import java.time.LocalDate;
import java.util.Objects;

public final class SyirkahTransition {

    public enum Status {
        DRAFT,
        CLOSING_REQUIRED,
        SNAPSHOT_FROZEN,
        SETTLEMENT_REQUIRED,
        CONTRACT_PENDING,
        COMPLETED,
        CANCELLED
    }

    private final String transitionId;
    private final String tenantId;
    private final String ledgerId;
    private final String previousContractId;
    private final int previousContractVersion;
    private final SyirkahTransitionType type;
    private final SyirkahTransitionReason reason;
    private final LocalDate transitionDate;

    private Status status;

    private String closeCycleId;
    private String snapshotId;
    private String nextContractId;

    private SyirkahTransition(
            String transitionId,
            String tenantId,
            String ledgerId,
            String previousContractId,
            int previousContractVersion,
            SyirkahTransitionType type,
            SyirkahTransitionReason reason,
            LocalDate transitionDate) {

        this.transitionId = requireText(transitionId, "transitionId");
        this.tenantId = requireText(tenantId, "tenantId");
        this.ledgerId = requireText(ledgerId, "ledgerId");
        this.previousContractId =
                requireText(previousContractId, "previousContractId");

        if (previousContractVersion <= 0) {
            throw new IllegalArgumentException(
                    "previousContractVersion must be positive");
        }

        this.previousContractVersion = previousContractVersion;
        this.type = Objects.requireNonNull(type);
        this.reason = Objects.requireNonNull(reason);
        this.transitionDate = Objects.requireNonNull(transitionDate);

        this.status = Status.DRAFT;
    }

    public static SyirkahTransition create(
            String transitionId,
            String tenantId,
            String ledgerId,
            String previousContractId,
            int previousContractVersion,
            SyirkahTransitionType type,
            SyirkahTransitionReason reason,
            LocalDate transitionDate) {

        return new SyirkahTransition(
                transitionId,
                tenantId,
                ledgerId,
                previousContractId,
                previousContractVersion,
                type,
                reason,
                transitionDate);
    }

    public void requireClose(String closeCycleId) {
        requireState(Status.DRAFT);

        this.closeCycleId = requireText(closeCycleId, "closeCycleId");
        this.status = Status.CLOSING_REQUIRED;
    }

    public void freezeSnapshot(String snapshotId) {
        requireState(Status.CLOSING_REQUIRED);

        this.snapshotId = requireText(snapshotId, "snapshotId");
        this.status = Status.SNAPSHOT_FROZEN;
    }

    public void requireSettlement() {
        requireState(Status.SNAPSHOT_FROZEN);
        this.status = Status.SETTLEMENT_REQUIRED;
    }

    public void attachNextContract(String nextContractId) {
        requireState(Status.SETTLEMENT_REQUIRED);

        this.nextContractId = requireText(nextContractId, "nextContractId");
        this.status = Status.CONTRACT_PENDING;
    }

    public void complete() {
        requireState(Status.CONTRACT_PENDING);
        this.status = Status.COMPLETED;
    }

    public void cancel() {
        if (status == Status.COMPLETED) {
            throw new IllegalStateException(
                    "Completed transition cannot be cancelled");
        }

        status = Status.CANCELLED;
    }

    private void requireState(Status expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Transition must be " + expected +
                    " but is " + status);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " cannot be blank");
        }

        return value;
    }

    public String transitionId() {
        return transitionId;
    }

    public Status status() {
        return status;
    }

    public String closeCycleId() {
        return closeCycleId;
    }

    public String snapshotId() {
        return snapshotId;
    }

    public String nextContractId() {
        return nextContractId;
    }
}
```

The important invariant is:

```text
SNAPSHOT_FROZEN
        ↓
settlement
        ↓
new contract
```

A new contract cannot be activated before the old state is frozen.

---

# 4. Partner transition instructions

We also need to describe what happens to each partner.

```java
package tech.kayys.syirkah.accounting.domain.syirkah.transition;

import java.math.BigDecimal;
import java.util.Objects;

public record PartnerTransitionInstruction(
        String partyId,
        TransitionAction action,
        BigDecimal capitalAtTransition,
        BigDecimal currentAccountAtTransition,
        boolean retainedInNextContract) {

    public enum TransitionAction {
        RETAIN,
        EXIT,
        ENTER,
        REPLACE
    }

    public PartnerTransitionInstruction {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(action, "action");

        if (partyId.isBlank()) {
            throw new IllegalArgumentException(
                    "partyId cannot be blank");
        }

        Objects.requireNonNull(capitalAtTransition);
        Objects.requireNonNull(currentAccountAtTransition);

        if (capitalAtTransition.signum() < 0) {
            throw new IllegalArgumentException(
                    "capitalAtTransition cannot be negative");
        }
    }
}
```

This allows:

```text
Old contract

Ahmad   RETAIN
Budi    EXIT

New contract

Ahmad   RETAIN
Utsman  ENTER
```

without pretending that Budi simply became Utsman.

---

# 5. The most important rule: retained profit

The source explicitly describes the case where the old partners close the books, have a profit, and decide whether to distribute it or retain it before creating the new Syirkah arrangement. Hukum-muamalah

Therefore add:

```java
package tech.kayys.syirkah.accounting.domain.syirkah.transition;

public enum ResultDisposition {

    DISTRIBUTE,

    RETAIN_IN_BUSINESS
}
```

And:

```java
package tech.kayys.syirkah.accounting.domain.syirkah.transition;

public record SyirkahResultDisposition(
        String calculationId,
        ResultDisposition disposition,
        String approvedBy) {

    public SyirkahResultDisposition {
        if (calculationId == null || calculationId.isBlank()) {
            throw new IllegalArgumentException(
                    "calculationId cannot be blank");
        }

        if (approvedBy == null || approvedBy.isBlank()) {
            throw new IllegalArgumentException(
                    "approvedBy cannot be blank");
        }
    }
}
```

This is crucial because:

```text
profit = 100
```

does **not** imply:

```text
old current accounts += 100
cash -= 100
```

The old partners may decide:

```text
DISTRIBUTE
```

or:

```text
RETAIN_IN_BUSINESS
```

---

# 6. Retained profit must affect the next opening position

For example:

```text
Old capital:
Ahmad = 60
Budi  = 40

Profit:
100

Profit disposition:
RETAIN
```

The next Syirkah does not start from:

```text
capital = 100
```

unless the new contract explicitly defines that.

Instead we need a transition opening position:

```java
package tech.kayys.syirkah.accounting.domain.syirkah.transition;

import java.math.BigDecimal;

public record NextContractOpeningPosition(
        String partyId,
        BigDecimal capitalContribution,
        BigDecimal retainedBusinessBalance) {

    public NextContractOpeningPosition {
        if (partyId == null || partyId.isBlank()) {
            throw new IllegalArgumentException(
                    "partyId cannot be blank");
        }

        if (capitalContribution == null ||
                capitalContribution.signum() < 0) {
            throw new IllegalArgumentException(
                    "capitalContribution cannot be negative");
        }

        if (retainedBusinessBalance == null ||
                retainedBusinessBalance.signum() < 0) {
            throw new IllegalArgumentException(
                    "retainedBusinessBalance cannot be negative");
        }
    }
}
```

But I would **not automatically convert retained profit into capital contribution**.

That requires an explicit contract decision.

This distinction matters because:

```text
retained business funds
```

and:

```text
partner capital
```

are not necessarily the same legal/accounting concept.

---

# 7. Partner replacement requires valuation

The source gives a particularly important example:

```text
Old partner's position = 103.8m
New partner pays      = 110m
Difference            = 6.2m
```

The source describes the difference as compensation related to the value of the business brand/intangible asset. Hukum-muamalah

Therefore we should **not** model partner replacement as:

```text
old capital = new capital
```

We need:

```java
package tech.kayys.syirkah.accounting.domain.syirkah.transition;

import java.math.BigDecimal;

public record PartnerInterestTransfer(
        String transferId,
        String outgoingPartyId,
        String incomingPartyId,
        BigDecimal carryingValue,
        BigDecimal agreedTransferPrice,
        BigDecimal premiumOrDiscount) {

    public PartnerInterestTransfer {
        if (transferId == null || transferId.isBlank()) {
            throw new IllegalArgumentException(
                    "transferId cannot be blank");
        }

        if (outgoingPartyId == null || outgoingPartyId.isBlank()) {
            throw new IllegalArgumentException(
                    "outgoingPartyId cannot be blank");
        }

        if (incomingPartyId == null || incomingPartyId.isBlank()) {
            throw new IllegalArgumentException(
                    "incomingPartyId cannot be blank");
        }

        if (carryingValue == null || carryingValue.signum() < 0) {
            throw new IllegalArgumentException(
                    "carryingValue cannot be negative");
        }

        if (agreedTransferPrice == null ||
                agreedTransferPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "agreedTransferPrice cannot be negative");
        }

        if (premiumOrDiscount == null) {
            throw new IllegalArgumentException(
                    "premiumOrDiscount cannot be null");
        }
    }

    public static PartnerInterestTransfer calculate(
            String transferId,
            String outgoingPartyId,
            String incomingPartyId,
            BigDecimal carryingValue,
            BigDecimal agreedTransferPrice) {

        return new PartnerInterestTransfer(
                transferId,
                outgoingPartyId,
                incomingPartyId,
                carryingValue,
                agreedTransferPrice,
                agreedTransferPrice.subtract(carryingValue));
    }
}
```

This keeps the distinction:

```text
carrying value
       ≠
transfer price
       ≠
new partner's capital contribution
```

unless an explicit policy says otherwise.

---

# 8. Syuf'ah should not be hidden inside accounting

The source also discusses **syuf'ah**, describing the existing partner's right concerning a partner interest transferred to a new external party. Hukum-muamalah

I would therefore create a separate policy boundary:

```java
package tech.kayys.syirkah.accounting.domain.syirkah.transition;

public interface PartnerTransferRestrictionPolicy {

    TransferDecision evaluate(
            String contractId,
            String outgoingPartyId,
            String incomingPartyId);

    record TransferDecision(
            boolean allowed,
            String reason) {
    }
}
```

Then a Musyarakah implementation can enforce whatever contractual/legal rules the tenant has configured.

Don't put syuf'ah logic into:

```text
PartnerAccount
```

or:

```text
GeneralLedgerService
```

because it is a **partner-right / contract policy**, not a journal-entry rule.

---

# 9. Full termination needs liquidation

The source's termination example is also very clear about the order:

```text
assets / cash / receivables
        ↓
liquidate / collect
        ↓
pay business debts
        ↓
determine remaining net assets
        ↓
determine profit/loss
        ↓
distribute profit
        ↓
return capital
        ↓
terminate Syirkah
```

The example explicitly deducts business debts before determining the remaining Syirkah assets, then separates profit sharing from return of initial capital. Hukum-muamalah

So add:

```java
package tech.kayys.syirkah.accounting.domain.syirkah.termination;

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

And a liquidation aggregate should track:

```text
liquidationId
tenantId
ledgerId
contractId
contractVersion
closeCycleId
status
grossRealization
liabilitiesSettled
netRealization
profitOrLoss
```

Do **not** simply calculate termination from partner capital balances.

The actual business assets and liabilities must be resolved first.

---

# 10. Termination should be a separate workflow

The resulting workflow becomes:

```text
                 SYIRKAH ACTIVE
                       │
             transition requested
                       │
                       ▼
              CLOSE CURRENT BOOKS
                       │
                       ▼
              FREEZE CLOSE SNAPSHOT
                       │
          ┌────────────┴────────────┐
          │                         │
     Partner change            Full closure
          │                         │
          ▼                         ▼
  transition settlement       liquidation
          │                         │
          ▼                         ▼
   next contract             liabilities settled
          │                         │
          ▼                         ▼
   next contract active       final result
                                    │
                                    ▼
                              profit allocation
                                    │
                                    ▼
                              capital return
                                    │
                                    ▼
                               TERMINATED
```

---

# 11. Extend `CloseCycle` carefully

I would **not** add all of these as mandatory close tasks:

```text
PARTNER_EXIT
CAPITAL_RETURN
LIQUIDATION
NEW_CONTRACT
```

Those are conditional.

The existing close remains:

```text
normal accounting close
        +
Syirkah result
        +
Syirkah allocation
        +
Syirkah distribution
```

Then:

```text
CloseCycle LOCKED
        │
        ├── ordinary continuing Syirkah
        │       └── next period
        │
        └── transition requested
                └── SyirkahTransition workflow
```

This prevents a normal monthly `tutup buku` from accidentally becoming a contract termination.

---

# 12. Application port

Add:

```java
package tech.kayys.syirkah.accounting.application.syirkah.transition;

import io.smallrye.mutiny.Uni;

import java.util.Optional;

public interface SyirkahTransitionRepository {

    Uni<Optional<SyirkahTransition>> findById(
            String tenantId,
            String ledgerId,
            String transitionId);

    Uni<Optional<SyirkahTransition>> findActiveByContractId(
            String tenantId,
            String ledgerId,
            String contractId);

    Uni<Void> save(
            String tenantId,
            String ledgerId,
            SyirkahTransition transition);
}
```

And enforce:

```sql
UNIQUE (
    tenant_id,
    ledger_id,
    transition_id
);
```

plus an application/database invariant preventing two active transitions for the same contract.

---

# 13. The transition service

The service should expose explicit operations rather than one giant method:

```java
public interface SyirkahTransitionService {

    Uni<SyirkahTransition> requestTransition(
            TransitionRequest request);

    Uni<SyirkahTransition> attachCloseCycle(
            String transitionId,
            String closeCycleId);

    Uni<SyirkahTransition> freezeSnapshot(
            String transitionId);

    Uni<SyirkahTransition> settleOldPosition(
            String transitionId);

    Uni<SyirkahTransition> attachNextContract(
            String transitionId,
            String nextContractId);

    Uni<SyirkahTransition> complete(
            String transitionId);
}
```

That gives us the proper maker/checker/audit boundaries.

---

# 14. The critical invariant

The new contract must **never become active before the previous position is frozen**.

Formally:

```text
newContract.active == true
```

requires:

```text
previousTransition.status == CONTRACT_PENDING
```

and therefore:

```text
previous close snapshot exists
previous close cycle is locked
old partner positions reconciled
required settlements completed
```

This is the key rule that prevents historical-period contamination.

---

## Resulting Syirkah architecture

At this point the Islamic accounting extension has become coherent:

```text
SyirkahContract
      │
      ▼
FinancialResult
      │
      ▼
Profit/Loss Allocation
      │
      ▼
CloseSnapshot
      │
      ├───────────────┐
      ▼               ▼
Partner Accounts   Responsibilities
      │
      ▼
Distribution / Settlement
      │
      ▼
SyirkahTransition
      │
      ├── Partner Entry
      ├── Partner Exit
      ├── Partner Replacement
      ├── Capital Change
      └── Full Termination
              │
              ▼
        New Contract
```

The next piece I would tackle is **the actual `tutup buku` calculation boundary**: making the snapshot derive a frozen partner opening/closing position from journal data, existing capital movements, distributions, retained profit, losses, and transition adjustments—without relying on mutable account balances. That will make the close mathematically reproducible and auditable.

/////////////

Yes. The next step should be the **actual `tutup buku` position boundary**.

The important design decision is:

> **The close snapshot must be calculated from immutable period movements and the prior frozen position—not from mutable partner/account balances.**

This makes the result reproducible even if account balances or operational projections change later.

### 1. Add a period movement provider

I would add this to the Syirkah accounting application/SPI layer:

```java
package tech.kayys.syirkah.accounting.application.syirkah.position;

import io.smallrye.mutiny.Uni;

import java.time.LocalDate;
import java.util.List;

public interface PartnerAccountMovementProvider {

    Uni<List<PartnerAccountMovement>> findMovements(
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate from,
            LocalDate to
    );
}
```

The movement itself should remain immutable:

```java
package tech.kayys.syirkah.accounting.application.syirkah.position;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record PartnerAccountMovement(
        String movementId,
        String tenantId,
        String ledgerId,
        String contractId,
        String partyId,
        MovementType type,
        BigDecimal amount,
        String currencyCode,
        LocalDate effectiveDate,
        String referenceType,
        String referenceId
) {

    public enum MovementType {
        CAPITAL_CONTRIBUTION,
        PROFIT_ALLOCATION,
        LOSS_ALLOCATION,
        CAPITAL_RETURN,
        CAPITAL_IMPAIRMENT,
        PARTNER_WITHDRAWAL,
        CASH_SETTLEMENT
    }

    public PartnerAccountMovement {
        requireText(movementId, "movementId");
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(contractId, "contractId");
        requireText(partyId, "partyId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(effectiveDate, "effectiveDate");
        requireText(referenceType, "referenceType");
        requireText(referenceId, "referenceId");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Movement amount must be positive");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
```

Notice that **cash settlement is deliberately a movement type but is not treated as capital or profit**.

---

# 2. Introduce a frozen partner period position

This is the actual accounting boundary for `tutup buku`.

```java
package tech.kayys.syirkah.accounting.application.syirkah.position;

import java.math.BigDecimal;
import java.util.Objects;

public record SyirkahPartnerPeriodPosition(
        String partyId,
        BigDecimal openingCapital,
        BigDecimal capitalContributions,
        BigDecimal capitalReturns,
        BigDecimal capitalImpairments,
        BigDecimal openingCurrentAccount,
        BigDecimal profitAllocations,
        BigDecimal lossAllocations,
        BigDecimal withdrawals,
        BigDecimal cashSettlements
) {

    public SyirkahPartnerPeriodPosition {
        Objects.requireNonNull(partyId, "partyId");
        requireNonNegative(openingCapital, "openingCapital");
        requireNonNegative(capitalContributions, "capitalContributions");
        requireNonNegative(capitalReturns, "capitalReturns");
        requireNonNegative(capitalImpairments, "capitalImpairments");
        requireNonNegative(openingCurrentAccount, "openingCurrentAccount");
        requireNonNegative(profitAllocations, "profitAllocations");
        requireNonNegative(lossAllocations, "lossAllocations");
        requireNonNegative(withdrawals, "withdrawals");
        requireNonNegative(cashSettlements, "cashSettlements");
    }

    public BigDecimal closingCapital() {
        return openingCapital
                .add(capitalContributions)
                .subtract(capitalReturns)
                .subtract(capitalImpairments);
    }

    public BigDecimal closingCurrentAccount() {
        return openingCurrentAccount
                .add(profitAllocations)
                .subtract(lossAllocations)
                .subtract(withdrawals);
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
```

This gives us a very important separation:

```text
Capital
    + contribution
    - return
    - impairment

Current account
    + profit allocation
    - loss allocation
    - withdrawal

Cash settlement
    = payment movement
```

**Profit allocation does not automatically increase capital.**

That distinction should remain explicit throughout the system.

---

# 3. Calculate positions from movements

Create:

```java
package tech.kayys.syirkah.accounting.application.syirkah.position;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SyirkahPartnerPositionCalculator {

    public SyirkahPartnerPeriodPosition calculate(
            String partyId,
            BigDecimal openingCapital,
            BigDecimal openingCurrentAccount,
            List<PartnerAccountMovement> movements
    ) {
        var partyMovements = movements.stream()
                .filter(m -> m.partyId().equals(partyId))
                .toList();

        return new SyirkahPartnerPeriodPosition(
                partyId,
                openingCapital,
                sum(partyMovements, PartnerAccountMovement.MovementType.CAPITAL_CONTRIBUTION),
                sum(partyMovements, PartnerAccountMovement.MovementType.CAPITAL_RETURN),
                sum(partyMovements, PartnerAccountMovement.MovementType.CAPITAL_IMPAIRMENT),
                openingCurrentAccount,
                sum(partyMovements, PartnerAccountMovement.MovementType.PROFIT_ALLOCATION),
                sum(partyMovements, PartnerAccountMovement.MovementType.LOSS_ALLOCATION),
                sum(partyMovements, PartnerAccountMovement.MovementType.PARTNER_WITHDRAWAL),
                sum(partyMovements, PartnerAccountMovement.MovementType.CASH_SETTLEMENT)
        );
    }

    private BigDecimal sum(
            List<PartnerAccountMovement> movements,
            PartnerAccountMovement.MovementType type
    ) {
        return movements.stream()
                .filter(m -> m.type() == type)
                .map(PartnerAccountMovement::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

However, I would **not** let this calculator obtain opening balances itself.

That belongs to a separate provider.

---

# 4. Opening position must come from the previous frozen close

Add:

```java
package tech.kayys.syirkah.accounting.application.syirkah.position;

import io.smallrye.mutiny.Uni;

import java.util.Optional;

public interface SyirkahOpeningPositionProvider {

    Uni<Optional<PartnerOpeningPosition>> findOpeningPosition(
            String tenantId,
            String ledgerId,
            String contractId,
            String partyId
    );

    record PartnerOpeningPosition(
            String partyId,
            java.math.BigDecimal openingCapital,
            java.math.BigDecimal openingCurrentAccount
    ) {
    }
}
```

The implementation should resolve:

```text
previous locked SyirkahCloseSnapshot
             |
             v
    previous closing position
             |
             v
      current opening
```

For the very first period:

```text
no previous snapshot
        |
        v
contract initial capital
        |
        v
opening position
```

This is much safer than:

```text
current PartnerAccount.capitalBalance
```

because `PartnerAccount` is mutable.

---

# 5. Add the close position calculation result

The close should produce a reconciliation object, not just balances.

```java
package tech.kayys.syirkah.accounting.application.syirkah.position;

import java.math.BigDecimal;
import java.util.List;

public record SyirkahPartnerClosePosition(
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

Then:

```java
package tech.kayys.syirkah.accounting.application.syirkah.position;

import io.smallrye.mutiny.Uni;

import java.time.LocalDate;
import java.util.List;

public final class SyirkahPartnerCloseCalculator {

    private final PartnerAccountMovementProvider movementProvider;
    private final SyirkahOpeningPositionProvider openingPositionProvider;
    private final SyirkahPartnerPositionCalculator positionCalculator;

    public SyirkahPartnerCloseCalculator(
            PartnerAccountMovementProvider movementProvider,
            SyirkahOpeningPositionProvider openingPositionProvider,
            SyirkahPartnerPositionCalculator positionCalculator
    ) {
        this.movementProvider = movementProvider;
        this.openingPositionProvider = openingPositionProvider;
        this.positionCalculator = positionCalculator;
    }

    public Uni<List<SyirkahPartnerClosePosition>> calculate(
            String tenantId,
            String ledgerId,
            String contractId,
            List<String> partyIds,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        return movementProvider.findMovements(
                tenantId,
                ledgerId,
                contractId,
                periodStart,
                periodEnd
        ).chain(movements ->
                Uni.combine().all().unis(
                        partyIds.stream()
                                .map(partyId -> calculateParty(
                                        tenantId,
                                        ledgerId,
                                        contractId,
                                        partyId,
                                        movements
                                ))
                                .toList()
                ).with(list -> list.stream()
                        .map(value -> (SyirkahPartnerClosePosition) value)
                        .toList())
        );
    }

    private Uni<SyirkahPartnerClosePosition> calculateParty(
            String tenantId,
            String ledgerId,
            String contractId,
            String partyId,
            List<PartnerAccountMovement> movements
    ) {
        return openingPositionProvider.findOpeningPosition(
                tenantId,
                ledgerId,
                contractId,
                partyId
        ).map(optional -> {
            var opening = optional.orElseThrow(() ->
                    new IllegalStateException(
                            "Opening position not found for party: " + partyId
                    ));

            var position = positionCalculator.calculate(
                    partyId,
                    opening.openingCapital(),
                    opening.openingCurrentAccount(),
                    movements
            );

            return new SyirkahPartnerClosePosition(
                    partyId,
                    position.openingCapital(),
                    position.capitalContributions(),
                    position.capitalReturns(),
                    position.capitalImpairments(),
                    position.closingCapital(),
                    position.openingCurrentAccount(),
                    position.profitAllocations(),
                    position.lossAllocations(),
                    position.withdrawals(),
                    position.closingCurrentAccount(),
                    position.cashSettlements()
            );
        });
    }
}
```

There is one refinement I would make in the actual implementation: avoid `Uni.combine()` over a large partner list and process in controlled batches if a Syirkah can have hundreds/thousands of partners.

---

# 6. Add reconciliation

This is critical for `tutup buku`.

We don't want:

```text
calculated closing capital = 109,500,000
```

without knowing whether all movements reconcile.

Add:

```java
package tech.kayys.syirkah.accounting.application.syirkah.position;

import java.math.BigDecimal;
import java.util.List;

public record SyirkahPositionReconciliation(
        boolean balanced,
        BigDecimal totalProfitAllocated,
        BigDecimal totalLossAllocated,
        BigDecimal totalCapitalContributed,
        BigDecimal totalCapitalReturned,
        BigDecimal totalCapitalImpaired,
        List<String> errors
) {
}
```

The reconciliation rules should include at least:

### Profit allocation

```text
sum(partner profit allocations)
    =
Syirkah profit allocated for distribution/retention
```

### Loss allocation

```text
sum(partner loss allocations)
    =
Syirkah loss allocated
```

### Capital

```text
opening capital
+ contributions
- returns
- impairments
=
closing capital
```

### Current account

```text
opening current account
+ profit allocations
- loss allocations
- withdrawals
=
closing current account
```

### Cash settlement

Cash settlement should **not** be included in those capital/current-account formulas unless the selected accounting policy explicitly models the settlement as a reduction of the corresponding payable/current-account balance.

That prevents double counting.

---

# 7. Most important: integrate this into `SyirkahCloseSnapshot`

The snapshot should now contain:

```text
SyirkahCloseSnapshot
│
├── Financial result
│   ├── revenue
│   ├── direct costs
│   ├── operating costs
│   ├── other costs
│   └── net result
│
├── Contract
│   ├── contract id
│   └── contract version
│
├── Partner positions
│   ├── opening capital
│   ├── contributions
│   ├── returns
│   ├── impairments
│   ├── closing capital
│   ├── opening current account
│   ├── profit allocations
│   ├── loss allocations
│   ├── withdrawals
│   ├── cash settlements
│   └── closing current account
│
├── Result allocations
│
├── Loss responsibilities
│
└── Reconciliation
```

This is the real historical `tutup buku` document.

---

# 8. Important correction to the earlier snapshot model

I would **not** store only:

```java
PartnerCapitalSnapshot
```

because that name is now too narrow.

Use:

```java
PartnerClosePositionSnapshot
```

instead:

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

This better represents what the close actually freezes.

---

# 9. Idempotency boundary

The calculation should work like this:

```text
calculate(closeCycleId)
        |
        v
find snapshot by tenant + ledger + closeCycle
        |
        +---- exists ----> return existing snapshot
        |
        +---- absent ----> calculate
                              |
                              v
                       reconcile
                              |
                              v
                       freeze snapshot
                              |
                              v
                           save
```

Never:

```text
calculate()
    -> delete old snapshot
    -> recalculate
    -> overwrite
```

A locked close snapshot should be **append-only/immutable**.

Database constraints should enforce:

```sql
UNIQUE (tenant_id, ledger_id, close_cycle_id)
```

and:

```sql
UNIQUE (tenant_id, ledger_id, calculation_id)
```

This also protects against two concurrent close workers.

---

# 10. The resulting `tutup buku` flow

The complete Syirkah close now becomes:

```text
Normal accounting close
        |
        v
FinancialResult
        |
        v
Syirkah calculation
        |
        v
Partner allocation
        |
        v
Load previous frozen positions
        |
        v
Load period partner movements
        |
        v
Calculate partner closing positions
        |
        v
Reconcile
        |
        +---- FAILED ---> close remains open
        |
        v
Freeze SyirkahCloseSnapshot
        |
        v
TASK_SYIRKAH_RESULT
        |
        v
TASK_SYIRKAH_ALLOCATION
        |
        v
Distribution / loss workflow
        |
        v
Accounting journal posted
        |
        v
TASK_SYIRKAH_DISTRIBUTION
        |
        v
Approve close
        |
        v
Lock fiscal period
```

### One especially important rule

The snapshot should be frozen **before** the distribution payment/settlement workflow.

That means:

```text
allocation determined
      ↓
position reconciled
      ↓
snapshot frozen
      ↓
distribution accounting
      ↓
cash settlement later
```

So if a partner is paid two weeks later, the historical `tutup buku` does not change.

---

## Next implementation step

After this boundary, the next concrete piece should be the **movement source** itself:

```text
PartnerAccountMovementProvider
          |
          +-- capital contribution movements
          +-- profit allocation movements
          +-- loss allocation movements
          +-- capital return movements
          +-- impairment movements
          +-- withdrawal movements
          +-- settlement movements
```

and, importantly, determine which of those should be **derived from existing journal entries** versus persisted as **business movements**.

That is where we should connect the Syirkah layer to the existing accounting implementation without creating a second accounting system.

//////////////
Good. The ledger inspection gives us an important constraint: **the existing `JournalEntry` already carries `tenantId`, `ledgerId`, `ShariaContractType`, and line dimensions**, but the current `JournalEntryRepository` only exposes `findAll()`, `findById()`, and `save()`. syirkah-framework(1)

So the next extension should be a **Syirkah movement projection**, not another accounting ledger.

## 1. Separate two kinds of movements

We should explicitly classify movements into:

### A. Accounting-derived movements

These are facts that already exist in the GL:

```text
profit allocation
loss allocation
capital contribution
capital return
capital impairment
partner withdrawal
cash settlement
```

But **only if the journal has a Syirkah source/reference or an explicit partner/contract classification**.

We should not infer:

> "This debit/credit looks like partner capital"

from account balances alone.

### B. Business movements

Some business events exist before accounting:

```text
partner admitted
capital contribution agreed
capital returned
partner withdrawal approved
profit allocation determined
```

These should produce a business movement first, then accounting policy may generate the journal.

That gives us:

```text
Business event
     |
     v
PartnerAccountMovement
     |
     v
Accounting policy
     |
     v
JournalEntry
```

rather than:

```text
JournalEntry
     |
     v
guess what business event happened
```

That distinction will make the Syirkah layer much safer.

---

# 2. Add explicit movement source metadata

I recommend extending `PartnerAccountMovement` with:

```java
public record PartnerAccountMovement(
        String movementId,
        String tenantId,
        String ledgerId,
        String contractId,
        String partyId,
        MovementType type,
        BigDecimal amount,
        String currencyCode,
        LocalDate effectiveDate,
        String sourceType,
        String sourceId,
        String accountingJournalEntryId
) {
    // validation
}
```

The important fields are:

```text
sourceType
sourceId
accountingJournalEntryId
```

For example:

```text
sourceType = SYIRKAH_PROFIT_ALLOCATION
sourceId   = calculation-8f4...
journal    = JE-019283
```

or:

```text
sourceType = PARTNER_CAPITAL_CONTRIBUTION
sourceId   = contribution-123
journal    = JE-019284
```

This gives us full traceability:

```text
Syirkah business fact
        ↓
movement
        ↓
journal
        ↓
close snapshot
```

---

# 3. Create the movement repository

This should be a proper persistence boundary:

```java
package tech.kayys.syirkah.accounting.application.syirkah.position;

import io.smallrye.mutiny.Uni;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PartnerAccountMovementRepository {

    Uni<Optional<PartnerAccountMovement>> findById(
            String tenantId,
            String ledgerId,
            String movementId
    );

    Uni<List<PartnerAccountMovement>> findByContractAndPeriod(
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate from,
            LocalDate to
    );

    Uni<List<PartnerAccountMovement>> findBySource(
            String tenantId,
            String ledgerId,
            String sourceType,
            String sourceId
    );

    Uni<Void> save(PartnerAccountMovement movement);
}
```

And then the existing:

```java
PartnerAccountMovementProvider
```

becomes a thin read abstraction over this repository.

---

# 4. Make movement creation idempotent

This is essential.

For example, profit allocation:

```text
calculationId = CALC-001
party = PARTY-A
amount = 2,000,000
```

must never generate two movements because two workers execute the close simultaneously.

Database constraint:

```sql
UNIQUE (
    tenant_id,
    ledger_id,
    source_type,
    source_id,
    party_id,
    movement_type
)
```

So:

```text
prepare allocation
        |
        v
create movement
        |
        +-- already exists --> reuse
        |
        +-- absent ----------> insert
```

The same pattern should be used for capital contributions, returns, withdrawals, etc.

---

# 5. Do not derive partner allocation from generic journal lines

This is a critical boundary.

The current `JournalEntry.JournalLine` contains:

- account ID
- debit
- credit
- description
- Sharia contract type
- dimensions

and dimensions currently include things such as cost center, profit center, department, project, branch, and segment. syirkah-framework(1)

There is **no existing partner dimension**.

Therefore we should **not** do something like:

```java
if (line.description().contains("Ahmad")) {
    // must be partner Ahmad
}
```

or:

```java
// account 3100 means partner capital
```

That would create fragile implicit accounting semantics.

Instead, Syirkah-generated journal entries should carry explicit source metadata.

---

# 6. Extend journal source support

The existing `PostJournalEntryCommand` already has:

```java
String sourceType,
String sourceId,
String postedBy
```

which is exactly the right extension point. syirkah-framework(1)

So we should **reuse it**, not introduce another journal-reference mechanism.

I recommend adding tenant and ledger context as previously discussed:

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

Then `GeneralLedgerService` must construct the journal using:

```java
new JournalEntry(
    entryId,
    TenantId.of(command.tenantId()),
    LedgerId.of(command.ledgerId()),
    ...
);
```

rather than the current constructor that defaults to the default tenant/primary ledger.

That existing defaulting is visible in `JournalEntry`, where the convenience constructor uses `TenantId.defaultTenant()` and `LedgerId.primary()`. syirkah-framework(1)

---

# 7. Add source metadata directly to `JournalEntry`

I would now make the earlier proposed change concrete:

```java
private String sourceType;
private String sourceId;

public void setPostingSource(String sourceType, String sourceId) {
    if (status != EntryStatus.DRAFT) {
        throw new IllegalStateException(
                "Cannot modify posting source after journal is no longer draft"
        );
    }

    if (sourceType == null || sourceType.isBlank()) {
        throw new IllegalArgumentException("sourceType must not be blank");
    }

    if (sourceId == null || sourceId.isBlank()) {
        throw new IllegalArgumentException("sourceId must not be blank");
    }

    this.sourceType = sourceType;
    this.sourceId = sourceId;
}

public String getSourceType() {
    return sourceType;
}

public String getSourceId() {
    return sourceId;
}
```

And when creating a reversal, preserve it.

The existing reversal implementation already preserves the Sharia contract type and reversal metadata, so source preservation belongs in that same mechanism. syirkah-framework(1)

---

# 8. Extend `JournalEntryRepository`

The current repository is too weak for reliable Syirkah projection. It currently exposes only:

```java
findById(...)
findAll()
save(...)
```

syirkah-framework(1)

Add:

```java
Uni<Optional<JournalEntry>> findBySource(
        String tenantId,
        String ledgerId,
        String sourceType,
        String sourceId
);
```

Eventually, also add a proper period query:

```java
Uni<List<JournalEntry>> findPostedByTenantLedgerAndDateRange(
        String tenantId,
        String ledgerId,
        LocalDate from,
        LocalDate to
);
```

Then `PeriodAwareSyirkahFinancialResultProvider` no longer needs to load the entire journal repository and filter in memory.

---

# 9. Create the projection service

Now the bridge becomes:

```java
public final class SyirkahMovementProjectionService {

    private final PartnerAccountMovementRepository movementRepository;

    public SyirkahMovementProjectionService(
            PartnerAccountMovementRepository movementRepository
    ) {
        this.movementRepository = movementRepository;
    }

    public Uni<PartnerAccountMovement> project(
            PartnerAccountMovement movement
    ) {
        return movementRepository.findBySource(
                movement.tenantId(),
                movement.ledgerId(),
                movement.sourceType(),
                movement.sourceId()
        ).chain(existing -> {

            if (!existing.isEmpty()) {
                return Uni.createFrom().item(existing.getFirst());
            }

            return movementRepository.save(movement)
                    .replaceWith(movement);
        });
    }
}
```

In production, the database uniqueness constraint remains the final concurrency guard.

The application-level lookup is only an optimization/idempotency convenience.

---

# 10. Profit allocation example

After:

```text
SyirkahResultCalculator
```

returns:

```text
Net profit = 5,000,000

Ahmad = 2,000,000
Budi  = 3,000,000
```

we create business movements:

```text
SYIRKAH_PROFIT_ALLOCATION
CALC-001
AHMAD
2,000,000

SYIRKAH_PROFIT_ALLOCATION
CALC-001
BUDI
3,000,000
```

Then accounting policy maps that to the existing GL:

```text
Dr appropriate distribution/current-account treatment
Cr appropriate Syirkah profit allocation treatment
```

The exact accounts remain policy-driven.

**The Syirkah domain should not hardcode those accounts.**

---

# 11. Capital contribution example

A partner contributes:

```text
Rp100,000,000
```

Business movement:

```text
CAPITAL_CONTRIBUTION
party = AHMAD
amount = 100,000,000
```

Accounting policy may produce:

```text
Dr Cash                    100,000,000
Cr Partner Capital         100,000,000
```

Then both records share the same source:

```text
sourceType = PARTNER_CAPITAL_CONTRIBUTION
sourceId   = CONTRIBUTION-001
```

Now the close can reconcile business movement and accounting movement.

---

# 12. Capital return

This must remain different from profit distribution:

```text
CAPITAL_RETURN
```

not:

```text
PROFIT_DISTRIBUTION
```

For example:

```text
Opening capital        100m
Capital contribution    20m
Capital return          10m
Closing capital         110m
```

while:

```text
Profit allocation        8m
```

belongs to the partner's current account, not automatically capital.

This is exactly why the frozen close position we designed earlier needs both:

```text
closingCapital
closingCurrentAccount
```

---

# 13. The resulting architecture

We now have a clean boundary:

```text
                    SYIRKAH DOMAIN
                         |
        +----------------+----------------+
        |                |                |
        v                v                v
 Financial Result   Partner Movement   Contract
        |                |
        |                v
        |          Movement Repository
        |                |
        +-------+--------+
                |
                v
       Syirkah Close Calculator
                |
                v
       Immutable Close Snapshot
                |
                v
        Accounting Policy
                |
                v
          General Ledger
                |
                v
          Journal Entry
```

And the reverse read path is deliberately different:

```text
Journal Entry
     |
     v
explicit source/reference
     |
     v
Syirkah movement
     |
     v
reconciliation
```

We **never infer Syirkah ownership merely from mutable account balances**.

---

## 14. One more important improvement

The current `JournalEntryPosted` event contains tenant, ledger, journal ID, entry number, poster, correlation ID, and causation ID, but it does **not currently contain `sourceType/sourceId`**. syirkah-framework(1)

I would extend it:

```java
public record JournalEntryPosted(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        LedgerId ledgerId,
        JournalEntryId journalEntryId,
        String entryNumber,
        String postedBy,
        String sourceType,
        String sourceId,
        String correlationId,
        String causationId
) implements AccountingEvent {
}
```

That gives event-driven deployments a much cleaner option:

```text
JournalEntryPosted
        |
        +--> accounting projections
        |
        +--> audit
        |
        +--> Syirkah movement projection
        |
        +--> external integration
```

while the monolith can simply invoke the same application service synchronously.

---

### So the next implementation sequence is now clear

```text
1. Extend JournalEntry with source metadata
2. Extend PostJournalEntryCommand with tenant/ledger context
3. Extend JournalEntryRepository with source + period queries
4. Extend JournalEntryPosted with source metadata
5. Add PartnerAccountMovementRepository
6. Add idempotent MovementProjectionService
7. Make Syirkah movement creation use business source IDs
8. Build the close position from those movements
9. Freeze it into SyirkahCloseSnapshot
10. Reconcile snapshot ↔ journal movements
```

This preserves the existing accounting implementation while making **Syirkah-aware `tutup buku` a first-class, auditable extension** rather than a second accounting engine.


//////////////


The next layer should be the **Syirkah movement write model** itself. The important decision is: **do not make `PartnerAccount` the source of truth**. It should be a projection/current-state view; the immutable movement ledger is what `tutup buku` consumes.

### 1. Establish the movement lifecycle

```text
Business operation
      │
      ▼
PartnerAccountMovement
      │
      ├── sourceType/sourceId
      ├── contractId
      ├── partyId
      ├── movementType
      └── effectiveDate
      │
      ▼
Accounting policy
      │
      ▼
JournalEntry
      │
      └── accountingJournalEntryId
```

For example, a capital contribution should produce:

```text
CAPITAL_CONTRIBUTION
    ↓
PartnerAccountMovement
    ↓
SyirkahCapitalAccountingPolicy
    ↓
JournalEntry
```

The reverse direction should only happen when the journal explicitly identifies its Syirkah source. We should **never infer a partner from account descriptions or account balances**.

The existing framework already follows a similar append-only movement pattern for assets, where movements are historical facts linked to their source event. syirkah-framework(1)

---

## 2. Make `PartnerAccountMovement` immutable

I would use this as the canonical domain object:

```java
public record PartnerAccountMovement(
        String movementId,
        String tenantId,
        String ledgerId,
        String contractId,
        String partyId,
        MovementType type,
        BigDecimal amount,
        String currencyCode,
        LocalDate effectiveDate,
        String sourceType,
        String sourceId,
        String accountingJournalEntryId
) {

    public PartnerAccountMovement {
        requireText(movementId, "movementId");
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(contractId, "contractId");
        requireText(partyId, "partyId");
        requireText(currencyCode, "currencyCode");
        requireText(sourceType, "sourceType");
        requireText(sourceId, "sourceId");

        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(effectiveDate, "effectiveDate cannot be null");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Movement amount must be positive");
        }
    }

    public enum MovementType {
        CAPITAL_CONTRIBUTION,
        PROFIT_ALLOCATION,
        LOSS_ALLOCATION,
        CAPITAL_RETURN,
        CAPITAL_IMPAIRMENT,
        PARTNER_WITHDRAWAL,
        CASH_SETTLEMENT
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
    }
}
```

The movement is **positive by definition**. Its semantic effect comes from `MovementType`.

That is much safer than having a generic `amount` that may randomly be positive or negative.

---

# 3. Separate movement creation from journal posting

This is the most important architectural boundary.

Create:

```java
public interface PartnerAccountMovementService {

    Uni<PartnerAccountMovement> record(
            RecordPartnerAccountMovementRequest request);
}
```

with:

```java
public record RecordPartnerAccountMovementRequest(
        String tenantId,
        String ledgerId,
        String contractId,
        String partyId,
        PartnerAccountMovement.MovementType type,
        BigDecimal amount,
        String currencyCode,
        LocalDate effectiveDate,
        String sourceType,
        String sourceId,
        String recordedBy
) {}
```

The service should:

1. validate contract/party ownership;
2. check source idempotency;
3. create the immutable movement;
4. persist it;
5. optionally invoke the accounting policy;
6. record the resulting journal reference.

But I would **not** make movement persistence and journal posting two unrelated operations.

The desired transaction is:

```text
movement
   +
journal
   +
movement.accountingJournalEntryId
```

or neither.

---

# 4. Idempotency becomes a first-class invariant

The database should enforce:

```sql
UNIQUE (
    tenant_id,
    ledger_id,
    source_type,
    source_id,
    party_id,
    movement_type
);
```

And the repository:

```java
public interface PartnerAccountMovementRepository {

    Uni<Optional<PartnerAccountMovement>> findById(
            String tenantId,
            String ledgerId,
            String movementId);

    Uni<List<PartnerAccountMovement>> findByContractAndPeriod(
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate from,
            LocalDate to);

    Uni<List<PartnerAccountMovement>> findBySource(
            String tenantId,
            String ledgerId,
            String sourceType,
            String sourceId);

    Uni<Void> save(PartnerAccountMovement movement);
}
```

The unique constraint is essential because:

```text
worker A -> source not found
worker B -> source not found
worker A -> insert
worker B -> insert
```

must not create two capital contributions.

The application check is for normal flow; the database constraint is the concurrency guard.

---

# 5. Do not immediately update `PartnerAccount`

Instead:

```text
PartnerAccountMovement
        │
        ▼
PartnerAccountProjection
        │
        ▼
PartnerAccount
```

`PartnerAccount` becomes a **materialized current position**.

That gives us:

```text
Immutable history
       ↓
PartnerAccountMovement
       ↓
Current projection
       ↓
PartnerAccount
```

If the projection is corrupted, it can be rebuilt from movements.

That is exactly what we want for financial reliability.

---

# 6. Accounting must point back to the movement

The current framework already has an important prerequisite: `PostJournalEntryCommand` already contains:

```java
String sourceType,
String sourceId,
String postedBy
```

as confirmed in the uploaded framework. syirkah-framework(1)

So the Syirkah side should use stable source identifiers such as:

```text
sourceType = SYIRKAH_PARTNER_MOVEMENT
sourceId   = movementId
```

Then the journal has an explicit relationship:

```text
PartnerAccountMovement
       │
       │ sourceId
       ▼
JournalEntry
```

This is much stronger than trying to recover the relationship from descriptions later.

---

# 7. Example: capital contribution

Suppose:

```text
Contract: SYK-001
Party:     PARTY-BUDI
Amount:    50,000,000
```

The business operation creates:

```text
MovementType = CAPITAL_CONTRIBUTION
Amount       = 50,000,000
```

Then accounting policy may map it to:

```text
DEBIT   Cash / Bank                    50,000,000
CREDIT  Partner Capital - Budi         50,000,000
```

The journal should carry:

```text
sourceType = SYIRKAH_PARTNER_MOVEMENT
sourceId   = <movementId>
```

and the movement eventually records:

```text
accountingJournalEntryId = <journalEntryId>
```

So we have bidirectional traceability:

```text
movement → journal
journal  → movement
```

---

# 8. Profit allocation is deliberately different

For:

```text
PROFIT_ALLOCATION
```

we should **not** increase capital automatically.

The movement affects:

```text
current account
```

not:

```text
capital
```

So:

```text
Opening capital
+ capital contributions
- capital returns
- capital impairment
= closing capital
```

while:

```text
Opening current account
+ profit allocation
- loss allocation
- withdrawals
= closing current account
```

This distinction is critical for the Syirkah accounting model.

---

# 9. Cash settlement is downstream

This is another important separation:

```text
PROFIT_ALLOCATION
        ≠
CASH_SETTLEMENT
```

For example:

```text
Profit allocation       10,000,000
Cash actually paid       4,000,000
Remaining payable        6,000,000
```

The first movement determines the partner's economic entitlement.

The second records actual settlement.

Otherwise the closing calculation could accidentally subtract the same amount twice.

---

# 10. `tutup buku` should consume movements

Now the close calculation becomes deterministic:

```text
Locked opening snapshot
        +
PartnerAccountMovement during period
        ↓
SyirkahPartnerClosePosition
        ↓
Reconciliation
        ↓
SyirkahCloseSnapshot
```

For each party:

```java
public record SyirkahPartnerClosePosition(
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
) {}
```

The equations then become explicit:

```text
closingCapital =
    openingCapital
    + capitalContributions
    - capitalReturns
    - capitalImpairments
```

and:

```text
closingCurrentAccount =
    openingCurrentAccount
    + profitAllocations
    - lossAllocations
    - withdrawals
```

`cashSettlements` remains separately reported.

---

# 11. Reconciliation must happen before snapshot freezing

The close engine should reject:

```text
CALCULATED
```

if movement reconciliation fails.

For example:

```text
Expected profit allocation:       100,000,000
Partner movements:                100,000,000
Difference:                                 0
```

passes.

But:

```text
Expected profit allocation:       100,000,000
Partner movements:                 90,000,000
Difference:                         10,000,000
```

must prevent the snapshot from becoming frozen.

The reconciliation object should therefore contain:

```java
public record SyirkahPositionReconciliation(
        boolean balanced,
        BigDecimal totalProfitAllocated,
        BigDecimal totalLossAllocated,
        BigDecimal totalCapitalContributed,
        BigDecimal totalCapitalReturned,
        BigDecimal totalCapitalImpaired,
        List<String> errors
) {}
```

---

# 12. The resulting `tutup buku` flow

I would now lock the architecture around this sequence:

```text
                     ┌─────────────────────┐
                     │ Business Operations  │
                     └──────────┬──────────┘
                                │
                                ▼
                     PartnerAccountMovement
                                │
                                ▼
                     Accounting Policy
                                │
                                ▼
                         Journal Entry
                                │
                                ▼
                     Accounting Journal
                                │
                                │
             ┌──────────────────┴──────────────────┐
             │                                     │
             ▼                                     ▼
     Current Partner View                 Close Calculation
                                                   │
                                                   ▼
                                      Partner Period Position
                                                   │
                                                   ▼
                                           Reconciliation
                                                   │
                                          balanced?
                                           /      \
                                         no        yes
                                         │          │
                                       STOP         ▼
                                               Close Snapshot
                                                   │
                                                   ▼
                                             Close Approval
                                                   │
                                                   ▼
                                                LOCK
```

This gives us a very clean rule:

> **The mutable partner account is a projection; the movement history and immutable close snapshot are the accounting evidence.**

That is the right foundation for `tutup buku`, partner statements, transition between akad, partner exit/entry, and eventually audit/reconstruction.

One additional point from the framework: the existing `GeneralLedgerService` currently mutates account balances and saves the journal separately, so the next hardening step should be the **transaction boundary around movement + journal + account mutation**, rather than adding more Syirkah calculations on top of that unsafe boundary. syirkah-framework(1)

### Next step

The next implementation slice should therefore be **the transactional `SyirkahPartnerMovementPostingService`**: one operation that records a movement, invokes the existing accounting policy, posts an idempotent journal, links the journal back to the movement, and makes the whole operation atomic. That is the bridge before we finish the immutable `SyirkahCloseSnapshot`.

/////////////

The next slice is the **transactional movement posting boundary**. The existing framework already has `UnitOfWork`-style transactional infrastructure in other bounded contexts, while the accounting posting handlers currently perform repository writes sequentially without that common transaction boundary. syirkah-framework(1)

### 1. Introduce an accounting transaction port

Keep the transaction abstraction above the persistence implementation:

```java
public interface AccountingUnitOfWork {

    <T> Uni<T> execute(Supplier<Uni<T>> work);
}
```

A database adapter can implement it with the existing reactive transaction mechanism.

The important point is that Syirkah must not know whether the implementation is:

- PostgreSQL/Panache,
- another relational database,
- an integration transaction,
- or an eventually-consistent adapter.

---

### 2. Create the Syirkah posting service

```java
public final class SyirkahPartnerMovementPostingService {

    public static final String SOURCE_TYPE = "SYIRKAH_PARTNER_MOVEMENT";

    private final PartnerAccountMovementRepository movementRepository;
    private final PartnerMovementAccountingPolicy accountingPolicy;
    private final GeneralLedgerService generalLedgerService;
    private final AccountingUnitOfWork unitOfWork;

    public SyirkahPartnerMovementPostingService(
            PartnerAccountMovementRepository movementRepository,
            PartnerMovementAccountingPolicy accountingPolicy,
            GeneralLedgerService generalLedgerService,
            AccountingUnitOfWork unitOfWork
    ) {
        this.movementRepository = Objects.requireNonNull(movementRepository);
        this.accountingPolicy = Objects.requireNonNull(accountingPolicy);
        this.generalLedgerService = Objects.requireNonNull(generalLedgerService);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
    }

    public Uni<PartnerAccountMovement> post(
            RecordPartnerAccountMovementRequest request) {

        return unitOfWork.execute(() ->
                movementRepository.findBySource(
                        request.tenantId(),
                        request.ledgerId(),
                        request.sourceType(),
                        request.sourceId()
                ).chain(existing -> {

                    if (!existing.isEmpty()) {
                        return resolveExisting(existing, request);
                    }

                    PartnerAccountMovement movement =
                            PartnerAccountMovementFactory.create(request);

                    return accountingPolicy.map(movement)
                            .chain(mapping ->
                                    postJournal(movement, mapping, request)
                            )
                            .chain(journalEntryId -> {
                                PartnerAccountMovement linked =
                                        movement.withAccountingJournalEntry(
                                                journalEntryId.value().toString());

                                return movementRepository.save(linked)
                                        .replaceWith(linked);
                            });
                })
        );
    }

    private Uni<PartnerAccountMovement> postJournal(
            PartnerAccountMovement movement,
            JournalMapping mapping,
            RecordPartnerAccountMovementRequest request) {

        PostJournalEntryCommand command =
                PostJournalEntryCommand.builder()
                        .description(mapping.description())
                        .referenceNumber(
                                "SYK-" + movement.movementId())
                        .lines(mapping.toJournalLines())
                        .sourceType(SOURCE_TYPE)
                        .sourceId(movement.movementId())
                        .postedBy(request.recordedBy())
                        .build();

        return generalLedgerService.postJournalEntry(command);
    }

    private Uni<PartnerAccountMovement> resolveExisting(
            List<PartnerAccountMovement> existing,
            RecordPartnerAccountMovementRequest request) {

        if (existing.size() != 1) {
            return Uni.createFrom().failure(
                    new IllegalStateException(
                            "Multiple movements found for source "
                                    + request.sourceId()));
        }

        PartnerAccountMovement movement = existing.get(0);

        validateIdempotentRequest(movement, request);

        return Uni.createFrom().item(movement);
    }

    private void validateIdempotentRequest(
            PartnerAccountMovement existing,
            RecordPartnerAccountMovementRequest request) {

        if (!existing.contractId().equals(request.contractId())
                || !existing.partyId().equals(request.partyId())
                || existing.type() != request.type()
                || existing.amount().compareTo(request.amount()) != 0) {

            throw new IllegalStateException(
                    "Source already exists with different movement data: "
                            + request.sourceId());
        }
    }
}
```

There is one deliberate difference from the simple `IdempotencyGuard` already in the framework: that guard is an in-memory `ConcurrentHashMap` and therefore cannot be the financial source of truth across multiple instances. syirkah-framework(1)

For Syirkah financial movements, **database uniqueness + transaction** is the real idempotency mechanism.

---

# 3. Add the accounting policy

Don't put account IDs into `PartnerAccountMovement`.

Instead:

```java
public interface PartnerMovementAccountingPolicy {

    Uni<JournalMapping> map(PartnerAccountMovement movement);
}
```

For example:

```java
public record JournalMapping(
        String description,
        List<JournalLineMapping> lines
) {

    public JournalMapping {
        Objects.requireNonNull(description);
        Objects.requireNonNull(lines);

        if (lines.isEmpty()) {
            throw new IllegalArgumentException(
                    "Journal mapping must contain at least one line");
        }
    }
}
```

The actual account mapping remains configuration/policy:

```text
CAPITAL_CONTRIBUTION
    -> Bank/Cash
    -> Partner Capital

PROFIT_ALLOCATION
    -> Profit Allocation / Equity Clearing
    -> Partner Current Account

LOSS_ALLOCATION
    -> Partner Current Account / Loss Clearing
    -> Business Result

CAPITAL_RETURN
    -> Partner Capital
    -> Bank/Cash

CAPITAL_IMPAIRMENT
    -> Capital Impairment
    -> Partner Capital

PARTNER_WITHDRAWAL
    -> Partner Current Account
    -> Bank/Cash

CASH_SETTLEMENT
    -> Partner Payable / Current Account
    -> Bank/Cash
```

Those mappings should remain policy-driven because the exact accounting treatment can depend on the contract and accounting policy.

---

# 4. One important correction: don't make `PartnerAccountMovement` mutable

The previous direction suggested adding:

```java
accountingJournalEntryId
```

That is useful, but the movement itself should remain immutable.

Therefore don't do:

```java
movement.setAccountingJournalEntryId(...)
```

Use a new immutable instance:

```java
public PartnerAccountMovement withAccountingJournalEntry(
        String journalEntryId) {

    return new PartnerAccountMovement(
            movementId,
            tenantId,
            ledgerId,
            contractId,
            partyId,
            type,
            amount,
            currencyCode,
            effectiveDate,
            sourceType,
            sourceId,
            journalEntryId
    );
}
```

This preserves the historical-fact semantics.

---

# 5. But there is a transaction-order problem

There is an important issue here.

If we do:

```text
movement INSERT
    ↓
journal POST
    ↓
movement UPDATE with journal ID
```

we temporarily have:

```text
movement exists
journal exists
movement linked
```

which is okay **only if all three operations are inside one database transaction**.

If the transaction fails:

```text
ROLLBACK
```

should remove all of them.

That gives us the invariant:

```text
movement exists
        ⇔
accounting journal exists and is linked
```

for accounting-backed movements.

---

# 6. Therefore distinguish two movement states

I would now add:

```java
public enum PartnerMovementStatus {
    PENDING_ACCOUNTING,
    POSTED,
    CANCELLED
}
```

But this requires one architectural decision:

### Business movement

Some movements may legitimately exist before accounting:

```text
Business event
    ↓
PENDING_ACCOUNTING
    ↓
Accounting policy
    ↓
POSTED
```

### Accounting-required movement

For close-critical movements:

```text
business event
    ↓
atomic movement + journal
    ↓
POSTED
```

For `tutup buku`, we should only consume:

```text
POSTED
```

movements.

Never count `PENDING_ACCOUNTING`.

---

# 7. This gives us an important close invariant

Before Syirkah result allocation:

```text
all required business movements
        ↓
POSTED
        ↓
journal-linked
        ↓
eligible for close
```

If something remains:

```text
PENDING_ACCOUNTING
```

then close reconciliation reports:

```text
UNPOSTED_PARTNER_MOVEMENT
```

and the Syirkah close cannot be frozen.

This is much safer than silently calculating from incomplete partner balances.

---

# 8. Extend the journal source contract

The existing `PostJournalEntryCommand` already has:

```java
sourceType
sourceId
postedBy
```

so we should preserve that design. syirkah-framework(1)

But it also currently lacks explicit tenant/ledger context, while the existing `GeneralLedgerService` constructs a `JournalEntry` through a constructor that uses default tenant/ledger behavior. syirkah-framework(1)

That should be corrected before this service becomes production-grade.

The command should become:

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
    // validation
}
```

Then the GL service must construct the journal using:

```java
TenantId.of(command.tenantId())
LedgerId.of(command.ledgerId())
```

rather than:

```java
TenantId.defaultTenant()
LedgerId.primary()
```

That is especially important for Syirkah because the movement itself is explicitly tenant/ledger scoped.

---

# 9. The GL source uniqueness constraint

At the journal level:

```sql
UNIQUE (
    tenant_id,
    ledger_id,
    source_type,
    source_id
);
```

At the movement level:

```sql
UNIQUE (
    tenant_id,
    ledger_id,
    source_type,
    source_id,
    party_id,
    movement_type
);
```

This gives us two protections.

### Journal

One source operation cannot create two journals.

### Movement

One source operation cannot create duplicate partner movements.

---

# 10. Failure semantics

This should now be explicit.

### Accounting mapping fails

```text
No movement
No journal
```

### Account does not exist

```text
No movement
No journal
```

### Journal validation fails

```text
No movement
No journal
```

### Journal posting fails

```text
No committed movement
No committed journal
```

### Movement duplicate

```text
Return existing movement
```

provided all immutable business attributes match.

### Same source, different amount/party/type

```text
FAIL
```

Never silently overwrite.

---

# 11. Reversal is also important

When an accounting journal is reversed, we should **not delete the original partner movement**.

Instead:

```text
Original movement
        │
        ▼
Original journal
        │
        ▼
Reversal journal
        │
        ▼
Reversal movement
```

For example:

```text
CAPITAL_CONTRIBUTION 100m
```

reversed becomes a new:

```text
CAPITAL_RETURN 100m
```

**only if the business semantics actually represent a capital return.**

A journal reversal by itself must not automatically invent a Syirkah business movement.

This distinction prevents accounting mechanics from silently changing the legal/economic meaning of the contract.

---

# 12. Then the close boundary becomes very strong

The eventual `tutup buku` calculation can require:

```text
                         ┌──────────────────┐
                         │ Period movements │
                         └────────┬─────────┘
                                  │
                         status = POSTED
                                  │
                                  ▼
                       ┌─────────────────────┐
                       │ Movement validation │
                       └─────────┬───────────┘
                                 │
                 ┌───────────────┴──────────────┐
                 │                              │
              errors                          valid
                 │                              │
                 ▼                              ▼
               STOP                    Partner positions
                                               │
                                               ▼
                                        Reconciliation
                                               │
                                               ▼
                                      Immutable snapshot
```

So the next concrete component after this is the **`SyirkahClosePositionCalculator`**.

Its responsibility should be narrow:

```java
public interface SyirkahClosePositionCalculator {

    Uni<SyirkahClosePositionResult> calculate(
            SyirkahClosePositionRequest request);
}
```

It should **not** calculate profit itself, **not** post journals, and **not** approve the close.

It consumes:

- locked opening snapshot,
- posted partner movements,
- Syirkah calculation/allocation result,

and produces the partner closing positions plus reconciliation.

That cleanly finishes the `tutup buku` calculation boundary before we move on to **immutable snapshot freezing and close-cycle task completion**.


////////////////


Yes. The next layer should be the **Syirkah close-position calculator**.

One important correction from reviewing the framework: the existing codebase uses both `Uni` and `CompletionStage` in different modules, so for the Syirkah accounting capability we should preserve the existing `Uni` style already used by the Syirkah close work rather than introduce another async abstraction.

### 1. Responsibility

`SyirkahClosePositionCalculator` should **not** calculate accounting profit. That already belongs to the financial-result/calculation layer.

Its responsibility is:

```text
Opening locked position
        +
Posted partner movements during period
        +
Syirkah allocation result
        ↓
Partner closing positions
        ↓
Position reconciliation
```

In particular:

- opening capital/current account comes from the **previous locked snapshot**
- only `POSTED` partner movements participate
- profit/loss allocations come from the Syirkah calculation
- capital return and capital impairment affect capital
- profit/loss allocation and withdrawals affect current account
- cash settlement remains separately reported
- the calculator must fail if the roll-forward does not reconcile

### 2. Core API

I would add these to the Syirkah application/domain area:

```java
public interface SyirkahClosePositionCalculator {

    Uni<SyirkahClosePositionResult> calculate(
            SyirkahClosePositionRequest request);
}
```

```java
public record SyirkahClosePositionRequest(
        String tenantId,
        String ledgerId,
        String contractId,
        String closeCycleId,
        String calculationId,
        LocalDate periodStart,
        LocalDate periodEnd
) {
    public SyirkahClosePositionRequest {
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        requireText(contractId, "contractId");
        requireText(closeCycleId, "closeCycleId");
        requireText(calculationId, "calculationId");

        Objects.requireNonNull(periodStart, "periodStart cannot be null");
        Objects.requireNonNull(periodEnd, "periodEnd cannot be null");

        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException(
                    "periodEnd cannot be before periodStart");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
    }
}
```

### 3. Result

The result should contain both the individual positions and the aggregate reconciliation:

```java
public record SyirkahClosePositionResult(
        String contractId,
        String closeCycleId,
        String calculationId,
        List<PartnerClosePositionSnapshot> positions,
        SyirkahPositionReconciliation reconciliation
) {
    public SyirkahClosePositionResult {
        Objects.requireNonNull(contractId, "contractId cannot be null");
        Objects.requireNonNull(closeCycleId, "closeCycleId cannot be null");
        Objects.requireNonNull(calculationId, "calculationId cannot be null");
        positions = List.copyOf(
                Objects.requireNonNull(positions, "positions cannot be null"));
        Objects.requireNonNull(
                reconciliation,
                "reconciliation cannot be null");

        if (!reconciliation.balanced()) {
            throw new IllegalArgumentException(
                    "Syirkah close position reconciliation is not balanced");
        }
    }
}
```

The existing `PartnerClosePositionSnapshot` remains the correct shape:

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
) {}
```

### 4. Do not use `PartnerAccount` as the source

This is important.

The calculator should read:

```text
Previous locked close snapshot
            +
Immutable PartnerAccountMovement history
            +
Current Syirkah calculation
```

not:

```text
PartnerAccount current balance
```

`PartnerAccount` is a projection/materialized current state. A close calculation must be reproducible historically.

The movement repository therefore remains:

```java
public interface PartnerAccountMovementRepository {

    Uni<Optional<PartnerAccountMovement>> findById(
            String tenantId,
            String ledgerId,
            String movementId);

    Uni<List<PartnerAccountMovement>> findByContractAndPeriod(
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate from,
            LocalDate to);

    Uni<List<PartnerAccountMovement>> findBySource(
            String tenantId,
            String ledgerId,
            String sourceType,
            String sourceId);

    Uni<Void> save(PartnerAccountMovement movement);
}
```

The calculator should explicitly ignore:

```java
PartnerMovementStatus.PENDING_ACCOUNTING
PartnerMovementStatus.CANCELLED
```

and use only:

```java
PartnerMovementStatus.POSTED
```

### 5. Movement semantics

The calculator should aggregate movement types rather than infer meaning from journal entries.

```java
switch (movement.movementType()) {
    case CAPITAL_CONTRIBUTION ->
            capitalContributions.add(amount);

    case CAPITAL_RETURN ->
            capitalReturns.add(amount);

    case CAPITAL_IMPAIRMENT ->
            capitalImpairments.add(amount);

    case PROFIT_ALLOCATION ->
            profitAllocations.add(amount);

    case LOSS_ALLOCATION ->
            lossAllocations.add(amount);

    case PARTNER_WITHDRAWAL ->
            withdrawals.add(amount);

    case CASH_SETTLEMENT ->
            cashSettlements.add(amount);
}
```

This keeps the Sharia/business meaning explicit.

### 6. The two roll-forward equations

For every partner:

```text
closing capital =
    opening capital
    + contributions
    - returns
    - impairments
```

and:

```text
closing current account =
    opening current account
    + profit allocations
    - loss allocations
    - withdrawals
```

Cash settlement is deliberately outside both equations.

So a partner could have:

```text
Profit allocation       10,000,000
Cash settlement           4,000,000
Outstanding allocation    6,000,000
```

The `10,000,000` belongs to the current-account calculation; the `4,000,000` is separately reported as cash settlement.

### 7. Allocation reconciliation

The calculator also needs to compare the movement-derived totals against the Syirkah calculation.

For example, if the calculation says:

```text
Net profit             5,000,000
Partner A allocation   2,000,000
Partner B allocation   3,000,000
```

then:

```text
sum partner profit allocations = 5,000,000
```

must hold.

Likewise for loss:

```text
sum partner loss allocations = calculated loss allocation
```

This catches situations where:

- calculation was generated but movements were only partially posted
- a movement was posted twice
- a movement has the wrong party
- a movement amount was changed
- one partner allocation was omitted

### 8. Reconciliation object

I would extend the existing concept to something like:

```java
public record SyirkahPositionReconciliation(
        boolean balanced,
        BigDecimal totalProfitAllocated,
        BigDecimal totalLossAllocated,
        BigDecimal totalCapitalContributed,
        BigDecimal totalCapitalReturned,
        BigDecimal totalCapitalImpaired,
        BigDecimal expectedProfitAllocation,
        BigDecimal expectedLossAllocation,
        List<String> errors
) {
    public SyirkahPositionReconciliation {
        errors = List.copyOf(
                Objects.requireNonNull(errors, "errors cannot be null"));
    }

    public static SyirkahPositionReconciliation balanced(
            BigDecimal totalProfitAllocated,
            BigDecimal totalLossAllocated,
            BigDecimal totalCapitalContributed,
            BigDecimal totalCapitalReturned,
            BigDecimal totalCapitalImpaired,
            BigDecimal expectedProfitAllocation,
            BigDecimal expectedLossAllocation) {

        return new SyirkahPositionReconciliation(
                true,
                totalProfitAllocated,
                totalLossAllocated,
                totalCapitalContributed,
                totalCapitalReturned,
                totalCapitalImpaired,
                expectedProfitAllocation,
                expectedLossAllocation,
                List.of());
    }
}
```

I would **not** silently round reconciliation differences away. Monetary calculations can have defined rounding, but an unexplained residual should be visible.

### 9. Calculator implementation flow

The application service becomes roughly:

```java
@ApplicationScoped
public class SyirkahClosePositionCalculatorImpl
        implements SyirkahClosePositionCalculator {

    private final SyirkahOpeningPositionProvider openingPositionProvider;
    private final PartnerAccountMovementRepository movementRepository;
    private final SyirkahCalculationRepository calculationRepository;

    public SyirkahClosePositionCalculatorImpl(
            SyirkahOpeningPositionProvider openingPositionProvider,
            PartnerAccountMovementRepository movementRepository,
            SyirkahCalculationRepository calculationRepository) {

        this.openingPositionProvider =
                Objects.requireNonNull(openingPositionProvider);
        this.movementRepository =
                Objects.requireNonNull(movementRepository);
        this.calculationRepository =
                Objects.requireNonNull(calculationRepository);
    }

    @Override
    public Uni<SyirkahClosePositionResult> calculate(
            SyirkahClosePositionRequest request) {

        return calculationRepository
                .findByCalculationId(
                        request.tenantId(),
                        request.ledgerId(),
                        request.calculationId())
                .chain(calculation ->
                        movementRepository.findByContractAndPeriod(
                                request.tenantId(),
                                request.ledgerId(),
                                request.contractId(),
                                request.periodStart(),
                                request.periodEnd())
                        .chain(movements ->
                                calculatePositions(
                                        request,
                                        calculation,
                                        movements)));
    }

    private Uni<SyirkahClosePositionResult> calculatePositions(
            SyirkahClosePositionRequest request,
            SyirkahCalculation calculation,
            List<PartnerAccountMovement> movements) {

        // Build partner positions from the locked opening snapshot,
        // then apply POSTED movements and validate against the
        // Syirkah calculation.

        return Uni.createFrom().item(
                buildResult(request, calculation, movements));
    }

    private SyirkahClosePositionResult buildResult(
            SyirkahClosePositionRequest request,
            SyirkahCalculation calculation,
            List<PartnerAccountMovement> movements) {

        // Implementation continues with partner aggregation
        // and reconciliation.
        throw new UnsupportedOperationException("Not implemented");
    }
}
```

One thing I would change before writing the final implementation: the existing `SyirkahCloseCalculationRepository` from the previous layer stores `SyirkahCloseProcessor.CloseCalculation`, so the calculator should consume **that exact calculation object**, rather than introduce another competing `SyirkahCalculationRepository` unless the framework actually has one. The framework search did not establish such a repository, so we should not invent it.

### 10. Final close dependency graph

This gives us a much cleaner architecture:

```text
                Journal Entries
                     │
                     ▼
        FinancialResultProvider
                     │
                     ▼
          SyirkahResultCalculator
                     │
                     ▼
             CloseCalculation
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
   Profit/Loss Allocation   Partner movements
          │                     │
          └──────────┬──────────┘
                     ▼
       SyirkahClosePositionCalculator
                     │
                     ▼
          Position Reconciliation
                     │
                     ▼
        Immutable Close Snapshot
                     │
                     ▼
              CloseCycle
                     │
                     ▼
                  LOCKED
```

This also gives us the correct audit story:

> **The financial result says what happened to the business; the allocation says how that result belongs to the partners; the movements say what was actually recorded to each partner position; the snapshot freezes the resulting position at close.**

The next implementation step should therefore be the **immutable close snapshot aggregate + repository**, because once the position calculator exists, the snapshot becomes the authoritative opening position for the following period and for Syirkah transition/termination workflows.

//////////////

Continuing: the next component is the **immutable `SyirkahCloseSnapshot`**. I checked the framework again; there is no existing concrete `SyirkahCloseSnapshot` implementation exposed in the source, so this is an extension rather than a replacement.

## 1. Snapshot aggregate

I recommend putting it under:

```text
finance/accounting/syirkah-accounting-domain/
└── src/main/java/tech/kayys/syirkah/accounting/domain/syirkah/close/
```

The snapshot should freeze the complete Syirkah position at a successful close.

```java
package tech.kayys.syirkah.accounting.domain.syirkah.close;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
    private final SyirkahPositionReconciliation reconciliation;

    private final Instant frozenAt;
    private final String frozenBy;

    private SyirkahCloseSnapshot(
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
            SyirkahPositionReconciliation reconciliation,
            Instant frozenAt,
            String frozenBy) {

        this.snapshotId = requireText(snapshotId, "snapshotId");
        this.tenantId = requireText(tenantId, "tenantId");
        this.ledgerId = requireText(ledgerId, "ledgerId");
        this.closeCycleId = requireText(closeCycleId, "closeCycleId");
        this.calculationId = requireText(calculationId, "calculationId");
        this.contractId = requireText(contractId, "contractId");

        if (contractVersion <= 0) {
            throw new IllegalArgumentException(
                    "contractVersion must be positive");
        }

        this.contractVersion = contractVersion;

        this.periodStart =
                Objects.requireNonNull(periodStart, "periodStart cannot be null");
        this.periodEnd =
                Objects.requireNonNull(periodEnd, "periodEnd cannot be null");

        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException(
                    "periodEnd cannot be before periodStart");
        }

        this.revenue = nonNegative(revenue, "revenue");
        this.directCosts = nonNegative(directCosts, "directCosts");
        this.operatingCosts = nonNegative(
                operatingCosts,
                "operatingCosts");
        this.otherCosts = nonNegative(otherCosts, "otherCosts");
        this.netResult =
                Objects.requireNonNull(netResult, "netResult cannot be null");

        this.resultType =
                Objects.requireNonNull(resultType, "resultType cannot be null");

        this.partnerPositions = List.copyOf(
                Objects.requireNonNull(
                        partnerPositions,
                        "partnerPositions cannot be null"));

        if (this.partnerPositions.isEmpty()) {
            throw new IllegalArgumentException(
                    "partnerPositions cannot be empty");
        }

        this.reconciliation =
                Objects.requireNonNull(
                        reconciliation,
                        "reconciliation cannot be null");

        if (!reconciliation.balanced()) {
            throw new IllegalArgumentException(
                    "Cannot freeze an unbalanced Syirkah position");
        }

        this.frozenAt =
                Objects.requireNonNull(frozenAt, "frozenAt cannot be null");

        this.frozenBy = requireText(frozenBy, "frozenBy");
    }

    public static SyirkahCloseSnapshot freeze(
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
            SyirkahPositionReconciliation reconciliation,
            String frozenBy) {

        return new SyirkahCloseSnapshot(
                UUID.randomUUID().toString(),
                tenantId,
                ledgerId,
                closeCycleId,
                calculationId,
                contractId,
                contractVersion,
                periodStart,
                periodEnd,
                revenue,
                directCosts,
                operatingCosts,
                otherCosts,
                netResult,
                resultType,
                partnerPositions,
                reconciliation,
                Instant.now(),
                frozenBy);
    }

    public enum ResultType {
        PROFIT,
        LOSS,
        BREAK_EVEN
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank");
        }
        return value;
    }

    private static BigDecimal nonNegative(
            BigDecimal value,
            String field) {

        Objects.requireNonNull(value, field + " cannot be null");

        if (value.signum() < 0) {
            throw new IllegalArgumentException(
                    field + " cannot be negative");
        }

        return value;
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

    public BigDecimal revenue() {
        return revenue;
    }

    public BigDecimal directCosts() {
        return directCosts;
    }

    public BigDecimal operatingCosts() {
        return operatingCosts;
    }

    public BigDecimal otherCosts() {
        return otherCosts;
    }

    public BigDecimal netResult() {
        return netResult;
    }

    public ResultType resultType() {
        return resultType;
    }

    public List<PartnerClosePositionSnapshot> partnerPositions() {
        return partnerPositions;
    }

    public SyirkahPositionReconciliation reconciliation() {
        return reconciliation;
    }

    public Instant frozenAt() {
        return frozenAt;
    }

    public String frozenBy() {
        return frozenBy;
    }
}
```

The important property is that there is **no mutation API** after `freeze()`.

---

# 2. Do not freeze before the close is actually valid

The snapshot should not merely mean "calculation completed."

The invariant should be:

```text
CloseCalculation
       +
PartnerPositionCalculation
       +
Reconciliation = BALANCED
       ↓
SyirkahCloseSnapshot.freeze()
       ↓
CloseCycle can complete mandatory Syirkah tasks
       ↓
CloseCycle APPROVED
       ↓
CloseCycle LOCKED
```

So:

```text
CALCULATED ≠ FROZEN
```

This distinction matters for retries and maker/checker workflows.

---

# 3. Snapshot repository

The repository should be tenant and ledger scoped.

```java
package tech.kayys.syirkah.accounting.application.syirkah.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.syirkah.close.SyirkahCloseSnapshot;

import java.util.Optional;

public interface SyirkahCloseSnapshotRepository {

    Uni<Optional<SyirkahCloseSnapshot>> findById(
            String tenantId,
            String ledgerId,
            String snapshotId);

    Uni<Optional<SyirkahCloseSnapshot>> findByCloseCycleId(
            String tenantId,
            String ledgerId,
            String closeCycleId);

    Uni<Optional<SyirkahCloseSnapshot>> findByCalculationId(
            String tenantId,
            String ledgerId,
            String calculationId);

    Uni<Void> save(SyirkahCloseSnapshot snapshot);
}
```

The `save()` implementation must be append-only from the domain's perspective.

There should be **no**:

```java
update(snapshot)
delete(snapshot)
```

API.

---

# 4. Database invariants

The database should enforce the idempotency boundary:

```sql
create unique index uq_syirkah_close_snapshot_cycle
    on syirkah_close_snapshot (
        tenant_id,
        ledger_id,
        close_cycle_id
    );
```

And:

```sql
create unique index uq_syirkah_close_snapshot_calculation
    on syirkah_close_snapshot (
        tenant_id,
        ledger_id,
        calculation_id
    );
```

This protects against two application instances freezing the same close concurrently.

The table should additionally contain:

```text
snapshot_id
tenant_id
ledger_id
close_cycle_id
calculation_id

contract_id
contract_version

period_start
period_end

revenue
direct_costs
operating_costs
other_costs
net_result
result_type

frozen_at
frozen_by
```

Partner positions should preferably be stored separately:

```text
syirkah_close_snapshot
        │
        └── syirkah_close_partner_position
```

rather than serializing the entire list into one JSON column.

That gives us queryability for:

- partner statements
- audit
- transition workflows
- partner exit
- capital return
- retained profit
- regulatory reporting.

---

# 5. Opening position provider

Now the earlier interface becomes concrete:

```java
package tech.kayys.syirkah.accounting.application.syirkah.port;

import io.smallrye.mutiny.Uni;

import java.util.Optional;

public interface SyirkahOpeningPositionProvider {

    Uni<Optional<PartnerOpeningPosition>> findOpeningPosition(
            String tenantId,
            String ledgerId,
            String contractId,
            String partyId);
}
```

But the implementation should **not** query `PartnerAccount`.

It should resolve:

```text
Current period
    ↓
previous locked SyirkahCloseSnapshot
    ↓
PartnerClosePositionSnapshot
    ↓
opening position
```

For example:

```java
public record PartnerOpeningPosition(
        String partyId,
        BigDecimal openingCapital,
        BigDecimal openingCurrentAccount
) {
    public PartnerOpeningPosition {
        if (partyId == null || partyId.isBlank()) {
            throw new IllegalArgumentException(
                    "partyId cannot be blank");
        }

        Objects.requireNonNull(
                openingCapital,
                "openingCapital cannot be null");

        Objects.requireNonNull(
                openingCurrentAccount,
                "openingCurrentAccount cannot be null");
    }
}
```

---

# 6. First period

There is one important edge case.

There may be **no previous Syirkah snapshot** for the first period.

That should not be treated as an error automatically.

The contract's initial capital contributions become the opening position according to the contract's effective opening rules.

So the provider can have a second source:

```text
previous locked snapshot
        │
        ├── found → use it
        │
        └── absent
              ↓
       initial contract position
```

I would keep this behind the same provider rather than leaking first-period logic into the calculator.

---

# 7. Snapshot service

The application service should make snapshot creation idempotent:

```java
public Uni<SyirkahCloseSnapshot> freeze(
        SyirkahCloseSnapshotRequest request) {

    return repository.findByCloseCycleId(
            request.tenantId(),
            request.ledgerId(),
            request.closeCycleId())
        .chain(existing -> {

            if (existing.isPresent()) {
                return Uni.createFrom()
                        .item(existing.get());
            }

            return calculateAndFreeze(request);
        });
}
```

But there is an important concurrency caveat:

```text
find
 ↓
not found
 ↓
calculate
 ↓
insert
```

is not enough by itself.

Two workers can both reach `not found`.

Therefore the **unique database constraint is authoritative**. If both attempt insertion, one wins and the other must reload the existing snapshot after the unique-key conflict.

---

# 8. Snapshot creation should happen only once

The lifecycle becomes:

```text
OPEN
 │
 ├── ordinary close tasks
 │
 ├── SYIRKAH_RESULT
 │       ↓
 │   calculation
 │
 ├── SYIRKAH_ALLOCATION
 │       ↓
 │   partner allocation
 │
 ├── position calculation
 │       ↓
 │   reconciliation
 │
 ├── snapshot freeze
 │
 ├── SYIRKAH_DISTRIBUTION
 │       ↓
 │   approved + posted
 │
 ├── validation
 │
 ├── approval
 │
 └── LOCKED
```

I would therefore **not** make snapshot freezing synonymous with `TASK_SYIRKAH_DISTRIBUTION`.

The snapshot represents the **business position at close**, while distribution posting represents an **accounting action**.

Those are related but different concepts.

---

# 9. Very important: distribution after snapshot

This also gives us a clean treatment for:

```text
Profit allocation = 10m
Cash paid          = 4m
Outstanding        = 6m
```

The frozen snapshot can contain:

```text
profitAllocations = 10m
cashSettlements   = 4m
closingCurrentAccount reflects the allocation
```

The cash payment does not retroactively alter the original financial result.

If a later settlement occurs:

```text
Close snapshot
     ↓
10m allocation
     ↓
4m settled
     ↓
6m outstanding
```

we can report the outstanding obligation without rewriting the historical close.

---

# 10. This becomes the foundation for transitions

Now the previously discussed transition workflow becomes much stronger:

```text
Existing Syirkah
       │
       ▼
Locked Close Snapshot
       │
       ├── partner positions
       ├── capital
       ├── current accounts
       ├── allocations
       ├── loss responsibility
       └── reconciliation
       │
       ▼
Transition
       │
       ├── Partner Exit
       ├── Partner Entry
       ├── Capital Change
       ├── Replacement
       └── Full Termination
       │
       ▼
New Contract
```

For example, an exiting partner's transition position comes from the **frozen close snapshot**, not whatever their current `PartnerAccount` happens to contain months later.

That is exactly what we need for auditability.

---

## Next implementation order

I would now proceed in this order:

```text
1. SyirkahCloseSnapshot
2. SyirkahCloseSnapshotRepository
3. Snapshot persistence adapter
4. Snapshot freeze application service
5. Wire snapshot into CloseCycle
6. Implement SyirkahOpeningPositionProvider from snapshots
7. Add transition/termination workflow
```

And one architectural point is now firm:

> **The locked Syirkah close snapshot becomes the historical source of truth for partner positions. `PartnerAccount` remains a rebuildable operational projection, not the authoritative opening balance for a subsequent Syirkah period.**

This prevents a later projection rebuild, correction, or operational mutation from changing the historical basis of a closed Syirkah period.


/////////////

The next layer is the **Syirkah transition workflow**. The uploaded framework already contains the broader Syirkah capability structure, while the muamalah source supports the important business rule that an existing Syirkah position must be determined before a new partner/capital structure replaces it. I’ll keep that rule outside generic accounting mechanics.

## 1. Transition is not another close cycle

A transition should sit **after a locked close snapshot**:

```text
Normal period
    ↓
Close calculation
    ↓
Position reconciliation
    ↓
Close snapshot
    ↓
CloseCycle LOCKED
    ↓
Syirkah transition
    ↓
Settlement / partner change
    ↓
New contract
```

This prevents us from making `CloseCycle` responsible for every possible partner-entry/exit scenario.

---

# 2. Transition types

Keep the existing proposed vocabulary:

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

And reasons:

```java
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

These are intentionally separate.

For example:

```text
PARTNER_EXIT
    + PARTNER_WITHDRAWAL
```

versus:

```text
PARTNER_EXIT
    + CONTRACT_BREACH
```

The accounting consequences can be very different.

---

# 3. Transition aggregate

```java
public final class SyirkahTransition {

    private final String transitionId;
    private final String tenantId;
    private final String ledgerId;

    private final String previousContractId;
    private final int previousContractVersion;

    private final SyirkahTransitionType type;
    private final SyirkahTransitionReason reason;
    private final LocalDate transitionDate;

    private TransitionStatus status;

    private String closeCycleId;
    private String snapshotId;
    private String nextContractId;

    private SyirkahTransition(
            String transitionId,
            String tenantId,
            String ledgerId,
            String previousContractId,
            int previousContractVersion,
            SyirkahTransitionType type,
            SyirkahTransitionReason reason,
            LocalDate transitionDate) {

        this.transitionId = requireText(
                transitionId,
                "transitionId");

        this.tenantId = requireText(
                tenantId,
                "tenantId");

        this.ledgerId = requireText(
                ledgerId,
                "ledgerId");

        this.previousContractId = requireText(
                previousContractId,
                "previousContractId");

        if (previousContractVersion <= 0) {
            throw new IllegalArgumentException(
                    "previousContractVersion must be positive");
        }

        this.previousContractVersion = previousContractVersion;

        this.type = Objects.requireNonNull(
                type,
                "type cannot be null");

        this.reason = Objects.requireNonNull(
                reason,
                "reason cannot be null");

        this.transitionDate = Objects.requireNonNull(
                transitionDate,
                "transitionDate cannot be null");

        this.status = TransitionStatus.DRAFT;
    }

    public static SyirkahTransition request(
            String transitionId,
            String tenantId,
            String ledgerId,
            String previousContractId,
            int previousContractVersion,
            SyirkahTransitionType type,
            SyirkahTransitionReason reason,
            LocalDate transitionDate) {

        return new SyirkahTransition(
                transitionId,
                tenantId,
                ledgerId,
                previousContractId,
                previousContractVersion,
                type,
                reason,
                transitionDate);
    }

    public void requireClose(String closeCycleId) {
        requireState(TransitionStatus.DRAFT);

        this.closeCycleId = requireText(
                closeCycleId,
                "closeCycleId");

        this.status = TransitionStatus.CLOSING_REQUIRED;
    }

    public void freezeSnapshot(String snapshotId) {
        requireState(TransitionStatus.CLOSING_REQUIRED);

        this.snapshotId = requireText(
                snapshotId,
                "snapshotId");

        this.status = TransitionStatus.SNAPSHOT_FROZEN;
    }

    public void requireSettlement() {
        requireState(TransitionStatus.SNAPSHOT_FROZEN);

        this.status = TransitionStatus.SETTLEMENT_REQUIRED;
    }

    public void attachNextContract(String nextContractId) {
        if (status != TransitionStatus.SETTLEMENT_REQUIRED
                && status != TransitionStatus.SNAPSHOT_FROZEN) {
            throw new IllegalStateException(
                    "Cannot attach next contract while transition is "
                            + status);
        }

        this.nextContractId = requireText(
                nextContractId,
                "nextContractId");

        this.status = TransitionStatus.CONTRACT_PENDING;
    }

    public void complete() {
        if (status != TransitionStatus.CONTRACT_PENDING) {
            throw new IllegalStateException(
                    "Transition cannot be completed from " + status);
        }

        if (snapshotId == null) {
            throw new IllegalStateException(
                    "A frozen snapshot is required");
        }

        if (nextContractId == null
                && type != SyirkahTransitionType.FULL_TERMINATION) {
            throw new IllegalStateException(
                    "A next contract is required");
        }

        this.status = TransitionStatus.COMPLETED;
    }

    public void cancel() {
        if (status == TransitionStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Completed transition cannot be cancelled");
        }

        this.status = TransitionStatus.CANCELLED;
    }

    private void requireState(TransitionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Expected " + expected + " but was " + status);
        }
    }

    private static String requireText(
            String value,
            String field) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank");
        }

        return value;
    }

    // getters...
}
```

Status:

```java
public enum TransitionStatus {
    DRAFT,
    CLOSING_REQUIRED,
    SNAPSHOT_FROZEN,
    SETTLEMENT_REQUIRED,
    CONTRACT_PENDING,
    COMPLETED,
    CANCELLED
}
```

---

# 4. A key correction: settlement is conditional

Not every transition requires monetary settlement.

For example:

```text
CONTRACT_RENEWAL
```

might simply freeze the previous position and establish a new version.

Whereas:

```text
PARTNER_EXIT
```

may require:

```text
closing position
    ↓
capital/current-account determination
    ↓
settlement
    ↓
partner leaves
```

And:

```text
FULL_TERMINATION
```

requires liquidation.

Therefore `requireSettlement()` should be invoked by the transition application service **only when the transition policy says settlement is required**.

Do not encode every possible Sharia/business rule into the aggregate.

---

# 5. Transition instructions

The aggregate needs a separate representation of what happens to each partner:

```java
public record PartnerTransitionInstruction(
        String partyId,
        Action action,
        BigDecimal capitalAtTransition,
        BigDecimal currentAccountAtTransition,
        boolean retainedInNextContract
) {

    public PartnerTransitionInstruction {
        if (partyId == null || partyId.isBlank()) {
            throw new IllegalArgumentException(
                    "partyId cannot be blank");
        }

        Objects.requireNonNull(
                capitalAtTransition,
                "capitalAtTransition cannot be null");

        Objects.requireNonNull(
                currentAccountAtTransition,
                "currentAccountAtTransition cannot be null");

        if (capitalAtTransition.signum() < 0) {
            throw new IllegalArgumentException(
                    "capitalAtTransition cannot be negative");
        }
    }

    public enum Action {
        RETAIN,
        EXIT,
        ENTER,
        REPLACE
    }
}
```

The values should come from the frozen snapshot, not be manually typed by an operator.

---

# 6. Result disposition

This is particularly important for retained earnings/profit.

```java
public enum ResultDisposition {
    DISTRIBUTE,
    RETAIN_IN_BUSINESS
}
```

And:

```java
public record SyirkahResultDisposition(
        String calculationId,
        ResultDisposition disposition,
        String approvedBy
) {
    public SyirkahResultDisposition {
        if (calculationId == null || calculationId.isBlank()) {
            throw new IllegalArgumentException(
                    "calculationId cannot be blank");
        }

        Objects.requireNonNull(
                disposition,
                "disposition cannot be null");

        if (approvedBy == null || approvedBy.isBlank()) {
            throw new IllegalArgumentException(
                    "approvedBy cannot be blank");
        }
    }
}
```

The important invariant is:

```text
PROFIT
  ↓
RETAIN_IN_BUSINESS
  ≠
automatically add profit to partner capital
```

Retained business funds and partner capital are different concepts.

If a subsequent contract explicitly converts retained funds into capital, that is a new contractual/accounting event.

---

# 7. Next-contract opening position

That distinction can be represented explicitly:

```java
public record NextContractOpeningPosition(
        String partyId,
        BigDecimal capitalContribution,
        BigDecimal retainedBusinessBalance
) {
    public NextContractOpeningPosition {
        if (partyId == null || partyId.isBlank()) {
            throw new IllegalArgumentException(
                    "partyId cannot be blank");
        }

        Objects.requireNonNull(
                capitalContribution,
                "capitalContribution cannot be null");

        Objects.requireNonNull(
                retainedBusinessBalance,
                "retainedBusinessBalance cannot be null");

        if (capitalContribution.signum() < 0
                || retainedBusinessBalance.signum() < 0) {
            throw new IllegalArgumentException(
                    "Opening amounts cannot be negative");
        }
    }
}
```

This avoids the dangerous shortcut:

```text
closing current account
        ↓
new capital
```

without an explicit business decision.

---

# 8. Repository

```java
public interface SyirkahTransitionRepository {

    Uni<Optional<SyirkahTransition>> findById(
            String tenantId,
            String ledgerId,
            String transitionId);

    Uni<Optional<SyirkahTransition>> findActiveByContractId(
            String tenantId,
            String ledgerId,
            String contractId);

    Uni<Void> save(
            String tenantId,
            String ledgerId,
            SyirkahTransition transition);
}
```

Database invariants:

```sql
unique (
    tenant_id,
    ledger_id,
    transition_id
)
```

And the contract should not have two simultaneously active transitions.

Conceptually:

```sql
unique (
    tenant_id,
    ledger_id,
    previous_contract_id,
    active_transition
)
```

The exact SQL implementation can depend on the existing persistence technology.

---

# 9. Transition service

The application boundary becomes:

```java
public interface SyirkahTransitionService {

    Uni<SyirkahTransition> requestTransition(
            TransitionRequest request);

    Uni<SyirkahTransition> attachCloseCycle(
            String transitionId,
            String closeCycleId);

    Uni<SyirkahTransition> freezeSnapshot(
            String transitionId);

    Uni<SyirkahTransition> settleOldPosition(
            String transitionId);

    Uni<SyirkahTransition> attachNextContract(
            String transitionId,
            String nextContractId);

    Uni<SyirkahTransition> complete(
            String transitionId);
}
```

The important point is that these are **explicit operations**.

There should be no:

```java
transitionEverythingAutomatically();
```

because partner exit, entry, replacement, capital changes and termination have materially different accounting and contractual consequences.

---

# 10. Full termination is a different branch

For:

```text
FULL_TERMINATION
```

the workflow should become:

```text
Active Syirkah
     ↓
Close period
     ↓
Freeze position
     ↓
Liquidation
     ↓
Realize assets
     ↓
Collect receivables
     ↓
Settle liabilities
     ↓
Determine net realization
     ↓
Determine profit/loss
     ↓
Allocate result
     ↓
Settle partner obligations
     ↓
Return remaining capital
     ↓
Terminate
```

So we should **not** force liquidation into the normal `SyirkahCloseSnapshot`.

Instead, introduce a separate:

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

The close snapshot gives us the starting point for liquidation; liquidation then creates its own final settlement state.

---

# 11. Partner replacement

Replacement also needs a separate concept.

Suppose:

```text
Partner A exits
Partner B enters
```

We should not model that as simply:

```text
A.partyId → B.partyId
```

because that destroys history.

Instead:

```text
Partner A
    ↓
exit/transfer settlement
    ↓
Partner B
    ↓
new contractual participation
```

For an interest transfer, preserve:

```java
public record PartnerInterestTransfer(
        String transferId,
        String outgoingPartyId,
        String incomingPartyId,
        BigDecimal carryingValue,
        BigDecimal agreedTransferPrice,
        BigDecimal premiumOrDiscount
) {

    public static PartnerInterestTransfer calculate(
            String transferId,
            String outgoingPartyId,
            String incomingPartyId,
            BigDecimal carryingValue,
            BigDecimal agreedTransferPrice) {

        BigDecimal premiumOrDiscount =
                agreedTransferPrice.subtract(carryingValue);

        return new PartnerInterestTransfer(
                transferId,
                outgoingPartyId,
                incomingPartyId,
                carryingValue,
                agreedTransferPrice,
                premiumOrDiscount);
    }
}
```

This preserves the crucial distinction:

```text
carrying value
     ≠
transfer price
     ≠
new partner capital contribution
```

unless the applicable business/accounting policy explicitly says they are equal.

---

# 12. Transfer restriction policy

Rules concerning whether a partner may transfer their interest should not be embedded in accounting:

```java
public interface PartnerTransferRestrictionPolicy {

    TransferDecision evaluate(
            String contractId,
            String outgoingPartyId,
            String incomingPartyId);
}
```

That gives us room for contract terms and the applicable Sharia/legal rules without contaminating the ledger model.

---

# 13. The resulting architecture

We now have a clean separation:

```text
                     SYIRKAH
                        │
          ┌─────────────┼─────────────┐
          │             │             │
      Accounting      Position     Contract
       Result         Snapshot     Transition
          │             │             │
          ▼             ▼             ▼
   Financial Result  Partner      Entry/Exit/
   + Allocation      Positions   Replacement
          │             │             │
          └─────────────┼─────────────┘
                        │
                  Settlement
                        │
             ┌──────────┴──────────┐
             ▼                     ▼
       Distribution          Capital Return
```

And full termination branches into:

```text
Transition
    ↓
Liquidation
    ↓
Realization
    ↓
Liability Settlement
    ↓
Final Result
    ↓
Distribution
    ↓
Capital Return
    ↓
Completed
```

This is the point where the Syirkah accounting extension starts becoming a proper **business lifecycle model**, rather than just a profit-sharing calculator.

The next piece should be **liquidation + final termination**, because that completes the other major state transition after partner entry/exit and lets us define the final capital-return semantics cleanly.

//////////////

The source gives us an important refinement: **a new partner or new capital requires closing the old Syirkah books first, then creating the new Syirkah with new bookkeeping**. For partner replacement, the source also describes consent of the existing partners and the right of the other partners to purchase first. Hukum-muamalah Hukum-muamalah

So the next implementation should make **liquidation/final settlement explicit**, rather than treating it as an ordinary distribution.

## 1. Liquidation aggregate

```java
public final class SyirkahLiquidation {

    private final String liquidationId;
    private final String tenantId;
    private final String ledgerId;
    private final String contractId;
    private final int contractVersion;
    private final String closeCycleId;

    private LiquidationStatus status;

    private BigDecimal grossRealization;
    private BigDecimal liabilitiesSettled;
    private BigDecimal netRealization;
    private BigDecimal profitOrLoss;

    private String completedBy;
    private Instant completedAt;

    private SyirkahLiquidation(
            String liquidationId,
            String tenantId,
            String ledgerId,
            String contractId,
            int contractVersion,
            String closeCycleId) {

        this.liquidationId = requireText(liquidationId, "liquidationId");
        this.tenantId = requireText(tenantId, "tenantId");
        this.ledgerId = requireText(ledgerId, "ledgerId");
        this.contractId = requireText(contractId, "contractId");

        if (contractVersion <= 0) {
            throw new IllegalArgumentException(
                    "contractVersion must be positive");
        }

        this.contractVersion = contractVersion;
        this.closeCycleId = requireText(closeCycleId, "closeCycleId");

        this.status = LiquidationStatus.NOT_STARTED;
        this.grossRealization = BigDecimal.ZERO;
        this.liabilitiesSettled = BigDecimal.ZERO;
        this.netRealization = BigDecimal.ZERO;
        this.profitOrLoss = BigDecimal.ZERO;
    }

    public static SyirkahLiquidation start(
            String liquidationId,
            String tenantId,
            String ledgerId,
            String contractId,
            int contractVersion,
            String closeCycleId) {

        return new SyirkahLiquidation(
                liquidationId,
                tenantId,
                ledgerId,
                contractId,
                contractVersion,
                closeCycleId);
    }

    public void beginAssetRealization() {
        requireStatus(LiquidationStatus.NOT_STARTED);
        status = LiquidationStatus.ASSET_REALIZATION;
    }

    public void recordRealization(BigDecimal amount) {
        requireStatus(LiquidationStatus.ASSET_REALIZATION);

        requireNonNegative(amount, "amount");

        grossRealization = grossRealization.add(amount);
    }

    public void completeAssetRealization() {
        requireStatus(LiquidationStatus.ASSET_REALIZATION);

        status = LiquidationStatus.LIABILITY_SETTLEMENT;
    }

    public void recordLiabilitySettlement(BigDecimal amount) {
        requireStatus(LiquidationStatus.LIABILITY_SETTLEMENT);

        requireNonNegative(amount, "amount");

        liabilitiesSettled = liabilitiesSettled.add(amount);

        if (liabilitiesSettled.compareTo(grossRealization) > 0) {
            throw new IllegalStateException(
                    "Liabilities cannot exceed realized assets");
        }
    }

    public void completeLiabilitySettlement() {
        requireStatus(LiquidationStatus.LIABILITY_SETTLEMENT);

        netRealization =
                grossRealization.subtract(liabilitiesSettled);

        status = LiquidationStatus.RECONCILIATION;
    }

    public void completeReconciliation() {
        requireStatus(LiquidationStatus.RECONCILIATION);

        status = LiquidationStatus.RESULT_DETERMINED;
    }

    public void determineResult(BigDecimal profitOrLoss) {
        requireStatus(LiquidationStatus.RESULT_DETERMINED);

        Objects.requireNonNull(
                profitOrLoss,
                "profitOrLoss cannot be null");

        this.profitOrLoss = profitOrLoss;

        status = LiquidationStatus.DISTRIBUTION;
    }

    public void completeDistribution() {
        requireStatus(LiquidationStatus.DISTRIBUTION);

        status = LiquidationStatus.CAPITAL_RETURN;
    }

    public void completeCapitalReturn(String completedBy) {
        requireStatus(LiquidationStatus.CAPITAL_RETURN);

        this.completedBy =
                requireText(completedBy, "completedBy");

        this.completedAt = Instant.now();
        this.status = LiquidationStatus.COMPLETED;
    }

    private void requireStatus(LiquidationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Expected " + expected + " but was " + status);
        }
    }

    private static void requireNonNegative(
            BigDecimal value,
            String field) {

        Objects.requireNonNull(value, field + " cannot be null");

        if (value.signum() < 0) {
            throw new IllegalArgumentException(
                    field + " cannot be negative");
        }
    }

    private static String requireText(
            String value,
            String field) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank");
        }

        return value;
    }
}
```

The status remains:

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

---

# 2. Do not calculate final partner return directly from gross assets

This is critical.

The source explicitly describes business assets as including:

- fixed/non-current assets
- movable assets
- receivables
- business cash

and says the assets to be distributed are those **after business debts are deducted**. Hukum-muamalah

Therefore:

```text
gross realization
        ↓
- liabilities
        ↓
net realization
        ↓
final result
        ↓
profit/loss treatment
        ↓
partner distribution
        ↓
capital return
```

Not:

```text
assets → partner capital
```

---

# 3. Separate liquidation realization from accounting journal

We should introduce a domain record:

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
        if (realizationId == null || realizationId.isBlank()) {
            throw new IllegalArgumentException(
                    "realizationId cannot be blank");
        }

        if (liquidationId == null || liquidationId.isBlank()) {
            throw new IllegalArgumentException(
                    "liquidationId cannot be blank");
        }

        Objects.requireNonNull(
                carryingValue,
                "carryingValue cannot be null");

        Objects.requireNonNull(
                realizedAmount,
                "realizedAmount cannot be null");

        if (carryingValue.signum() < 0
                || realizedAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Values cannot be negative");
        }
    }

    public BigDecimal realizationGainOrLoss() {
        return realizedAmount.subtract(carryingValue);
    }
}
```

Then accounting treatment remains policy-driven.

For example:

```text
asset carrying value     100m
realized for             90m
                         ----
liquidation loss         10m
```

That loss must flow into the appropriate Syirkah result mechanism; it should not disappear inside the asset journal.

---

# 4. Liability settlement must also be explicit

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
        Objects.requireNonNull(
                outstandingAmount,
                "outstandingAmount cannot be null");

        Objects.requireNonNull(
                settledAmount,
                "settledAmount cannot be null");

        if (outstandingAmount.signum() < 0
                || settledAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Liability amounts cannot be negative");
        }

        if (settledAmount.compareTo(outstandingAmount) > 0) {
            throw new IllegalArgumentException(
                    "Settled amount cannot exceed outstanding amount");
        }
    }
}
```

This gives us an auditable liquidation trail:

```text
Asset
 ├── carrying value
 ├── realization
 └── gain/loss

Liability
 ├── outstanding
 ├── settled
 └── remaining
```

---

# 5. Final liquidation result

Instead of embedding everything into `SyirkahLiquidation`, introduce a frozen result:

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

    public SyirkahLiquidationResult {
        Objects.requireNonNull(
                grossRealization,
                "grossRealization cannot be null");

        Objects.requireNonNull(
                liabilitiesSettled,
                "liabilitiesSettled cannot be null");

        Objects.requireNonNull(
                netRealization,
                "netRealization cannot be null");

        Objects.requireNonNull(
                originalCapital,
                "originalCapital cannot be null");

        Objects.requireNonNull(
                profitOrLoss,
                "profitOrLoss cannot be null");

        if (grossRealization.signum() < 0
                || liabilitiesSettled.signum() < 0
                || originalCapital.signum() < 0) {
            throw new IllegalArgumentException(
                    "Amounts cannot be negative");
        }

        BigDecimal expectedNet =
                grossRealization.subtract(liabilitiesSettled);

        if (expectedNet.compareTo(netRealization) != 0) {
            throw new IllegalArgumentException(
                    "Net realization does not reconcile");
        }
    }

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

But one important point:

**Do not blindly define**

```text
profitOrLoss = netRealization - originalCapital
```

for every Syirkah type.

The source explicitly describes that calculation for the particular Mudharabah dissolution arrangement, while other Syirkah types may have different contract/accounting treatment. Hukum-muamalah

So the actual calculation should be delegated:

```java
public interface SyirkahLiquidationResultPolicy {

    Uni<SyirkahLiquidationResult> determine(
            SyirkahLiquidation liquidation,
            SyirkahCloseSnapshot openingSnapshot,
            SyirkahContract.Version contractVersion);
}
```

This keeps the **contract-specific Sharia rule** out of the generic liquidation state machine.

---

# 6. Capital return becomes a separate obligation

We should not reuse `PartnerDistributionObligation`.

Create:

```java
public final class PartnerCapitalReturnObligation {

    private final String obligationId;
    private final String liquidationId;
    private final String partyId;
    private final BigDecimal capitalAmount;

    private CapitalReturnStatus status;

    private String approvedBy;
    private String postedJournalEntryId;

    public PartnerCapitalReturnObligation(
            String obligationId,
            String liquidationId,
            String partyId,
            BigDecimal capitalAmount) {

        this.obligationId =
                requireText(obligationId, "obligationId");

        this.liquidationId =
                requireText(liquidationId, "liquidationId");

        this.partyId =
                requireText(partyId, "partyId");

        Objects.requireNonNull(
                capitalAmount,
                "capitalAmount cannot be null");

        if (capitalAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "capitalAmount must be positive");
        }

        this.capitalAmount = capitalAmount;
        this.status = CapitalReturnStatus.CALCULATED;
    }

    public void approve(String approver) {
        if (status != CapitalReturnStatus.CALCULATED) {
            throw new IllegalStateException(
                    "Capital return must be CALCULATED");
        }

        this.approvedBy =
                requireText(approver, "approver");

        this.status = CapitalReturnStatus.APPROVED;
    }

    public void markPosted(String journalEntryId) {
        if (status != CapitalReturnStatus.APPROVED) {
            throw new IllegalStateException(
                    "Capital return must be APPROVED");
        }

        this.postedJournalEntryId =
                requireText(
                        journalEntryId,
                        "journalEntryId");

        this.status = CapitalReturnStatus.POSTED;
    }

    public void settle() {
        if (status != CapitalReturnStatus.POSTED) {
            throw new IllegalStateException(
                    "Capital return must be POSTED");
        }

        this.status = CapitalReturnStatus.SETTLED;
    }

    // getters...
}
```

```java
public enum CapitalReturnStatus {
    CALCULATED,
    APPROVED,
    POSTED,
    SETTLED,
    CANCELLED
}
```

This preserves the distinction:

```text
profit allocation
        ≠
capital return
        ≠
cash settlement
```

---

# 7. Final termination reconciliation

Before `CAPITAL_RETURN` can complete, require:

```text
net realizable business assets
    =
liabilities settled
+ partner distributions
+ partner capital returns
+ explicitly retained amount
```

The exact formula can be policy-specific, but the invariant should be explicit.

Create:

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

    public SyirkahLiquidationReconciliation {
        errors = List.copyOf(errors);

        if (balanced && !errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "Balanced reconciliation cannot contain errors");
        }
    }
}
```

No tolerance should silently hide money.

If there is a 0.01 difference, we need either:

- deterministic rounding policy, or
- explicit reconciliation adjustment.

Never:

```java
difference = BigDecimal.ZERO;
```

just to make the close pass.

---

# 8. Liquidation repository

```java
public interface SyirkahLiquidationRepository {

    Uni<Optional<SyirkahLiquidation>> findById(
            String tenantId,
            String ledgerId,
            String liquidationId);

    Uni<Optional<SyirkahLiquidation>> findByContractId(
            String tenantId,
            String ledgerId,
            String contractId);

    Uni<Void> save(
            String tenantId,
            String ledgerId,
            SyirkahLiquidation liquidation);
}
```

Supporting repositories:

```java
public interface LiquidationRealizationRepository {

    Uni<List<LiquidationRealization>> findByLiquidationId(
            String tenantId,
            String ledgerId,
            String liquidationId);

    Uni<Void> save(
            LiquidationRealization realization);
}
```

and:

```java
public interface LiquidationLiabilitySettlementRepository {

    Uni<List<LiquidationLiabilitySettlement>> findByLiquidationId(
            String tenantId,
            String ledgerId,
            String liquidationId);

    Uni<Void> save(
            LiquidationLiabilitySettlement settlement);
}
```

---

# 9. Application service

The complete API becomes:

```java
public interface SyirkahLiquidationService {

    Uni<SyirkahLiquidation> start(
            StartLiquidationRequest request);

    Uni<SyirkahLiquidation> beginAssetRealization(
            String liquidationId);

    Uni<LiquidationRealization> recordRealization(
            RecordLiquidationRealizationRequest request);

    Uni<SyirkahLiquidation> completeAssetRealization(
            String liquidationId);

    Uni<LiquidationLiabilitySettlement> settleLiability(
            RecordLiabilitySettlementRequest request);

    Uni<SyirkahLiquidation> completeLiabilitySettlement(
            String liquidationId);

    Uni<SyirkahLiquidationResult> determineResult(
            String liquidationId);

    Uni<List<PartnerDistributionObligation>> prepareDistribution(
            String liquidationId);

    Uni<List<PartnerCapitalReturnObligation>> prepareCapitalReturn(
            String liquidationId);

    Uni<SyirkahLiquidation> complete(
            String liquidationId);
}
```

Again, no single method automatically performs the entire termination.

---

# 10. How it connects to the existing close snapshot

This is the important historical boundary:

```text
                 OLD SYIRKAH
                     │
              Locked Close
                     │
                     ▼
          SyirkahCloseSnapshot
                     │
              immutable history
                     │
                     ▼
               Liquidation
                     │
       ┌─────────────┴─────────────┐
       ▼                           ▼
Asset realization          Liability settlement
       │                           │
       └─────────────┬─────────────┘
                     ▼
             Final reconciliation
                     │
                     ▼
              Final result
                     │
              ┌──────┴──────┐
              ▼             ▼
          Profit/Loss    Capital return
              │             │
              └──────┬──────┘
                     ▼
                TERMINATED
```

The snapshot is never rewritten.

---

# 11. New contract starts from a new opening

For a transition rather than termination:

```text
Old contract
     │
     ▼
locked snapshot
     │
     ▼
settlement
     │
     ▼
old contract closed
     │
     ▼
new contract
     │
     ▼
new opening position
```

This matches the source's explicit principle that when new capital or a new partner enters, the old Syirkah is first closed and the new Syirkah gets new bookkeeping. Hukum-muamalah

So we should **not** create:

```text
old contract version 1
        ↓
same running ledger
        ↓
new partner
```

as though nothing happened.

Instead:

```text
contract A / version 1
        ↓
close snapshot A
        ↓
transition
        ↓
contract B / version 1
```

---

# 12. Partner replacement becomes especially clean

For replacement:

```text
Existing partners approve
        ↓
Syufah / transfer restriction policy
        ↓
Old Syirkah close
        ↓
Frozen position
        ↓
Transfer / settlement
        ↓
Old partner exits
        ↓
New partner enters
        ↓
New Syirkah contract
```

The source specifically states that an external sale of a partner's share requires consent of the other partners and discusses their pre-emptive right; after agreement, the Syirkah is closed first so its latest financial position can be determined. Hukum-muamalah

Therefore our existing:

```java
PartnerTransferRestrictionPolicy
```

is not optional decoration—it is the right architectural boundary for this rule.

---

## 13. Final lifecycle

At this point the Syirkah extension has a complete lifecycle:

```text
CONTRACT ACTIVE
      │
      ├── normal period
      │       ↓
      │   close result
      │       ↓
      │   allocation
      │       ↓
      │   partner positions
      │       ↓
      │   immutable snapshot
      │       ↓
      │   CLOSE LOCKED
      │
      ├── continue
      │       ↓
      │   next period
      │
      ├── partner/capital transition
      │       ↓
      │   settlement
      │       ↓
      │   new contract
      │
      └── full termination
              ↓
          liquidation
              ↓
       asset realization
              ↓
       liability settlement
              ↓
        final result
              ↓
        distribution
              ↓
        capital return
              ↓
          TERMINATED
```

### One important source-derived rule

The source's accounting example defines net profit as:

```text
Total Revenue
- Direct Costs
- Operating Costs
- Other Costs
= Net Profit
```

and explicitly distinguishes profit sharing from loss treatment. Hukum-muamalah

So the implementation should continue using our existing:

```text
FinancialResult
SyirkahResultCalculator
LossAllocationPolicyResolver
```

rather than introducing a second liquidation-specific profit formula.

**Next I would implement the transition/termination orchestration and its invariants**, including the exact `SyirkahTransitionServiceImpl`, `SyirkahLiquidationServiceImpl`, and the idempotency/transaction boundaries connecting them to the existing close snapshot and journal posting.

//////////////


Next, I would implement the **Syirkah liquidation settlement/accounting bridge**.

The liquidation aggregate should remain business-state oriented. Accounting should be a separate policy/application layer, just like profit distribution.

### 1. Capital-return accounting policy

```java
package tech.kayys.syirkah.accounting.domain.syirkah.liquidation;

import java.util.List;

public interface SyirkahCapitalReturnAccountingPolicy {

    JournalMapping map(List<PartnerCapitalReturnObligation> obligations);

    record JournalMapping(
            String description,
            List<JournalLineMapping> lines
    ) {
        public JournalMapping {
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("Description cannot be blank");
            }
            if (lines == null || lines.isEmpty()) {
                throw new IllegalArgumentException("Journal mapping must contain lines");
            }
            lines = List.copyOf(lines);
        }
    }

    record JournalLineMapping(
            String accountId,
            LineType type,
            String amount,
            String currencyCode,
            String description
    ) {
        public JournalLineMapping {
            if (accountId == null || accountId.isBlank()) {
                throw new IllegalArgumentException("Account ID cannot be blank");
            }
            if (amount == null || amount.isBlank()) {
                throw new IllegalArgumentException("Amount cannot be blank");
            }
            if (currencyCode == null || currencyCode.isBlank()) {
                throw new IllegalArgumentException("Currency code cannot be blank");
            }
        }
    }

    enum LineType {
        DEBIT,
        CREDIT
    }
}
```

This deliberately does **not** decide that every capital return is:

```text
Dr Partner Capital
Cr Cash
```

because the actual accounting treatment can depend on the ledger design, impairment, retained balances, settlement mechanism, and contract policy.

---

### 2. Capital-return posting service

The flow should be:

```text
Liquidation result
      ↓
Capital return obligations
      ↓
Approval
      ↓
Accounting policy
      ↓
Journal posting
      ↓
Obligations marked POSTED
      ↓
Cash settlement
```

Not:

```text
Liquidation → automatically pay partners
```

The proposed application service:

```java
package tech.kayys.syirkah.accounting.application.syirkah.liquidation;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;
import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.PartnerCapitalReturnObligation;

import java.util.List;

public interface SyirkahCapitalReturnPostingService {

    Uni<JournalEntryId> post(
            String tenantId,
            String ledgerId,
            String liquidationId,
            String postedBy
    );

    Uni<List<PartnerCapitalReturnObligation>> findObligations(
            String tenantId,
            String ledgerId,
            String liquidationId
    );
}
```

The implementation should enforce:

```text
all obligations APPROVED
        ↓
map accounting
        ↓
post one idempotent journal
        ↓
mark every obligation POSTED
```

Use:

```text
sourceType = SYIRKAH_CAPITAL_RETURN
sourceId   = liquidationId
```

and the same database-backed source uniqueness we established for other Syirkah accounting operations.

---

### 3. Do not confuse capital return with profit distribution

This is important for the model.

Suppose:

```text
Original capital       100m
Final net assets       130m
Profit                 30m
```

The liquidation may produce:

```text
Profit allocation      30m
Capital return        100m
```

Those are **two different obligations**.

Therefore:

```text
PartnerDistributionObligation
```

and:

```text
PartnerCapitalReturnObligation
```

must remain separate aggregates.

Likewise:

```text
PROFIT_ALLOCATION
CAPITAL_RETURN
CASH_SETTLEMENT
```

remain separate `PartnerAccountMovement` types.

---

### 4. Add liquidation reconciliation

Before capital return is approved, introduce:

```java
package tech.kayys.syirkah.accounting.domain.syirkah.liquidation;

import java.math.BigDecimal;
import java.util.List;

public record SyirkahLiquidationReconciliation(
        BigDecimal netRealization,
        BigDecimal totalProfitDistribution,
        BigDecimal totalLossAbsorption,
        BigDecimal totalCapitalReturn,
        BigDecimal remainingBalance,
        boolean balanced,
        List<String> errors
) {

    public SyirkahLiquidationReconciliation {
        netRealization = requireNonNegative(netRealization, "Net realization");
        totalProfitDistribution = requireNonNegative(
                totalProfitDistribution,
                "Total profit distribution"
        );
        totalLossAbsorption = requireNonNegative(
                totalLossAbsorption,
                "Total loss absorption"
        );
        totalCapitalReturn = requireNonNegative(
                totalCapitalReturn,
                "Total capital return"
        );
        remainingBalance = requireNonNegative(
                remainingBalance,
                "Remaining balance"
        );
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    private static BigDecimal requireNonNegative(
            BigDecimal value,
            String field
    ) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(
                    field + " must not be negative"
            );
        }
        return value;
    }
}
```

The key invariant is:

```text
net realization
=
profit distribution
+ capital return
+ remaining balance
```

for the profit case, with the exact loss-side treatment determined by the liquidation policy.

The reconciliation must explicitly represent any residual instead of silently forcing it to zero.

---

### 5. Complete liquidation lifecycle

At this point the lifecycle becomes:

```text
NORMAL CLOSE
    ↓
Locked Syirkah Close Snapshot
    ↓
Syirkah Liquidation
    ↓
Asset Realization
    ↓
Liability Settlement
    ↓
Final Reconciliation
    ↓
Liquidation Result
    ↓
┌───────────────────────┐
│ Profit                │
│   ↓                   │
│ Profit Distribution   │
│   ↓                   │
│ Capital Return        │
│   ↓                   │
│ Cash Settlement       │
│                       │
│ OR                    │
│                       │
│ Loss                  │
│   ↓                   │
│ Loss Allocation /     │
│ Responsibility        │
│   ↓                   │
│ Capital Return        │
└───────────────────────┘
    ↓
Old Contract Terminated
```

And for a partner replacement:

```text
Old Contract
    ↓
Locked Close Snapshot
    ↓
Settlement
    ↓
Old Contract Closed
    ↓
Transfer / Exit / Entry
    ↓
New Contract
    ↓
New Opening Position
```

### 6. One important correction to the previous design

The framework already has a generic `UnitOfWork` abstraction:

```java
tech.kayys.syirkah.foundation.application.transaction.UnitOfWork
```

So we **should use that existing abstraction**, rather than introducing a separate `AccountingUnitOfWork`.

That keeps the new Syirkah implementation aligned with the existing platform architecture.

The next concrete layer after this should therefore be the **`SyirkahLiquidationService` application orchestration + repository ports**, including idempotent state transitions and the final `COMPLETE` invariant.

/////////////

Next is the **`SyirkahLiquidationService` application orchestration layer**.

The domain aggregate already defines the state machine. The application layer should now enforce the workflow, persistence, idempotency, and separation between liquidation, accounting, and settlement.

### 1. Repository ports

```java
package tech.kayys.syirkah.accounting.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.SyirkahLiquidation;

import java.util.Optional;

public interface SyirkahLiquidationRepository {

    Uni<Optional<SyirkahLiquidation>> findById(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<Optional<SyirkahLiquidation>> findByContractId(
            String tenantId,
            String ledgerId,
            String contractId
    );

    Uni<Void> save(SyirkahLiquidation liquidation);
}
```

Realization and liability records remain separate:

```java
package tech.kayys.syirkah.accounting.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.LiquidationRealization;

import java.util.List;

public interface LiquidationRealizationRepository {

    Uni<List<LiquidationRealization>> findByLiquidationId(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<Void> save(LiquidationRealization realization);
}
```

```java
package tech.kayys.syirkah.accounting.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.LiquidationLiabilitySettlement;

import java.util.List;

public interface LiquidationLiabilitySettlementRepository {

    Uni<List<LiquidationLiabilitySettlement>> findByLiquidationId(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<Void> save(LiquidationLiabilitySettlement settlement);
}
```

---

### 2. Application service

```java
package tech.kayys.syirkah.accounting.application.syirkah.liquidation;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface SyirkahLiquidationService {

    Uni<SyirkahLiquidation> start(StartLiquidationRequest request);

    Uni<SyirkahLiquidation> beginAssetRealization(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<SyirkahLiquidation> recordRealization(
            RecordLiquidationRealizationRequest request
    );

    Uni<SyirkahLiquidation> completeAssetRealization(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<SyirkahLiquidation> recordLiabilitySettlement(
            RecordLiabilitySettlementRequest request
    );

    Uni<SyirkahLiquidation> completeLiabilitySettlement(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<SyirkahLiquidation> completeReconciliation(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<SyirkahLiquidation> determineResult(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<SyirkahLiquidation> completeDistribution(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<SyirkahLiquidation> completeCapitalReturn(
            String tenantId,
            String ledgerId,
            String liquidationId,
            String completedBy
    );
}
```

---

### 3. Start request

```java
package tech.kayys.syirkah.accounting.application.syirkah.liquidation;

import java.time.LocalDate;
import java.util.Objects;

public record StartLiquidationRequest(
        String tenantId,
        String ledgerId,
        String liquidationId,
        String contractId,
        int contractVersion,
        String closeCycleId,
        LocalDate liquidationDate,
        String requestedBy
) {

    public StartLiquidationRequest {
        require(tenantId, "Tenant ID");
        require(ledgerId, "Ledger ID");
        require(liquidationId, "Liquidation ID");
        require(contractId, "Contract ID");
        require(closeCycleId, "Close cycle ID");
        require(requestedBy, "Requested by");

        Objects.requireNonNull(liquidationDate, "Liquidation date cannot be null");

        if (contractVersion <= 0) {
            throw new IllegalArgumentException(
                    "Contract version must be positive"
            );
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
    }
}
```

---

### 4. Critical invariant: liquidation requires a locked close

This is where the application service should connect the previous work.

Do **not** allow:

```text
Open close
   ↓
Liquidation
```

Require:

```text
CloseCycle
   ↓
SYIRKAH_RESULT
   ↓
SYIRKAH_ALLOCATION
   ↓
SYIRKAH_DISTRIBUTION
   ↓
VALIDATION
   ↓
APPROVAL
   ↓
LOCKED
   ↓
Liquidation
```

The service therefore needs a port:

```java
package tech.kayys.syirkah.accounting.application.port;

import io.smallrye.mutiny.Uni;

public interface SyirkahCloseStateProvider {

    Uni<Boolean> isLocked(
            String tenantId,
            String ledgerId,
            String closeCycleId
    );
}
```

Then:

```java
if (!locked) {
    return Uni.createFrom().failure(
            new IllegalStateException(
                    "Syirkah liquidation requires a locked close cycle"
            )
    );
}
```

This prevents liquidation from bypassing the normal accounting close.

---

### 5. Idempotent `start`

The important pattern is:

```text
find existing
     │
     ├── exists → return existing
     │
     └── absent
           ↓
       validate close
           ↓
       create liquidation
           ↓
       save
```

Conceptually:

```java
return liquidationRepository
        .findById(
                request.tenantId(),
                request.ledgerId(),
                request.liquidationId()
        )
        .chain(existing -> {
            if (existing.isPresent()) {
                return Uni.createFrom().item(existing.get());
            }

            return closeStateProvider.isLocked(
                    request.tenantId(),
                    request.ledgerId(),
                    request.closeCycleId()
            ).chain(locked -> {
                if (!locked) {
                    return Uni.createFrom().failure(
                            new IllegalStateException(
                                    "Liquidation requires a locked close cycle"
                            )
                    );
                }

                SyirkahLiquidation liquidation =
                        SyirkahLiquidation.start(
                                request.liquidationId(),
                                request.tenantId(),
                                request.ledgerId(),
                                request.contractId(),
                                request.contractVersion(),
                                request.closeCycleId(),
                                request.liquidationDate()
                        );

                return liquidationRepository
                        .save(liquidation)
                        .replaceWith(liquidation);
            });
        });
```

The database still needs a uniqueness constraint. The application-level lookup is **not** sufficient against concurrent requests.

```sql
UNIQUE (
    tenant_id,
    ledger_id,
    liquidation_id
);

UNIQUE (
    tenant_id,
    ledger_id,
    contract_id,
    status
);
```

The second constraint should be implemented more carefully as a database partial/conditional unique index if the database supports it, so only active liquidation states conflict.

---

### 6. Realization must be idempotent too

A realization should have its own business source/reference:

```java
public record RecordLiquidationRealizationRequest(
        String tenantId,
        String ledgerId,
        String realizationId,
        String liquidationId,
        String assetReference,
        BigDecimal carryingValue,
        BigDecimal realizedAmount,
        LocalDate realizationDate,
        String sourceReference
) {}
```

Database:

```sql
UNIQUE (
    tenant_id,
    ledger_id,
    realization_id
);
```

And preferably:

```sql
UNIQUE (
    tenant_id,
    ledger_id,
    liquidation_id,
    source_reference
);
```

That protects against a retry accidentally realizing the same asset twice.

---

### 7. Liability settlement has the same rule

```text
Outstanding liability
        ↓
Settlement instruction
        ↓
Actual settled amount
        ↓
Cannot exceed outstanding amount
```

Never allow:

```text
settledAmount > outstandingAmount
```

and never allow a second identical settlement simply because the first request timed out.

---

### 8. Result determination

This should **not** be implemented directly inside the service.

Use:

```java
package tech.kayys.syirkah.accounting.domain.syirkah.liquidation;

import io.smallrye.mutiny.Uni;

public interface SyirkahLiquidationResultPolicy {

    Uni<SyirkahLiquidationResult> determine(
            SyirkahLiquidation liquidation,
            SyirkahCloseSnapshot openingSnapshot,
            SyirkahContract.Version contractVersion
    );
}
```

So:

```text
LiquidationService
       ↓
ResultPolicy
       ↓
SyirkahLiquidationResult
```

This is important because the calculation of final partner entitlement should remain **contract/policy-specific**, particularly for Mudharabah versus Musyarakah/Inan and responsibility cases.

---

### 9. Completion invariant

The final operation should be deliberately strict:

```text
RESULT_DETERMINED
        ↓
DISTRIBUTION completed
        ↓
CAPITAL_RETURN completed
        ↓
COMPLETE
```

Therefore `completeCapitalReturn()` should verify:

```text
1. liquidation result exists
2. reconciliation is balanced
3. all profit/loss obligations are completed
4. all capital-return obligations are POSTED
5. no unresolved liability remains
6. no unresolved asset realization remains
7. operator is supplied
```

Only then:

```java
liquidation.completeCapitalReturn(completedBy);
```

This gives us the final invariant:

```text
COMPLETE
  ⇔
final financial position reconciled
AND
result determined
AND
partner result obligations completed
AND
capital return obligations completed
AND
no unresolved liquidation balance
```

### Where this puts us

The Syirkah financial lifecycle is now becoming coherent:

```text
Contract
   ↓
Periodic Close
   ↓
Financial Result
   ↓
Partner Allocation
   ↓
Close Position
   ↓
Immutable Close Snapshot
   ↓
LOCKED
   │
   ├── Continue Syirkah
   │
   ├── Partner Entry/Exit
   │
   └── Full Liquidation
           ↓
       Realization
           ↓
       Liability Settlement
           ↓
       Final Reconciliation
           ↓
       Final Result
           ↓
       Distribution
           ↓
       Capital Return
           ↓
       Terminated
```

The next piece I would implement is **`SyirkahLiquidationResultPolicy` + final liquidation reconciliation**, because that is the point where the system must formally distinguish **net assets, profit/loss, partner allocation, and capital return** before any money can be settled.


The source supports the distinction we need: in **Mudharabah**, ordinary business loss is borne by capital providers, while the manager becomes responsible when loss results from intentional misconduct or violation of the agreed conditions. Hukum-muamalah

So the next implementation should be a **policy-based liquidation result**, not a generic `netRealization - capital` formula.

### 1. Result model

```java
package tech.kayys.syirkah.accounting.domain.syirkah.liquidation;

import java.math.BigDecimal;
import java.util.Objects;

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
        require(liquidationId, "Liquidation ID");
        require(contractId, "Contract ID");

        if (contractVersion <= 0) {
            throw new IllegalArgumentException(
                    "Contract version must be positive"
            );
        }

        requireNonNegative(grossRealization, "Gross realization");
        requireNonNegative(liabilitiesSettled, "Liabilities settled");
        requireNonNegative(originalCapital, "Original capital");

        Objects.requireNonNull(profitOrLoss, "Profit or loss cannot be null");

        BigDecimal expectedNet =
                grossRealization.subtract(liabilitiesSettled);

        if (expectedNet.compareTo(netRealization) != 0) {
            throw new IllegalArgumentException(
                    "Net realization must equal gross realization minus liabilities settled"
            );
        }

        if (netRealization.signum() < 0) {
            throw new IllegalArgumentException(
                    "Net realization cannot be negative"
            );
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

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
    }

    private static void requireNonNegative(
            BigDecimal value,
            String name
    ) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(
                    name + " must not be negative"
            );
        }
    }
}
```

### 2. Policy contract

```java
package tech.kayys.syirkah.accounting.domain.syirkah.liquidation;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.syirkah.SyirkahContract;
import tech.kayys.syirkah.accounting.domain.syirkah.close.SyirkahCloseSnapshot;

public interface SyirkahLiquidationResultPolicy {

    Uni<SyirkahLiquidationResult> determine(
            SyirkahLiquidation liquidation,
            SyirkahCloseSnapshot openingSnapshot,
            SyirkahContract.Version contractVersion
    );
}
```

This keeps the domain rule explicit:

```text
Liquidation
   +
Opening position
   +
Contract version
        ↓
Liquidation result policy
        ↓
Final profit/loss
```

### 3. Default policy for capital-based Syirkah

For contracts where the liquidation result is determined from the final net realization against the capital position:

```java
package tech.kayys.syirkah.accounting.domain.syirkah.liquidation;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.syirkah.SyirkahContract;
import tech.kayys.syirkah.accounting.domain.syirkah.close.SyirkahCloseSnapshot;

import java.math.BigDecimal;

public final class CapitalBasedLiquidationResultPolicy
        implements SyirkahLiquidationResultPolicy {

    @Override
    public Uni<SyirkahLiquidationResult> determine(
            SyirkahLiquidation liquidation,
            SyirkahCloseSnapshot openingSnapshot,
            SyirkahContract.Version contractVersion
    ) {
        BigDecimal originalCapital =
                openingSnapshot.positions().stream()
                        .map(position -> position.openingCapital())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netRealization =
                liquidation.getNetRealization();

        BigDecimal profitOrLoss =
                netRealization.subtract(originalCapital);

        return Uni.createFrom().item(
                new SyirkahLiquidationResult(
                        liquidation.getLiquidationId(),
                        liquidation.getContractId(),
                        liquidation.getContractVersion(),
                        liquidation.getGrossRealization(),
                        liquidation.getLiabilitiesSettled(),
                        netRealization,
                        originalCapital,
                        profitOrLoss
                )
        );
    }
}
```

But this policy should **not** be universally assigned to every `SyirkahType`.

### 4. Resolver

We already established the pattern of resolving loss behavior by contract type. Extend that pattern here:

```java
package tech.kayys.syirkah.accounting.domain.syirkah.liquidation;

import tech.kayys.syirkah.accounting.domain.syirkah.SyirkahContract;

public interface SyirkahLiquidationResultPolicyResolver {

    SyirkahLiquidationResultPolicy resolve(
            SyirkahContract.Version contractVersion
    );
}
```

Then:

```java
package tech.kayys.syirkah.accounting.domain.syirkah.liquidation;

import tech.kayys.syirkah.accounting.domain.syirkah.SyirkahContract;
import tech.kayys.syirkah.accounting.domain.syirkah.SyirkahType;

import java.util.Map;
import java.util.Objects;

public final class DefaultSyirkahLiquidationResultPolicyResolver
        implements SyirkahLiquidationResultPolicyResolver {

    private final Map<SyirkahType, SyirkahLiquidationResultPolicy> policies;

    public DefaultSyirkahLiquidationResultPolicyResolver(
            Map<SyirkahType, SyirkahLiquidationResultPolicy> policies
    ) {
        this.policies = Map.copyOf(
                Objects.requireNonNull(policies)
        );
    }

    @Override
    public SyirkahLiquidationResultPolicy resolve(
            SyirkahContract.Version contractVersion
    ) {
        Objects.requireNonNull(contractVersion);

        SyirkahLiquidationResultPolicy policy =
                policies.get(contractVersion.type());

        if (policy == null) {
            throw new IllegalStateException(
                    "No liquidation result policy configured for Syirkah type: "
                            + contractVersion.type()
            );
        }

        return policy;
    }
}
```

If the current `SyirkahContract.Version` does not expose `type()`, use the existing contract-level type accessor rather than adding a duplicate field.

### 5. Do not silently use the current PartnerAccount

The calculation should use:

```text
Locked historical snapshot
        ↓
opening capital
```

not:

```text
Current PartnerAccount
```

That preserves the previously established invariant:

```text
PartnerAccount = rebuildable projection
CloseSnapshot = historical financial position
```

### 6. Final reconciliation

Once the result is determined:

```text
Net realization
      ↓
Profit/Loss
      ↓
Partner allocations
      +
Capital returns
      +
Any remaining balance
      ↓
Final reconciliation
```

For a profit example:

```text
Net realization       130m
Original capital      100m
Profit                 30m
```

Then:

```text
Profit allocation       30m
Capital return          100m
Remaining balance        0
```

For a loss:

```text
Net realization        70m
Original capital      100m
Loss                   30m
```

The `30m` is **loss**, not a negative capital-return obligation. The capital return is limited by the remaining capital position.

That distinction is essential.

### 7. Next layer

After this, the next implementation should be the **`SyirkahLiquidationAllocationService`**:

```text
SyirkahLiquidationResult
        ↓
contract-specific allocation
        ↓
PartnerDistributionObligation
        +
PartnerCapitalReturnObligation
        +
LossResponsibility / Recovery
        ↓
Final LiquidationReconciliation
```

That will connect the existing profit/loss allocation engine to liquidation without duplicating its business rules.

//////////////

The next layer is the **liquidation allocation service**. The important design point is that liquidation produces three different financial outcomes:

1. **Profit allocation**
2. **Loss allocation / responsibility**
3. **Capital return**

They must never be collapsed into one “partner payout”.

### 1. Allocation result

```java
package tech.kayys.syirkah.accounting.domain.syirkah.liquidation;

import tech.kayys.syirkah.accounting.domain.syirkah.distribution.PartnerDistributionObligation;
import tech.kayys.syirkah.accounting.domain.syirkah.capital.PartnerCapitalReturnObligation;
import tech.kayys.syirkah.accounting.domain.syirkah.loss.SyirkahLossResponsibility;

import java.util.List;

public record SyirkahLiquidationAllocation(
        String liquidationId,
        SyirkahLiquidationResult result,
        List<PartnerDistributionObligation> profitDistributions,
        List<PartnerCapitalReturnObligation> capitalReturns,
        List<SyirkahLossResponsibility> lossResponsibilities
) {

    public SyirkahLiquidationAllocation {
        if (liquidationId == null || liquidationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Liquidation ID cannot be blank"
            );
        }

        if (result == null) {
            throw new IllegalArgumentException(
                    "Liquidation result cannot be null"
            );
        }

        profitDistributions =
                List.copyOf(profitDistributions == null
                        ? List.of()
                        : profitDistributions);

        capitalReturns =
                List.copyOf(capitalReturns == null
                        ? List.of()
                        : capitalReturns);

        lossResponsibilities =
                List.copyOf(lossResponsibilities == null
                        ? List.of()
                        : lossResponsibilities);
    }
}
```

This deliberately does **not** use a generic `PartnerSettlement`.

---

## 2. Allocation service

```java
package tech.kayys.syirkah.accounting.application.syirkah.liquidation;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.syirkah.SyirkahContract;
import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.SyirkahLiquidation;
import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.SyirkahLiquidationAllocation;
import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.SyirkahLiquidationResult;
import tech.kayys.syirkah.accounting.domain.syirkah.close.SyirkahCloseSnapshot;

public interface SyirkahLiquidationAllocationService {

    Uni<SyirkahLiquidationAllocation> allocate(
            SyirkahLiquidation liquidation,
            SyirkahLiquidationResult result,
            SyirkahCloseSnapshot openingSnapshot,
            SyirkahContract.Version contractVersion
    );
}
```

The service should use the **existing** `SyirkahResultCalculator` for profit/loss allocation rather than creating another nisbah engine.

---

# 3. Profit allocation

For liquidation profit:

```text
Net realization
       -
Opening capital
       =
Liquidation profit
       ↓
Existing SyirkahResultCalculator
       ↓
Partner profit allocations
```

For example:

```text
Net realization       130
Opening capital       100
Profit                 30

Partner A 40%          12
Partner B 60%          18
```

The result is then converted into the already-established `PartnerDistributionObligation`.

```java
private List<PartnerDistributionObligation> createProfitDistributions(
        SyirkahLiquidationResult result,
        ProfitSharingResult allocation
) {
    return allocation.allocations().stream()
            .filter(a -> a.amount().signum() > 0)
            .map(a -> PartnerDistributionObligation.calculated(
                    result.liquidationId(),
                    result.contractId(),
                    result.contractVersion(),
                    a.partyId(),
                    a.amount()
            ))
            .toList();
}
```

The exact factory should follow the existing `PartnerDistributionObligation` API if it already has a different constructor/factory.

---

# 4. Capital return is separate

This is the more important part.

For:

```text
Opening capital = 100
Net realization = 130
Profit = 30
```

the obligations are:

```text
Profit distribution = 30
Capital return      = 100
```

**Not:**

```text
Partner payout = 130
```

Create a separate obligation:

```java
package tech.kayys.syirkah.accounting.domain.syirkah.capital;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public final class PartnerCapitalReturnObligation {

    public enum Status {
        CALCULATED,
        APPROVED,
        POSTED,
        SETTLED,
        CANCELLED
    }

    private final String obligationId;
    private final String liquidationId;
    private final String partyId;
    private final BigDecimal capitalAmount;

    private Status status;
    private String approvedBy;
    private Instant approvedAt;
    private String postedJournalEntryId;
    private Instant postedAt;

    public PartnerCapitalReturnObligation(
            String obligationId,
            String liquidationId,
            String partyId,
            BigDecimal capitalAmount
    ) {
        require(obligationId, "Obligation ID");
        require(liquidationId, "Liquidation ID");
        require(partyId, "Party ID");

        if (capitalAmount == null || capitalAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Capital return amount must be positive"
            );
        }

        this.obligationId = obligationId;
        this.liquidationId = liquidationId;
        this.partyId = partyId;
        this.capitalAmount = capitalAmount;
        this.status = Status.CALCULATED;
    }

    public void approve(String approver) {
        require(approver, "Approver");

        if (status != Status.CALCULATED) {
            throw new IllegalStateException(
                    "Capital return can only be approved from CALCULATED"
            );
        }

        status = Status.APPROVED;
        approvedBy = approver;
        approvedAt = Instant.now();
    }

    public void markPosted(
            String journalEntryId,
            String postedBy
    ) {
        require(journalEntryId, "Journal entry ID");
        require(postedBy, "Posted by");

        if (status != Status.APPROVED) {
            throw new IllegalStateException(
                    "Capital return can only be posted from APPROVED"
            );
        }

        status = Status.POSTED;
        postedJournalEntryId = journalEntryId;
        postedAt = Instant.now();
    }

    public void settle() {
        if (status != Status.POSTED) {
            throw new IllegalStateException(
                    "Capital return can only be settled from POSTED"
            );
        }

        status = Status.SETTLED;
    }

    public String getObligationId() {
        return obligationId;
    }

    public String getLiquidationId() {
        return liquidationId;
    }

    public String getPartyId() {
        return partyId;
    }

    public BigDecimal getCapitalAmount() {
        return capitalAmount;
    }

    public Status getStatus() {
        return status;
    }

    private static void require(String value, String name) {
        Objects.requireNonNull(value, name + " cannot be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " cannot be blank"
            );
        }
    }
}
```

---

# 5. Capital return calculation

Capital return must use the **opening capital position**, not the profit allocation.

For each partner:

```text
returnable capital
    =
opening capital
    - capital impairments
    - previous capital returns
```

The liquidation service should therefore consume the frozen close position.

Conceptually:

```java
private BigDecimal calculateReturnableCapital(
        PartnerClosePositionSnapshot position
) {
    return position.openingCapital()
            .add(position.capitalContributions())
            .subtract(position.capitalReturns())
            .subtract(position.capitalImpairments());
}
```

For a full liquidation, that becomes the maximum capital-return obligation.

---

# 6. Loss is different

Suppose:

```text
Opening capital       100
Net realization        70
Loss                   30
```

We do **not** create:

```text
Capital return = 70
Loss distribution = 30
```

as two independent payouts without reconciliation.

Instead:

```text
Capital position
        ↓
Loss allocation policy
        ↓
Remaining capital
        ↓
Capital return
```

For a normal capital-based loss:

```text
Partner A capital     40
Partner B capital     60

Loss                  30

A absorbs              12
B absorbs              18

Remaining capital:
A                      28
B                      42
```

Therefore:

```text
Capital return:
A                       28
B                       42
```

This is exactly why **loss allocation and capital return cannot be one operation**.

---

# 7. Reconciliation

Now we need the invariant that prevents money from being created or lost during liquidation.

```java
package tech.kayys.syirkah.accounting.domain.syirkah.liquidation;

import java.math.BigDecimal;
import java.util.ArrayList;
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
        errors = List.copyOf(
                errors == null ? List.of() : errors
        );
    }

    public static SyirkahLiquidationReconciliation reconcile(
            BigDecimal netRealization,
            BigDecimal totalProfitDistribution,
            BigDecimal totalLossAbsorption,
            BigDecimal totalCapitalReturn
    ) {
        List<String> errors = new ArrayList<>();

        BigDecimal distributable =
                netRealization
                        .subtract(totalCapitalReturn);

        BigDecimal expectedDistribution =
                distributable.max(BigDecimal.ZERO);

        BigDecimal actualDistribution =
                totalProfitDistribution;

        if (expectedDistribution.compareTo(actualDistribution) != 0) {
            errors.add(
                    "Profit distribution does not reconcile with "
                            + "net realization after capital return"
            );
        }

        BigDecimal remainingBalance =
                netRealization
                        .subtract(totalCapitalReturn)
                        .subtract(totalProfitDistribution);

        boolean balanced =
                remainingBalance.signum() == 0
                        && errors.isEmpty();

        return new SyirkahLiquidationReconciliation(
                netRealization,
                totalProfitDistribution,
                totalLossAbsorption,
                totalCapitalReturn,
                remainingBalance,
                balanced,
                errors
        );
    }
}
```

However, for **loss**, we need a slightly different equation.

The stronger invariant is:

```text
net realization
=
capital returned
+
profit distributed
```

when the result is a profit.

For a loss:

```text
opening capital
-
loss absorption
=
capital returned
```

Therefore the final reconciliation should actually know the **opening capital** as well.

I'd refine the model to:

```java
public record SyirkahLiquidationReconciliation(
        BigDecimal openingCapital,
        BigDecimal netRealization,
        BigDecimal profitDistributed,
        BigDecimal lossAbsorbed,
        BigDecimal capitalReturned,
        BigDecimal remainingBalance,
        boolean balanced,
        List<String> errors
) {}
```

with invariants:

```text
PROFIT:

netRealization
=
openingCapital
+
profitDistributed
```

and:

```text
netRealization
=
capitalReturned
+
profitDistributed
```

while:

```text
LOSS:

openingCapital
=
netRealization
+
lossAbsorbed
```

and:

```text
netRealization
=
capitalReturned
```

assuming all remaining assets are returned.

---

# 8. Final liquidation equation

That gives us a very useful invariant:

```text
                 PROFIT
                   │
        ┌──────────┴──────────┐
        │                     │
Capital return          Profit distribution
        │                     │
        └──────────┬──────────┘
                   ↓
             Net realization
```

For loss:

```text
                 LOSS
                   │
        ┌──────────┴──────────┐
        │                     │
 Loss absorption        Capital return
        │                     │
        └──────────┬──────────┘
                   ↓
             Net realization
```

And **cash settlement is still downstream**:

```text
Liquidation result
       ↓
Obligations
       ↓
Approval
       ↓
Accounting posting
       ↓
Cash settlement
```

This preserves the distinction we already established between allocation and actual payment.

### The next implementation step

The next piece should now be **`SyirkahLiquidationAllocationServiceImpl` + the final reconciliation calculator**, which will combine:

- `SyirkahResultCalculator`
- `LossAllocationPolicyResolver`
- frozen `SyirkahCloseSnapshot`
- `PartnerDistributionObligation`
- `PartnerCapitalReturnObligation`
- conditional `SyirkahLossResponsibility`
- `SyirkahLiquidationReconciliation`

and make the whole liquidation calculation **idempotent and impossible to complete when the reconciliation does not balance**.

////////////////


## Next: `SyirkahLiquidationAllocationServiceImpl`

This is the orchestration boundary between the already-existing Syirkah calculation engine and liquidation obligations.

The important rule is:

> **Allocation may be recalculated safely, but a liquidation must never advance to settlement while its reconciliation is unbalanced.**

### 1. Repository for the allocation result

```java
package tech.kayys.syirkah.accounting.spi.syirkah.liquidation;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.SyirkahLiquidationAllocation;

import java.util.Optional;

public interface SyirkahLiquidationAllocationRepository {

    Uni<Optional<SyirkahLiquidationAllocation>> findByLiquidationId(
            String tenantId,
            String ledgerId,
            String liquidationId
    );

    Uni<Void> save(
            String tenantId,
            String ledgerId,
            SyirkahLiquidationAllocation allocation
    );
}
```

The database should enforce:

```sql
UNIQUE (
    tenant_id,
    ledger_id,
    liquidation_id
);
```

That gives us persistent idempotency rather than relying on an in-memory guard.

---

# 2. Reconciliation calculator

I would make this a pure domain service.

```java
package tech.kayys.syirkah.accounting.domain.syirkah.liquidation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class SyirkahLiquidationReconciliationCalculator {

    public SyirkahLiquidationReconciliation calculate(
            SyirkahLiquidationResult result,
            BigDecimal totalProfitDistribution,
            BigDecimal totalLossAbsorption,
            BigDecimal totalCapitalReturn
    ) {
        requireNonNegative(
                totalProfitDistribution,
                "Total profit distribution"
        );

        requireNonNegative(
                totalLossAbsorption,
                "Total loss absorption"
        );

        requireNonNegative(
                totalCapitalReturn,
                "Total capital return"
        );

        List<String> errors = new ArrayList<>();

        BigDecimal remainingBalance;

        if (result.isProfit()) {
            /*
             * Final assets must equal:
             *
             * original capital
             * + profit distributed
             *
             * and simultaneously:
             *
             * capital returned
             * + profit distributed
             */
            BigDecimal expectedProfit =
                    result.netRealization()
                            .subtract(result.originalCapital());

            if (expectedProfit.compareTo(totalProfitDistribution) != 0) {
                errors.add(
                        "Profit distribution does not equal liquidation profit"
                );
            }

            if (totalLossAbsorption.signum() != 0) {
                errors.add(
                        "Loss absorption must be zero for a profitable liquidation"
                );
            }

            BigDecimal expectedCapitalReturn =
                    result.originalCapital();

            if (expectedCapitalReturn.compareTo(totalCapitalReturn) != 0) {
                errors.add(
                        "Capital return does not equal returnable capital"
                );
            }

            remainingBalance =
                    result.netRealization()
                            .subtract(totalCapitalReturn)
                            .subtract(totalProfitDistribution);

        } else if (result.isLoss()) {
            /*
             * For loss:
             *
             * original capital
             * =
             * loss absorbed
             * + capital returned
             */
            BigDecimal expectedLoss =
                    result.originalCapital()
                            .subtract(result.netRealization());

            if (expectedLoss.compareTo(totalLossAbsorption) != 0) {
                errors.add(
                        "Loss absorption does not equal liquidation loss"
                );
            }

            if (totalProfitDistribution.signum() != 0) {
                errors.add(
                        "Profit distribution must be zero for a loss"
                );
            }

            BigDecimal expectedCapitalReturn =
                    result.netRealization();

            if (expectedCapitalReturn.compareTo(totalCapitalReturn) != 0) {
                errors.add(
                        "Capital return does not equal remaining net realization"
                );
            }

            remainingBalance =
                    result.netRealization()
                            .subtract(totalCapitalReturn);

        } else {
            /*
             * Break-even:
             *
             * original capital = capital returned
             */
            if (totalProfitDistribution.signum() != 0) {
                errors.add(
                        "Profit distribution must be zero at break-even"
                );
            }

            if (totalLossAbsorption.signum() != 0) {
                errors.add(
                        "Loss absorption must be zero at break-even"
                );
            }

            if (result.originalCapital()
                    .compareTo(totalCapitalReturn) != 0) {

                errors.add(
                        "Capital return does not equal original capital"
                );
            }

            remainingBalance =
                    result.netRealization()
                            .subtract(totalCapitalReturn);
        }

        boolean balanced =
                remainingBalance.signum() == 0
                        && errors.isEmpty();

        return new SyirkahLiquidationReconciliation(
                result.originalCapital(),
                result.netRealization(),
                totalProfitDistribution,
                totalLossAbsorption,
                totalCapitalReturn,
                remainingBalance,
                balanced,
                errors
        );
    }

    private static void requireNonNegative(
            BigDecimal value,
            String name
    ) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(
                    name + " must not be negative"
            );
        }
    }
}
```

### Important refinement

This calculator assumes the **whole net realization is ultimately allocated between capital return and result allocation**.

If the contract has retained business assets, restricted reserves, unresolved liabilities, or another disposition mechanism, that must be represented explicitly rather than allowing this calculator to silently balance by ignoring it.

---

# 3. Allocation service implementation

```java
package tech.kayys.syirkah.accounting.application.syirkah.liquidation;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.syirkah.SyirkahContract;
import tech.kayys.syirkah.accounting.domain.syirkah.close.SyirkahCloseSnapshot;
import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class SyirkahLiquidationAllocationServiceImpl
        implements SyirkahLiquidationAllocationService {

    private final SyirkahLiquidationAllocationRepository allocationRepository;
    private final SyirkahLiquidationResultPolicyResolver resultPolicyResolver;
    private final SyirkahLiquidationReconciliationCalculator reconciliationCalculator;
    private final SyirkahPartnerAllocationService partnerAllocationService;
    private final SyirkahCapitalReturnCalculator capitalReturnCalculator;

    public SyirkahLiquidationAllocationServiceImpl(
            SyirkahLiquidationAllocationRepository allocationRepository,
            SyirkahLiquidationResultPolicyResolver resultPolicyResolver,
            SyirkahLiquidationReconciliationCalculator reconciliationCalculator,
            SyirkahPartnerAllocationService partnerAllocationService,
            SyirkahCapitalReturnCalculator capitalReturnCalculator
    ) {
        this.allocationRepository =
                Objects.requireNonNull(allocationRepository);

        this.resultPolicyResolver =
                Objects.requireNonNull(resultPolicyResolver);

        this.reconciliationCalculator =
                Objects.requireNonNull(reconciliationCalculator);

        this.partnerAllocationService =
                Objects.requireNonNull(partnerAllocationService);

        this.capitalReturnCalculator =
                Objects.requireNonNull(capitalReturnCalculator);
    }

    @Override
    public Uni<SyirkahLiquidationAllocation> allocate(
            SyirkahLiquidation liquidation,
            SyirkahLiquidationResult result,
            SyirkahCloseSnapshot openingSnapshot,
            SyirkahContract.Version contractVersion
    ) {
        Objects.requireNonNull(liquidation);
        Objects.requireNonNull(result);
        Objects.requireNonNull(openingSnapshot);
        Objects.requireNonNull(contractVersion);

        return allocationRepository.findByLiquidationId(
                        liquidation.getTenantId(),
                        liquidation.getLedgerId(),
                        liquidation.getLiquidationId()
                )
                .chain(existing -> {
                    if (existing.isPresent()) {
                        return Uni.createFrom().item(existing.get());
                    }

                    return calculateAndPersist(
                            liquidation,
                            result,
                            openingSnapshot,
                            contractVersion
                    );
                });
    }

    private Uni<SyirkahLiquidationAllocation> calculateAndPersist(
            SyirkahLiquidation liquidation,
            SyirkahLiquidationResult result,
            SyirkahCloseSnapshot openingSnapshot,
            SyirkahContract.Version contractVersion
    ) {
        return partnerAllocationService
                .allocate(
                        result,
                        contractVersion
                )
                .chain(partnerAllocation ->
                        capitalReturnCalculator.calculate(
                                result,
                                openingSnapshot,
                                partnerAllocation
                        )
                        .map(capitalReturns -> {

                            BigDecimal totalProfit =
                                    partnerAllocation.totalProfit();

                            BigDecimal totalLoss =
                                    partnerAllocation.totalLoss();

                            BigDecimal totalCapitalReturn =
                                    capitalReturns.totalAmount();

                            SyirkahLiquidationReconciliation reconciliation =
                                    reconciliationCalculator.calculate(
                                            result,
                                            totalProfit,
                                            totalLoss,
                                            totalCapitalReturn
                                    );

                            if (!reconciliation.balanced()) {
                                throw new IllegalStateException(
                                        "Liquidation reconciliation failed: "
                                                + String.join(
                                                "; ",
                                                reconciliation.errors()
                                        )
                                );
                            }

                            SyirkahLiquidationAllocation allocation =
                                    new SyirkahLiquidationAllocation(
                                            liquidation.getLiquidationId(),
                                            result,
                                            partnerAllocation
                                                    .profitDistributions(),
                                            capitalReturns.obligations(),
                                            partnerAllocation
                                                    .lossResponsibilities()
                                    );

                            return allocation;
                        })
                )
                .chain(allocation ->
                        allocationRepository.save(
                                liquidation.getTenantId(),
                                liquidation.getLedgerId(),
                                allocation
                        )
                        .replaceWith(allocation)
                );
    }
}
```

There is one deliberate architectural choice here:

**the allocation service does not post accounting journals.**

It creates a business result that can subsequently go through:

```text
allocation
   ↓
approval
   ↓
accounting policy
   ↓
journal
   ↓
settlement
```

---

# 4. Partner allocation port

Rather than embedding another profit-sharing implementation here:

```java
package tech.kayys.syirkah.accounting.application.syirkah.liquidation;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.syirkah.SyirkahContract;
import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.SyirkahLiquidationResult;

public interface SyirkahPartnerAllocationService {

    Uni<PartnerAllocationResult> allocate(
            SyirkahLiquidationResult result,
            SyirkahContract.Version contractVersion
    );
}
```

Result:

```java
package tech.kayys.syirkah.accounting.application.syirkah.liquidation;

import tech.kayys.syirkah.accounting.domain.syirkah.distribution.PartnerDistributionObligation;
import tech.kayys.syirkah.accounting.domain.syirkah.loss.SyirkahLossResponsibility;

import java.math.BigDecimal;
import java.util.List;

public record PartnerAllocationResult(
        List<PartnerDistributionObligation> profitDistributions,
        List<SyirkahLossResponsibility> lossResponsibilities,
        BigDecimal totalProfit,
        BigDecimal totalLoss
) {

    public PartnerAllocationResult {
        profitDistributions =
                List.copyOf(
                        profitDistributions == null
                                ? List.of()
                                : profitDistributions
                );

        lossResponsibilities =
                List.copyOf(
                        lossResponsibilities == null
                                ? List.of()
                                : lossResponsibilities
                );

        totalProfit =
                totalProfit == null
                        ? BigDecimal.ZERO
                        : totalProfit;

        totalLoss =
                totalLoss == null
                        ? BigDecimal.ZERO
                        : totalLoss;
    }
}
```

This lets the implementation delegate to the existing:

```text
SyirkahResultCalculator
        +
LossAllocationPolicyResolver
```

rather than creating a second calculation engine.

---

# 5. Capital return calculator

```java
package tech.kayys.syirkah.accounting.application.syirkah.liquidation;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.syirkah.close.PartnerClosePositionSnapshot;
import tech.kayys.syirkah.accounting.domain.syirkah.close.SyirkahCloseSnapshot;
import tech.kayys.syirkah.accounting.domain.syirkah.liquidation.SyirkahLiquidationResult;
import tech.kayys.syirkah.accounting.domain.syirkah.capital.PartnerCapitalReturnObligation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class SyirkahCapitalReturnCalculator {

    public Uni<CapitalReturnResult> calculate(
            SyirkahLiquidationResult result,
            SyirkahCloseSnapshot openingSnapshot,
            PartnerAllocationResult allocation
    ) {
        List<PartnerCapitalReturnObligation> obligations =
                openingSnapshot.positions()
                        .stream()
                        .map(position ->
                                calculateForPartner(
                                        result,
                                        position,
                                        allocation
                                )
                        )
                        .filter(java.util.Objects::nonNull)
                        .toList();

        BigDecimal total =
                obligations.stream()
                        .map(PartnerCapitalReturnObligation::getCapitalAmount)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        return Uni.createFrom().item(
                new CapitalReturnResult(
                        obligations,
                        total
                )
        );
    }

    private PartnerCapitalReturnObligation calculateForPartner(
            SyirkahLiquidationResult result,
            PartnerClosePositionSnapshot position,
            PartnerAllocationResult allocation
    ) {
        BigDecimal capital =
                position.openingCapital()
                        .add(position.capitalContributions())
                        .subtract(position.capitalReturns())
                        .subtract(position.capitalImpairments());

        BigDecimal loss =
                allocation.lossResponsibilities()
                        .stream()
                        .filter(r ->
                                r.partyId().equals(position.partyId())
                        )
                        .map(r -> r.amount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        /*
         * Ordinary loss allocation reduces the capital available
         * for return. A responsibility determination is treated
         * separately and should not be confused with ordinary
         * capital impairment.
         *
         * The concrete policy may override this calculation.
         */
        BigDecimal returnable =
                capital.subtract(loss);

        if (returnable.signum() <= 0) {
            return null;
        }

        return new PartnerCapitalReturnObligation(
                UUID.randomUUID().toString(),
                result.liquidationId(),
                position.partyId(),
                returnable
        );
    }

    public record CapitalReturnResult(
            List<PartnerCapitalReturnObligation> obligations,
            BigDecimal totalAmount
    ) {
        public CapitalReturnResult {
            obligations = List.copyOf(obligations);
        }
    }
}
```

### One architectural correction

The `loss` calculation above should eventually distinguish:

```text
NORMAL_BUSINESS_LOSS
        ↓
capital absorption

MANAGER_NEGLIGENCE
        ↓
responsibility / recovery
```

We should **not** automatically subtract every `SyirkahLossResponsibility` from capital.

So the production version should consume a richer `PartnerLossAllocationResult`, containing:

```text
capitalLoss
responsibilityAmount
recoveryAmount
```

rather than treating every loss-related amount as capital impairment.

That prevents the exact semantic error we wanted to avoid.

---

# 6. Liquidation state transition

The liquidation aggregate should only move forward after allocation is reconciled.

Add:

```java
public void completeAllocation(
        String allocationId,
        String completedBy
) {
    requireActor(completedBy);

    if (status != LiquidationStatus.RESULT_DETERMINED) {
        throw new IllegalStateException(
                "Liquidation allocation requires RESULT_DETERMINED status"
        );
    }

    if (allocationId == null || allocationId.isBlank()) {
        throw new IllegalArgumentException(
                "Allocation ID cannot be blank"
        );
    }

    this.status = LiquidationStatus.DISTRIBUTION;
}
```

But I would **not** yet call this `COMPLETED`.

The lifecycle remains:

```text
RESULT_DETERMINED
       ↓
DISTRIBUTION
       ↓
profit/loss obligations approved
       ↓
capital return obligations approved
       ↓
accounting posted
       ↓
capital returned / distributions settled
       ↓
COMPLETED
```

---

# 7. Critical invariant

The final completion method should enforce:

```java
public void complete(
        SyirkahLiquidationReconciliation reconciliation,
        String completedBy
) {
    requireActor(completedBy);

    if (status != LiquidationStatus.DISTRIBUTION) {
        throw new IllegalStateException(
                "Liquidation can only complete from DISTRIBUTION"
        );
    }

    if (reconciliation == null || !reconciliation.balanced()) {
        throw new IllegalStateException(
                "Cannot complete an unbalanced liquidation"
        );
    }

    // Additional obligation-status checks happen here.

    this.status = LiquidationStatus.COMPLETED;
    this.completedBy = completedBy;
    this.completedAt = Instant.now();
}
```

So the domain itself protects against an application-service mistake.

---

## Resulting architecture

We now have a clean chain:

```text
Locked Syirkah Close Snapshot
              │
              ▼
       Liquidation Result
              │
              ├───────────────┐
              ▼               ▼
      Profit/Loss         Capital Position
       Allocation               │
              │                 ▼
              │          Capital Return
              │            Obligation
              ▼
     Profit Distribution
        Obligation
              │
              └───────────────┐
                              ▼
                  Final Reconciliation
                              │
                       balanced = true
                              │
                              ▼
                    Approval / Accounting
                              │
                              ▼
                         Settlement
                              │
                              ▼
                         COMPLETED
```

And importantly:

```text
Profit allocation ≠ capital return
Loss allocation ≠ loss responsibility
Allocation ≠ accounting
Accounting ≠ cash settlement
```

That separation is the foundation we need before adding the **liquidation accounting policies and journal posting** layer next.


//////////////


Next, the natural layer is **liquidation accounting and posting**, while keeping accounting policy separate from the liquidation domain.

### 1. Capital-return accounting policy

```java
public interface SyirkahCapitalReturnAccountingPolicy {

    JournalMapping map(
            PartnerCapitalReturnObligation obligation);

    record JournalMapping(
            List<JournalLineMapping> lines,
            String description
    ) {
        public JournalMapping {
            if (lines == null || lines.isEmpty()) {
                throw new IllegalArgumentException("Journal mapping must contain lines");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("Description cannot be blank");
            }
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
            if (type == null) {
                throw new IllegalArgumentException("Line type cannot be null");
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

The important point is that **capital return is not an expense**.

A typical policy may eventually map:

```text
Debit  : Partner Capital / Partner Current Account
Credit : Capital Return Payable
```

and the actual payment would be a separate settlement:

```text
Debit  : Capital Return Payable
Credit : Cash / Bank
```

But those account choices belong to the accounting policy/configuration, not the liquidation aggregate.

---

### 2. Liquidation accounting policy

Rather than making one giant policy, keep the three accounting events explicit:

```java
public interface SyirkahLiquidationAccountingPolicy {

    JournalMapping mapProfitDistribution(
            String liquidationId,
            String calculationId,
            List<PartnerDistributionObligation> obligations);

    JournalMapping mapLossRecovery(
            String liquidationId,
            List<LossRecoveryObligation> obligations);

    JournalMapping mapCapitalReturn(
            String liquidationId,
            List<PartnerCapitalReturnObligation> obligations);

    record JournalMapping(
            List<JournalLineMapping> lines,
            String description
    ) {
        public JournalMapping {
            if (lines == null || lines.isEmpty()) {
                throw new IllegalArgumentException("Journal mapping must contain lines");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("Description cannot be blank");
            }
        }
    }

    record JournalLineMapping(
            String accountId,
            LineType type,
            String amount,
            String currencyCode,
            String description
    ) {}

    enum LineType {
        DEBIT,
        CREDIT
    }
}
```

This keeps these concepts separate:

```text
Liquidation result
      │
      ├── Profit
      │     └── Profit distribution
      │
      ├── Loss
      │     ├── Capital loss
      │     └── Responsibility/recovery
      │
      └── Capital return
             │
             └── Settlement later
```

---

### 3. Posting service

Now introduce an application-level posting boundary:

```java
public interface SyirkahLiquidationPostingService {

    Uni<JournalEntryId> postProfitDistribution(
            SyirkahLiquidationPostingRequest request);

    Uni<List<JournalEntryId>> postLossRecovery(
            SyirkahLiquidationPostingRequest request);

    Uni<List<JournalEntryId>> postCapitalReturns(
            SyirkahLiquidationPostingRequest request);
}
```

Request:

```java
public record SyirkahLiquidationPostingRequest(
        String tenantId,
        String ledgerId,
        String liquidationId,
        String calculationId,
        String currencyCode,
        String postedBy
) {
    public SyirkahLiquidationPostingRequest {
        requireNonBlank(tenantId, "Tenant ID");
        requireNonBlank(ledgerId, "Ledger ID");
        requireNonBlank(liquidationId, "Liquidation ID");
        requireNonBlank(currencyCode, "Currency code");
        requireNonBlank(postedBy, "Posted by");
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
    }
}
```

---

### 4. Source identifiers

Every liquidation accounting event needs a deterministic source.

```java
public final class SyirkahLiquidationSourceTypes {

    private SyirkahLiquidationSourceTypes() {
    }

    public static final String PROFIT_DISTRIBUTION =
            "SYIRKAH_LIQUIDATION_PROFIT_DISTRIBUTION";

    public static final String LOSS_RECOVERY =
            "SYIRKAH_LIQUIDATION_LOSS_RECOVERY";

    public static final String CAPITAL_RETURN =
            "SYIRKAH_LIQUIDATION_CAPITAL_RETURN";
}
```

The source ID should be deterministic, for example:

```text
SYIRKAH_LIQUIDATION_PROFIT_DISTRIBUTION:{liquidationId}
SYIRKAH_LIQUIDATION_LOSS_RECOVERY:{responsibilityId}
SYIRKAH_LIQUIDATION_CAPITAL_RETURN:{obligationId}
```

This makes retries safe.

---

### 5. Do not mark obligations POSTED before GL succeeds

The sequence must be:

```text
Load obligation
      ↓
Validate APPROVED
      ↓
Build accounting policy mapping
      ↓
Post journal
      ↓
Journal POSTED successfully
      ↓
markPosted(journalEntryId)
      ↓
Persist obligation
```

Never:

```text
markPosted()
   ↓
post journal
```

because a journal failure would leave the domain claiming that money was accounted for when it was not.

---

### 6. Capital return posting

The service should therefore look conceptually like:

```java
public Uni<JournalEntryId> postCapitalReturn(
        PartnerCapitalReturnObligation obligation,
        SyirkahLiquidationPostingRequest request) {

    if (obligation.getStatus()
            != PartnerCapitalReturnObligation.Status.APPROVED) {
        return Uni.createFrom().failure(
                new IllegalStateException(
                        "Capital return obligation must be approved"));
    }

    String sourceId = obligation.getObligationId();

    return journalEntryRepository.findBySource(
            request.tenantId(),
            request.ledgerId(),
            SyirkahLiquidationSourceTypes.CAPITAL_RETURN,
            sourceId
    ).chain(existing -> {

        if (existing.isPresent()) {
            return Uni.createFrom().item(
                    JournalEntryId.of(existing.get().getId()));
        }

        SyirkahLiquidationAccountingPolicy.JournalMapping mapping =
                accountingPolicy.mapCapitalReturn(
                        request.liquidationId(),
                        List.of(obligation));

        PostJournalEntryCommand command =
                buildCommand(
                        request,
                        SyirkahLiquidationSourceTypes.CAPITAL_RETURN,
                        sourceId,
                        mapping);

        return generalLedgerService
                .postJournalEntry(command)
                .chain(journalEntryId -> {
                    obligation.markPosted(
                            journalEntryId.value(),
                            request.postedBy());

                    return obligationRepository
                            .save(obligation)
                            .replaceWith(journalEntryId);
                });
    });
}
```

The exact `JournalEntryId` conversion should follow the existing value-object API rather than assuming a factory that may not exist.

### 7. Database invariant

For each liquidation accounting source:

```sql
CREATE UNIQUE INDEX uq_journal_source
ON journal_entry (
    tenant_id,
    ledger_id,
    source_type,
    source_id
);
```

This is especially important because:

```text
HTTP retry
   ↓
application retry
   ↓
worker retry
   ↓
another application instance
```

must never produce duplicate financial postings.

The in-memory `IdempotencyGuard` is not sufficient for this.

---

### 8. Final liquidation accounting lifecycle

We now have a clean end-to-end model:

```text
SyirkahLiquidation
        │
        ▼
Asset Realization
        │
        ▼
Liability Settlement
        │
        ▼
Final Reconciliation
        │
        ▼
Liquidation Result
        │
        ├───────────────┐
        ▼               ▼
Profit              Loss
        │               │
        ▼               ├── Capital Loss
Profit Allocation       │
        │               └── Responsibility
        ▼                       │
Distribution             Recovery Obligation
        │
        └───────────────┐
                        ▼
                 Capital Return
                        │
                        ▼
                Accounting Posting
                        │
                        ▼
                 Cash Settlement
                        │
                        ▼
                 Liquidation Complete
```

The next layer after this should be **settlement**, because we now have to distinguish *accounting posted* from *actual money paid/received*, including partial settlement and reconciliation.

//////////////


Yes. Before continuing implementation, I would checkpoint the architecture so we don't drift or accidentally rebuild existing ERP/accounting capabilities.

## Checkpoint — Syirkah extension

### Current position

We started with the existing Syirkah/accounting implementation and extended it through this chain:

```text
Existing Accounting / ERP
        │
        ├── General Ledger
        ├── Journal
        ├── Fiscal Close
        ├── Reporting
        └── Islamic Accounting capability
                │
                ▼
        Syirkah Contract
                │
                ▼
        Financial Result
                │
                ▼
        Profit / Loss Allocation
                │
                ├── Profit Distribution
                └── Loss Allocation
                        │
                        ├── Normal Business Loss
                        └── Responsibility / Recovery
                │
                ▼
        Partner Accounts / Movements
                │
                ▼
        Period Close Snapshot
                │
                ▼
        Contract Transition
                │
                ├── Partner Entry
                ├── Partner Exit
                ├── Replacement
                ├── Capital Change
                └── Renewal
                │
                ▼
        Full Liquidation
                │
                ├── Asset Realization
                ├── Liability Settlement
                ├── Final Reconciliation
                ├── Final Result
                ├── Distribution
                └── Capital Return
                │
                ▼
        Accounting Posting
                │
                ▼
        Cash Settlement
```

### What is already conceptually covered

| Area | Status |
|---|---|
| Syirkah types | ✅ |
| Parties / roles | ✅ |
| Versioned Syirkah contract | ✅ |
| Profit-sharing ratio | ✅ |
| Financial result calculation | ✅ |
| Profit allocation | ✅ |
| Loss allocation | ✅ |
| Contract-aware loss policy | ✅ |
| Mudharabah-specific responsibility | ✅ |
| Close-cycle Syirkah tasks | ✅ |
| Result calculation during close | ✅ |
| Distribution obligations | ✅ |
| Maker/checker approval | ✅ |
| Distribution accounting policy | ✅ |
| Partner account movements | ✅ |
| Capital/current-account distinction | ✅ |
| Partner close-position snapshot | ✅ |
| Reconciliation | ✅ |
| Opening-position derivation | ✅ |
| Contract transition | ✅ |
| Partner replacement | ✅ |
| Transfer restrictions | ✅ |
| Full liquidation aggregate | ✅ |
| Liquidation realization | ✅ |
| Liability settlement | ✅ |
| Liquidation result policy | ✅ |
| Liquidation allocation | ✅ |
| Capital-return obligations | ✅ |
| Liquidation accounting boundary | 🔄 Current |
| Actual settlement | ⏭️ Next |

The important architectural checkpoint is that **we have not turned Syirkah into another accounting engine**. It is a domain/business layer that uses the existing accounting infrastructure.

---

# Roadmap from here

I suggest we organize the remaining work into **8 phases**.

## Phase 1 — Liquidation Accounting Completion

**Current phase**

Finish:

```text
Liquidation
   ↓
Accounting Policy
   ↓
Journal Posting
   ↓
Posted Obligations
```

Remaining:

- liquidation profit-distribution posting
- loss-recovery posting
- capital-return posting
- source/idempotency guarantees
- transaction boundaries
- accounting reconciliation
- reversal handling

**Goal:** every liquidation financial event can safely reach the existing GL.

---

# Phase 2 — Settlement

Separate accounting from actual money movement.

### Introduce

```text
SettlementObligation
SettlementInstruction
Settlement
SettlementAllocation
SettlementStatus
```

Lifecycle:

```text
CALCULATED
    ↓
APPROVED
    ↓
ACCOUNTING_POSTED
    ↓
SETTLEMENT_PENDING
    ↓
PARTIALLY_SETTLED
    ↓
SETTLED
```

This allows:

```text
Profit allocation       = 10m
Accounting posted       = 10m
Cash actually paid      = 4m
Outstanding settlement  = 6m
```

without corrupting the Syirkah allocation.

Also support:

- bank/cash settlement
- partial settlement
- multiple payments
- settlement reversal
- external payment reference
- reconciliation

---

# Phase 3 — Complete Close Integration

Connect everything properly to `CloseCycle`.

Target:

```text
Close
 │
 ├── Existing accounting tasks
 │
 ├── Syirkah result
 │
 ├── Syirkah allocation
 │
 ├── Distribution
 │
 ├── Loss responsibility, if applicable
 │
 ├── Snapshot
 │
 └── Lock
```

Important:

**Not every Syirkah close should require every possible workflow.**

For example:

```text
PROFIT
  → distribution

NORMAL LOSS
  → loss allocation

RESPONSIBILITY LOSS
  → responsibility/recovery workflow

BREAK EVEN
  → no distribution
```

This keeps the close process conditional instead of creating unnecessary mandatory tasks.

---

# Phase 4 — Syirkah Statement / Reporting

Once the transactional model is stable, build read models.

Examples:

### Partner statement

```text
Opening position
+ Capital contribution
+ Profit allocation
- Loss allocation
- Capital return
- Withdrawal
= Closing position

Cash settlements
Outstanding obligations
```

### Syirkah statement

```text
Revenue
Direct costs
Operating costs
Other costs
Net result

Profit allocation
Loss allocation
Capital position
Partner positions
```

### Contract lifecycle statement

```text
Contract
 ├── versions
 ├── partners
 ├── capital
 ├── results
 ├── distributions
 ├── transitions
 └── liquidation
```

These should be **projection/reporting models**, not new transactional aggregates.

---

# Phase 5 — Transition / Partner Lifecycle

Then complete the operational lifecycle around changing partners.

```text
Existing Contract
       ↓
Close
       ↓
Snapshot
       ↓
Settlement
       ↓
Partner Transition
       ↓
New Contract
       ↓
Opening Position
```

Cover:

- partner entry
- partner exit
- partner replacement
- capital increase
- capital reduction
- contract renewal
- retained business result
- transfer between partners

Particularly important:

> A new partner should not simply be inserted into the old active contract and ledger state.

The old position needs to be frozen first.

---

# Phase 6 — Full Liquidation / Termination

We have already designed most of this.

Finish the complete lifecycle:

```text
START
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
SETTLEMENT
 ↓
COMPLETED
```

Then add:

- cancellation rules
- reversal
- reopening restrictions
- final immutable snapshot
- contract termination
- final partner statements.

---

# Phase 7 — Compliance, Audit & Reliability

This is important because Syirkah creates financial/legal state transitions.

Add:

### Audit trail

Every important transition:

```text
who
when
what
previous state
new state
reason
reference
```

### Idempotency

Database-level uniqueness for:

```text
tenant
ledger
source type
source id
```

### Concurrency

Protect:

- close
- allocation
- approval
- posting
- settlement
- liquidation completion

against simultaneous workers/users.

### Immutability

Once something is financially posted:

```text
do not edit
do not delete
```

Use reversal/correction events.

---

# Phase 8 — Integration / Ecosystem

Only after the domain is stable.

Expose Syirkah through:

```text
REST
Events
Messaging
Webhooks
Integration adapters
```

Potential events:

```text
SyirkahContractCreated
SyirkahContractVersionActivated
SyirkahResultCalculated
SyirkahAllocationApproved
SyirkahDistributionPosted
SyirkahLossResponsibilityDetermined
SyirkahTransitionStarted
SyirkahLiquidationStarted
SyirkahLiquidationCompleted
SyirkahSettlementCompleted
```

This makes the capability usable by:

- ERP
- POS
- ecommerce
- investment systems
- cooperative systems
- Islamic finance systems
- external partner platforms.

---

# Bigger architecture roadmap

The overall target is therefore:

```text
                    SYIRKAH PLATFORM
                           │
          ┌────────────────┼────────────────┐
          │                │                │
       Contract         Accounting       Operations
          │                │                │
          │                │                ├── Settlement
          │                │                ├── Transition
          │                │                └── Liquidation
          │                │
          │                ├── GL
          │                ├── Close
          │                ├── Reporting
          │                └── Islamic Accounting
          │
          ├── Parties
          ├── Capital
          ├── Nisbah
          └── Akad
                           │
                           ▼
                    Domain Events
                           │
             ┌─────────────┼─────────────┐
             ▼             ▼             ▼
          Reporting    Integration     Audit
```

## The key design rule going forward

I would keep enforcing this separation:

```text
BUSINESS FACT
      ↓
DOMAIN AGGREGATE
      ↓
BUSINESS OBLIGATION / MOVEMENT
      ↓
ACCOUNTING POLICY
      ↓
GENERAL LEDGER
      ↓
SETTLEMENT
```

**Never shortcut:**

```text
Syirkah domain → directly manipulate Account.balance
```

and never make accounting assumptions inside the Sharia domain unless the rule is genuinely part of the business contract semantics.

### Recommended implementation order

So from the current checkpoint:

**Now**
1. Finish liquidation accounting posting
2. Add transaction/idempotency guarantees
3. Add settlement model

**Then**
4. Complete close orchestration
5. Complete reporting/read models
6. Complete transition lifecycle
7. Finish full termination lifecycle
8. Audit/reliability
9. Integration/events

That gives us a stable path without disturbing the substantial ERP/accounting implementation that already exists.

///////////////


The existing framework has a payment/settlement ecosystem, but I would **not couple Syirkah settlement directly to the gateway `Payment` model**. Syirkah needs a domain settlement obligation first; payment gateways become adapters.

## Next: Syirkah settlement layer

### 1. Generic settlement status

```java
public enum SyirkahSettlementStatus {
    PENDING,
    PARTIALLY_SETTLED,
    SETTLED,
    FAILED,
    CANCELLED
}
```

### 2. Settlement obligation

This is the financial claim that must actually be paid/received.

```java
public final class SyirkahSettlementObligation {

    private final String settlementId;
    private final String tenantId;
    private final String ledgerId;

    private final String sourceType;
    private final String sourceId;

    private final String partyId;
    private final BigDecimal obligatedAmount;
    private final String currencyCode;

    private BigDecimal settledAmount;
    private SyirkahSettlementStatus status;

    private String approvedBy;
    private Instant approvedAt;

    public SyirkahSettlementObligation(
            String settlementId,
            String tenantId,
            String ledgerId,
            String sourceType,
            String sourceId,
            String partyId,
            BigDecimal obligatedAmount,
            String currencyCode) {

        requireText(settlementId, "Settlement ID");
        requireText(tenantId, "Tenant ID");
        requireText(ledgerId, "Ledger ID");
        requireText(sourceType, "Source type");
        requireText(sourceId, "Source ID");
        requireText(partyId, "Party ID");
        requireText(currencyCode, "Currency code");

        if (obligatedAmount == null || obligatedAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Obligated amount must be positive");
        }

        this.settlementId = settlementId;
        this.tenantId = tenantId;
        this.ledgerId = ledgerId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.partyId = partyId;
        this.obligatedAmount = obligatedAmount;
        this.currencyCode = currencyCode;

        this.settledAmount = BigDecimal.ZERO;
        this.status = SyirkahSettlementStatus.PENDING;
    }

    public void approve(String approver) {
        requireText(approver, "Approver");

        if (status != SyirkahSettlementStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending settlements can be approved");
        }

        this.approvedBy = approver;
        this.approvedAt = Instant.now();
    }

    public void recordSettlement(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Settlement amount must be positive");
        }

        if (approvedBy == null) {
            throw new IllegalStateException(
                    "Settlement must be approved before settlement");
        }

        if (status == SyirkahSettlementStatus.CANCELLED
                || status == SyirkahSettlementStatus.SETTLED) {
            throw new IllegalStateException(
                    "Settlement cannot be recorded in status " + status);
        }

        BigDecimal newSettledAmount = settledAmount.add(amount);

        if (newSettledAmount.compareTo(obligatedAmount) > 0) {
            throw new IllegalArgumentException(
                    "Settlement exceeds obligated amount");
        }

        settledAmount = newSettledAmount;

        status = settledAmount.compareTo(obligatedAmount) == 0
                ? SyirkahSettlementStatus.SETTLED
                : SyirkahSettlementStatus.PARTIALLY_SETTLED;
    }

    public void cancel() {
        if (status == SyirkahSettlementStatus.SETTLED) {
            throw new IllegalStateException(
                    "Settled obligation cannot be cancelled");
        }

        status = SyirkahSettlementStatus.CANCELLED;
    }

    public BigDecimal getOutstandingAmount() {
        return obligatedAmount.subtract(settledAmount);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
    }

    public String getSettlementId() {
        return settlementId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getLedgerId() {
        return ledgerId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getPartyId() {
        return partyId;
    }

    public BigDecimal getObligatedAmount() {
        return obligatedAmount;
    }

    public BigDecimal getSettledAmount() {
        return settledAmount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public SyirkahSettlementStatus getStatus() {
        return status;
    }
}
```

The critical invariant is:

```text
settledAmount <= obligatedAmount
```

---

## 3. Settlement must reference its business source

Examples:

```text
SYIRKAH_PROFIT_DISTRIBUTION
        ↓
distribution obligation
        ↓
settlement obligation
```

```text
SYIRKAH_CAPITAL_RETURN
        ↓
capital return obligation
        ↓
settlement obligation
```

```text
SYIRKAH_LOSS_RECOVERY
        ↓
loss recovery obligation
        ↓
settlement obligation
```

Therefore we don't create a generic unexplained payment.

---

## 4. Repository

```java
public interface SyirkahSettlementRepository {

    Uni<Optional<SyirkahSettlementObligation>> findById(
            String tenantId,
            String ledgerId,
            String settlementId);

    Uni<List<SyirkahSettlementObligation>> findBySource(
            String tenantId,
            String ledgerId,
            String sourceType,
            String sourceId);

    Uni<List<SyirkahSettlementObligation>> findOutstandingByParty(
            String tenantId,
            String ledgerId,
            String partyId);

    Uni<Void> save(
            SyirkahSettlementObligation obligation);
}
```

Database uniqueness:

```sql
UNIQUE (
    tenant_id,
    ledger_id,
    source_type,
    source_id,
    party_id
)
```

This prevents duplicate obligations when a worker retries settlement preparation.

---

## 5. Settlement adapter

Now separate the domain from payment/banking providers.

```java
public interface SyirkahSettlementProcessor {

    Uni<SettlementResult> settle(
            SettlementRequest request);

    Uni<SettlementStatusResult> checkStatus(
            String externalReference);

    record SettlementRequest(
            String settlementId,
            String partyId,
            BigDecimal amount,
            String currencyCode,
            String paymentReference
    ) {}

    record SettlementResult(
            boolean accepted,
            String externalReference,
            String failureReason
    ) {}

    record SettlementStatusResult(
            SettlementProcessingStatus status,
            String externalReference
    ) {}

    enum SettlementProcessingStatus {
        PENDING,
        SUCCESS,
        FAILED
    }
}
```

Adapters can then connect to:

```text
SyirkahSettlementProcessor
        │
        ├── Bank transfer
        ├── Payment gateway
        ├── Virtual account
        ├── Wallet
        ├── Treasury system
        └── External partner system
```

The existing payment infrastructure can become one of those adapters rather than becoming part of the Syirkah domain.

---

## 6. Settlement orchestration

```java
public interface SyirkahSettlementService {

    Uni<SyirkahSettlementObligation> prepare(
            SettlementPreparationRequest request);

    Uni<SyirkahSettlementObligation> approve(
            String settlementId,
            String approver);

    Uni<SyirkahSettlementObligation> execute(
            String settlementId,
            SettlementExecutionRequest request);

    Uni<SyirkahSettlementObligation> reconcile(
            String settlementId);
}
```

The lifecycle becomes:

```text
Allocation
    ↓
Accounting obligation
    ↓
Accounting POSTED
    ↓
Settlement obligation
    ↓
APPROVED
    ↓
Payment instruction
    ↓
External processor
    ↓
Settlement result
    ↓
PARTIALLY_SETTLED / SETTLED
```

---

## 7. Very important: accounting and settlement are different

For example:

```text
Profit allocation       10,000,000
Accounting posted       10,000,000
Settlement obligation   10,000,000
Paid                     4,000,000
Outstanding              6,000,000
```

The partner's profit allocation remains **10 million**.

The settlement is only:

```text
4 million paid
6 million outstanding
```

We must not mutate the original allocation to `4m`.

---

## 8. Capital return follows the same separation

```text
Capital return obligation
        10m
          │
          ├── Accounting posted
          │
          └── Settlement
                 │
                 ├── 3m paid
                 └── 7m outstanding
```

This is particularly important for liquidation because **capital return is not equivalent to cash payment**.

---

## 9. Settlement completion invariant

A liquidation cannot become `COMPLETED` merely because accounting was posted.

It should require:

```text
Liquidation result determined
        ↓
Allocation reconciled
        ↓
Accounting posted
        ↓
Capital-return obligations reconciled
        ↓
Settlement policy satisfied
        ↓
Liquidation COMPLETED
```

And the settlement policy should be configurable because some implementations may allow:

```text
COMPLETED_WITH_OUTSTANDING_SETTLEMENT
```

while others may require:

```text
ALL_SETTLED
```

We should **not hardcode that choice into `SyirkahLiquidation`**.

The next logical step after this is therefore to introduce **`SyirkahSettlementCompletionPolicy` + settlement reconciliation**, then wire it into the liquidation completion state.


/////////////


Next is **settlement completion policy + reconciliation**, then we can safely wire it into liquidation completion.

### 1. Settlement completion policy

```java id="q4m7sx"
public interface SyirkahSettlementCompletionPolicy {

    CompletionDecision evaluate(
            SyirkahLiquidation liquidation,
            List<SyirkahSettlementObligation> obligations);

    record CompletionDecision(
            boolean allowed,
            String reason
    ) {
        public static CompletionDecision allowed() {
            return new CompletionDecision(true, "Settlement requirements satisfied");
        }

        public static CompletionDecision blocked(String reason) {
            return new CompletionDecision(false, reason);
        }
    }
}
```

A default strict policy:

```java id="m9c2rk"
public final class AllSettlementsRequiredPolicy
        implements SyirkahSettlementCompletionPolicy {

    @Override
    public CompletionDecision evaluate(
            SyirkahLiquidation liquidation,
            List<SyirkahSettlementObligation> obligations) {

        if (obligations == null || obligations.isEmpty()) {
            return CompletionDecision.allowed();
        }

        List<SyirkahSettlementObligation> outstanding =
                obligations.stream()
                        .filter(obligation ->
                                obligation.getStatus()
                                        != SyirkahSettlementStatus.SETTLED)
                        .toList();

        if (!outstanding.isEmpty()) {
            return CompletionDecision.blocked(
                    "Liquidation has outstanding settlement obligations");
        }

        return CompletionDecision.allowed();
    }
}
```

But we should also support a policy where the liquidation may finish with outstanding payable/receivable balances:

```java id="8zj2xp"
public final class OutstandingSettlementsAllowedPolicy
        implements SyirkahSettlementCompletionPolicy {

    @Override
    public CompletionDecision evaluate(
            SyirkahLiquidation liquidation,
            List<SyirkahSettlementObligation> obligations) {

        return CompletionDecision.allowed();
    }
}
```

That decision belongs to configuration/business policy, not the aggregate itself.

---

# 2. Settlement reconciliation

We should not simply check statuses.

We need monetary reconciliation.

```java id="v7j4qk"
public record SyirkahSettlementReconciliation(
        BigDecimal totalObligated,
        BigDecimal totalSettled,
        BigDecimal totalOutstanding,
        boolean balanced,
        List<String> errors
) {

    public SyirkahSettlementReconciliation {
        totalObligated = requireNonNegative(totalObligated);
        totalSettled = requireNonNegative(totalSettled);
        totalOutstanding = requireNonNegative(totalOutstanding);
        errors = errors == null
                ? List.of()
                : List.copyOf(errors);
    }

    private static BigDecimal requireNonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(
                    "Settlement amount cannot be negative");
        }
        return value;
    }
}
```

Calculator:

```java id="8q8n1v"
public final class SyirkahSettlementReconciliationCalculator {

    public SyirkahSettlementReconciliation calculate(
            List<SyirkahSettlementObligation> obligations) {

        BigDecimal obligated = BigDecimal.ZERO;
        BigDecimal settled = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;

        List<String> errors = new ArrayList<>();

        if (obligations == null) {
            return new SyirkahSettlementReconciliation(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    false,
                    List.of("Settlement obligations are required"));
        }

        for (SyirkahSettlementObligation obligation : obligations) {
            obligated = obligated.add(
                    obligation.getObligatedAmount());

            settled = settled.add(
                    obligation.getSettledAmount());

            outstanding = outstanding.add(
                    obligation.getOutstandingAmount());

            if (obligation.getSettledAmount()
                    .compareTo(obligation.getObligatedAmount()) > 0) {
                errors.add(
                        "Settlement exceeds obligation: "
                                + obligation.getSettlementId());
            }
        }

        BigDecimal expectedOutstanding =
                obligated.subtract(settled);

        if (expectedOutstanding.compareTo(outstanding) != 0) {
            errors.add(
                    "Settlement totals are inconsistent");
        }

        return new SyirkahSettlementReconciliation(
                obligated,
                settled,
                outstanding,
                errors.isEmpty(),
                errors);
    }
}
```

The fundamental equation is:

```text id="7f7e3h"
totalOutstanding
    =
totalObligated
    -
totalSettled
```

---

# 3. Wire it into liquidation completion

The liquidation aggregate should not know the details of payment gateways.

Instead, the application service performs:

```java id="g9c6pp"
public Uni<Void> completeLiquidation(
        String tenantId,
        String ledgerId,
        String liquidationId) {

    return liquidationRepository
            .findById(tenantId, ledgerId, liquidationId)
            .chain(optional -> {

                if (optional.isEmpty()) {
                    return Uni.createFrom().failure(
                            new IllegalArgumentException(
                                    "Liquidation not found"));
                }

                SyirkahLiquidation liquidation = optional.get();

                return settlementRepository
                        .findByLiquidationId(
                                tenantId,
                                ledgerId,
                                liquidationId)
                        .chain(obligations -> {

                            SyirkahSettlementReconciliation reconciliation =
                                    reconciliationCalculator.calculate(
                                            obligations);

                            if (!reconciliation.balanced()) {
                                return Uni.createFrom().failure(
                                        new IllegalStateException(
                                                "Settlement reconciliation failed: "
                                                        + reconciliation.errors()));
                            }

                            SyirkahSettlementCompletionPolicy.CompletionDecision decision =
                                    completionPolicy.evaluate(
                                            liquidation,
                                            obligations);

                            if (!decision.allowed()) {
                                return Uni.createFrom().failure(
                                        new IllegalStateException(
                                                decision.reason()));
                            }

                            liquidation.complete(
                                    "SYSTEM");

                            return liquidationRepository
                                    .save(liquidation);
                        });
            });
}
```

The exact repository method should follow the repository introduced for the liquidation settlement model.

---

# 4. Better lifecycle

At this point the liquidation lifecycle becomes:

```text
STARTED
   │
   ▼
ASSET_REALIZATION
   │
   ▼
LIABILITY_SETTLEMENT
   │
   ▼
RECONCILIATION
   │
   ▼
RESULT_DETERMINED
   │
   ▼
ALLOCATION
   │
   ▼
ACCOUNTING_POSTED
   │
   ▼
SETTLEMENT
   │
   ├───────────────┐
   │               │
   ▼               ▼
ALL_SETTLED    OUTSTANDING
   │               │
   │          completion policy
   │               │
   └───────┬───────┘
           ▼
       COMPLETED
```

This gives us a very useful distinction:

> **Liquidation completion is a business-policy decision based on reconciled obligations, not simply a payment status.**

---

# 5. One important correction

I would **not add `SETTLEMENT` directly as another hardcoded mandatory liquidation state** if we want Syirkah to support different operational models.

Instead:

```java
public interface SyirkahLiquidationCompletionPolicy {
    CompletionDecision evaluate(
            SyirkahLiquidation liquidation,
            SyirkahLiquidationReconciliation liquidationReconciliation,
            SyirkahSettlementReconciliation settlementReconciliation);
}
```

This lets a deployment decide whether:

```text
Policy A:
everything must be settled
```

or:

```text
Policy B:
accounting + obligations reconciled is sufficient;
unpaid amounts remain open
```

or potentially:

```text
Policy C:
capital return must be settled,
but other receivables may remain outstanding
```

That is much more extensible.

---

# 6. Next architectural checkpoint

With this layer, we now have:

```text
                    SYIRKAH
                       │
       ┌───────────────┼────────────────┐
       ▼               ▼                ▼
    Contract         Close          Liquidation
       │               │                │
       ▼               ▼                ▼
    Capital         Snapshot        Result
    Parties         Position        Allocation
    Nisbah          Reconcile           │
                                        ▼
                                  Accounting
                                        │
                                        ▼
                                   Obligation
                                        │
                                        ▼
                                    Settlement
                                        │
                                        ▼
                                  Reconciliation
```

### Next after this

The next major area should be **Syirkah reporting/read models**, not another transactional aggregate.

Specifically:

1. `SyirkahPartnerStatement`
2. `SyirkahCapitalStatement`
3. `SyirkahProfitDistributionStatement`
4. `SyirkahLossStatement`
5. `SyirkahSettlementStatement`
6. `SyirkahContractLifecycleStatement`
7. consolidated Syirkah period statement

That will give us a stable read-side model over everything we've built, without contaminating the domain aggregates with reporting concerns.


///////////////


Next is the **Syirkah reporting/read model layer**. We should keep it read-only and derive it from the frozen transactional state rather than creating another source of truth.

## 1. Partner statement

```java
public record SyirkahPartnerStatement(
        String tenantId,
        String ledgerId,
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
    public SyirkahPartnerStatement {
        requireNonNegative(openingCapital, "Opening capital");
        requireNonNegative(capitalContributions, "Capital contributions");
        requireNonNegative(capitalReturns, "Capital returns");
        requireNonNegative(capitalImpairments, "Capital impairments");
        requireNonNegative(openingCurrentAccount, "Opening current account");
        requireNonNegative(profitAllocations, "Profit allocations");
        requireNonNegative(lossAllocations, "Loss allocations");
        requireNonNegative(withdrawals, "Withdrawals");
        requireNonNegative(cashSettlements, "Cash settlements");
        requireNonNegative(outstandingSettlement, "Outstanding settlement");
    }

    private static void requireNonNegative(
            BigDecimal value,
            String field) {

        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(
                    field + " cannot be negative");
        }
    }
}
```

The report uses the same roll-forward already established:

```text
Closing capital
=
Opening capital
+ Contributions
- Returns
- Impairments
```

and:

```text
Closing current account
=
Opening current account
+ Profit allocations
- Loss allocations
- Withdrawals
```

Cash settlement stays separate.

---

## 2. Capital statement

```java
public record SyirkahCapitalStatement(
        String contractId,
        int contractVersion,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal openingCapital,
        BigDecimal contributions,
        BigDecimal returns,
        BigDecimal impairments,
        BigDecimal closingCapital
) {}
```

This is deliberately narrower than the partner statement.

It answers:

> How did the Syirkah capital position change?

It does **not** answer:

> How much profit was paid?

Those are different questions.

---

## 3. Profit distribution statement

```java
public record SyirkahProfitDistributionStatement(
        String contractId,
        int contractVersion,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalProfit,
        List<PartnerProfitAllocation> allocations,
        BigDecimal totalAllocated,
        BigDecimal totalSettled,
        BigDecimal totalOutstanding
) {

    public record PartnerProfitAllocation(
            String partyId,
            BigDecimal allocatedAmount,
            BigDecimal settledAmount,
            BigDecimal outstandingAmount
    ) {}
}
```

Reconciliation invariant:

```text
totalAllocated == sum(partner allocations)
```

and:

```text
totalOutstanding
=
totalAllocated
- totalSettled
```

---

## 4. Loss statement

Loss needs more detail than profit because we already introduced responsibility handling.

```java
public record SyirkahLossStatement(
        String contractId,
        int contractVersion,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalLoss,
        List<PartnerLossAllocation> allocations,
        List<LossResponsibilityLine> responsibilities,
        BigDecimal totalRecovered,
        BigDecimal totalOutstandingRecovery
) {

    public record PartnerLossAllocation(
            String partyId,
            BigDecimal allocatedAmount
    ) {}

    public record LossResponsibilityLine(
            String responsibilityId,
            String partyId,
            LossCause cause,
            BigDecimal amount,
            BigDecimal recoveredAmount,
            BigDecimal outstandingAmount
    ) {}
}
```

This is important because:

```text
Loss
├── normal business loss
│     └── allocation
│
└── responsibility
      └── recovery
```

The report should not collapse those into one number.

---

## 5. Settlement statement

```java
public record SyirkahSettlementStatement(
        String contractId,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<SettlementLine> lines,
        BigDecimal totalObligated,
        BigDecimal totalSettled,
        BigDecimal totalOutstanding
) {

    public record SettlementLine(
            String settlementId,
            String sourceType,
            String sourceId,
            String partyId,
            BigDecimal obligatedAmount,
            BigDecimal settledAmount,
            BigDecimal outstandingAmount,
            SyirkahSettlementStatus status
    ) {}
}
```

This gives operations a completely different view from accounting:

```text
Accounting:
    What was recognized?

Settlement:
    What was actually paid/received?
```

---

# 6. Contract lifecycle statement

This becomes useful once partner transitions and liquidation are active.

```java
public record SyirkahContractLifecycleStatement(
        String contractId,
        List<ContractLifecycleEvent> events
) {

    public record ContractLifecycleEvent(
            LocalDate date,
            String eventType,
            String referenceId,
            String description
    ) {}
}
```

Example:

```text
2026-01-01  CONTRACT_CREATED
2026-01-01  VERSION_ACTIVATED
2026-03-31  PERIOD_CLOSED
2026-03-31  PROFIT_ALLOCATED
2026-04-05  PROFIT_SETTLED
2026-06-30  PARTNER_EXIT
2026-06-30  SNAPSHOT_FROZEN
2026-07-01  CONTRACT_TERMINATED
```

This is a read model, not another domain event store.

---

# 7. Reporting provider

Rather than putting database queries into each report object:

```java
public interface SyirkahReportingService {

    Uni<SyirkahPartnerStatement> getPartnerStatement(
            String tenantId,
            String ledgerId,
            String contractId,
            String partyId,
            LocalDate periodStart,
            LocalDate periodEnd);

    Uni<SyirkahCapitalStatement> getCapitalStatement(
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate periodStart,
            LocalDate periodEnd);

    Uni<SyirkahProfitDistributionStatement> getProfitDistributionStatement(
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate periodStart,
            LocalDate periodEnd);

    Uni<SyirkahLossStatement> getLossStatement(
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate periodStart,
            LocalDate periodEnd);

    Uni<SyirkahSettlementStatement> getSettlementStatement(
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate periodStart,
            LocalDate periodEnd);

    Uni<SyirkahContractLifecycleStatement> getContractLifecycle(
            String tenantId,
            String ledgerId,
            String contractId);
}
```

---

# 8. One important architectural rule

The reporting layer should preferably read:

```text
Immutable movements
       +
Frozen close snapshots
       +
Distribution obligations
       +
Settlement obligations
       +
Liquidation snapshots
       ↓
Syirkah reporting projections
```

rather than:

```text
Current PartnerAccount
```

alone.

Why?

Because `PartnerAccount` is a materialized projection and can be rebuilt.

The historical truth is:

```text
Movement
Snapshot
Obligation
Settlement
```

---

# 9. Period report architecture

Eventually:

```text
                    SyirkahReportingService
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
     Partner View       Contract View       Period View
          │                   │                   │
          ▼                   ▼                   ▼
      Capital             Lifecycle          Result
      Current Account     Transition         Allocation
      Settlement          Liquidation        Settlement
```

This gives us a clean foundation for the next major step: **the consolidated Syirkah period statement and reconciliation engine**, where we bring result, allocation, capital, current account, settlement, and liquidation information together without duplicating the underlying transactions.


////////////////

The existing framework already has reconciliation/read-model patterns, while the Syirkah source explicitly defines profit as **net profit** from revenue less direct, operating, and other costs. Hukum-muamalah

The next implementation should therefore be:

### 1. Consolidated period statement

```java
public record SyirkahPeriodStatement(
        String tenantId,
        String ledgerId,
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
    public enum ResultType {
        PROFIT,
        LOSS,
        BREAK_EVEN
    }
}
```

This becomes the **executive/read-model view**, not a new source of accounting truth.

---

### 2. Period reconciliation

```java
public record SyirkahPeriodReconciliation(
        boolean balanced,
        List<String> errors,

        BigDecimal resultAllocationDifference,
        BigDecimal capitalRollForwardDifference,
        BigDecimal currentAccountRollForwardDifference,
        BigDecimal settlementDifference,
        BigDecimal lossRecoveryDifference
) {
    public SyirkahPeriodReconciliation {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static SyirkahPeriodReconciliation balanced() {
        return new SyirkahPeriodReconciliation(
                true,
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
```

The reconciliation engine should explicitly verify each invariant rather than merely comparing one final total.

---

### 3. Reconciliation calculator

```java
public interface SyirkahPeriodReconciliationCalculator {

    SyirkahPeriodReconciliation reconcile(
            SyirkahPeriodStatement statement);
}
```

Implementation rules:

```text
PROFIT:
    netResult == totalProfitAllocated

LOSS:
    abs(netResult) == totalLossAllocated

BREAK_EVEN:
    netResult == 0
    total allocation == 0
```

Capital:

```text
closingCapital
=
openingCapital
+ contributions
- returns
- impairments
```

Current account:

```text
closingCurrentAccount
=
openingCurrentAccount
+ profitAllocations
- lossAllocations
- withdrawals
```

Settlement:

```text
outstanding
=
obligated
- settled
```

Loss recovery:

```text
outstandingRecovery
=
responsibility
- recovered
```

Use a monetary tolerance only where the existing accounting currency/rounding policy requires it; do **not** silently round reconciliation differences to zero.

---

### 4. Statement builder

Instead of making the calculator query repositories, separate collection from reconciliation:

```java
public interface SyirkahPeriodStatementProvider {

    Uni<SyirkahPeriodStatement> build(
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate periodStart,
            LocalDate periodEnd);
}
```

Internally:

```text
Close Snapshot
      +
Partner Movements
      +
Distribution Obligations
      +
Settlement Obligations
      +
Loss Responsibilities
      +
Recovery Obligations
      ↓
SyirkahPeriodStatement
      ↓
SyirkahPeriodReconciliationCalculator
      ↓
validated statement
```

This keeps reporting independent of transactional writes.

---

### 5. Critical rule for locked periods

For a closed period, the report should prefer the **frozen `SyirkahCloseSnapshot`**.

For an open period, it may build a live projection.

```java
public interface SyirkahPeriodSnapshotResolver {

    Uni<Optional<SyirkahCloseSnapshot>> findLockedSnapshot(
            String tenantId,
            String ledgerId,
            String closeCycleId);

    Uni<Optional<SyirkahCloseSnapshot>> findLockedSnapshotByPeriod(
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate periodEnd);
}
```

Therefore:

```text
OPEN PERIOD
    ↓
live read model

LOCKED PERIOD
    ↓
immutable snapshot
```

This prevents a historical report from changing because a current projection was later rebuilt.

---

## 6. Partner consolidation

Then aggregate the individual partner statements:

```java
public record SyirkahPartnerConsolidatedStatement(
        String contractId,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<SyirkahPartnerStatement> partners,
        BigDecimal totalOpeningCapital,
        BigDecimal totalContributions,
        BigDecimal totalReturns,
        BigDecimal totalImpairments,
        BigDecimal totalClosingCapital,
        BigDecimal totalProfitAllocations,
        BigDecimal totalLossAllocations,
        BigDecimal totalClosingCurrentAccount
) {}
```

Invariant:

```text
sum(partner closing capital)
==
contract closing capital
```

and:

```text
sum(partner profit allocations)
==
period profit allocation
```

and:

```text
sum(partner loss allocations)
==
period loss allocation
```

This gives us a very useful audit property:

> **The consolidated contract view must equal the sum of its partner positions.**

---

## 7. Do not make this another aggregate

This layer should live under something like:

```text
finance/accounting/
    syirkah-accounting-domain/
        ...
    syirkah-accounting-application/
        ...
    syirkah-accounting-reporting/
        src/main/java/
            .../reporting/
                SyirkahPeriodStatement.java
                SyirkahPartnerStatement.java
                SyirkahCapitalStatement.java
                SyirkahProfitDistributionStatement.java
                SyirkahLossStatement.java
                SyirkahSettlementStatement.java
                SyirkahPeriodReconciliation.java
                SyirkahPeriodReconciliationCalculator.java
                SyirkahPeriodStatementProvider.java
```

If the current Maven structure does not yet have a reporting module, add it as a **capability/read-model module**, rather than putting reporting logic into the existing domain aggregates.

### The resulting architecture

```text
                    SYIRKAH PERIOD
                          │
          ┌───────────────┼────────────────┐
          ▼               ▼                ▼
      Financial        Partner          Settlement
       Result          Movements        Obligations
          │               │                │
          └───────────────┼────────────────┘
                          ▼
                Period Statement
                          │
                          ▼
                 Reconciliation
                          │
              ┌───────────┴───────────┐
              ▼                       ▼
       Partner Statements       Contract Statement
              │                       │
              └───────────┬───────────┘
                          ▼
                   Locked Snapshot
```

This is the right point to move next into **Syirkah transition/partner lifecycle reporting**, because the reporting layer now has a stable period foundation.


//////////////


Next is the **Syirkah transition / partner lifecycle layer**.

This is where the accounting close model meets the actual lifecycle of an akad: partner entry, exit, replacement, capital changes, renewal, and termination. The important rule remains: **an old Syirkah position must be frozen before the next contract becomes active.**

### 1. Transition aggregate

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

```java
public enum SyirkahTransitionStatus {
    DRAFT,
    CLOSING_REQUIRED,
    SNAPSHOT_FROZEN,
    SETTLEMENT_REQUIRED,
    CONTRACT_PENDING,
    COMPLETED,
    CANCELLED
}
```

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

    private SyirkahTransitionStatus status;

    private String closeCycleId;
    private String snapshotId;
    private String nextContractId;

    public SyirkahTransition(
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
        this.status = SyirkahTransitionStatus.DRAFT;
    }

    public void requireClose(String closeCycleId) {
        requireStatus(SyirkahTransitionStatus.DRAFT);

        this.closeCycleId = requireText(closeCycleId, "closeCycleId");
        this.status = SyirkahTransitionStatus.CLOSING_REQUIRED;
    }

    public void freezeSnapshot(String snapshotId) {
        requireStatus(SyirkahTransitionStatus.CLOSING_REQUIRED);

        this.snapshotId = requireText(snapshotId, "snapshotId");
        this.status = SyirkahTransitionStatus.SNAPSHOT_FROZEN;
    }

    public void requireSettlement() {
        requireStatus(SyirkahTransitionStatus.SNAPSHOT_FROZEN);
        this.status = SyirkahTransitionStatus.SETTLEMENT_REQUIRED;
    }

    public void attachNextContract(String nextContractId) {
        if (status != SyirkahTransitionStatus.SNAPSHOT_FROZEN
                && status != SyirkahTransitionStatus.SETTLEMENT_REQUIRED) {
            throw new IllegalStateException(
                    "Transition is not ready for a next contract");
        }

        this.nextContractId =
                requireText(nextContractId, "nextContractId");

        this.status = SyirkahTransitionStatus.CONTRACT_PENDING;
    }

    public void complete() {
        if (status != SyirkahTransitionStatus.CONTRACT_PENDING) {
            throw new IllegalStateException(
                    "Transition must have a next contract before completion");
        }

        this.status = SyirkahTransitionStatus.COMPLETED;
    }

    public void cancel() {
        if (status == SyirkahTransitionStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Completed transition cannot be cancelled");
        }

        this.status = SyirkahTransitionStatus.CANCELLED;
    }

    private void requireStatus(SyirkahTransitionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Expected status " + expected
                            + " but was " + status);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }
        return value;
    }
}
```

The lifecycle is deliberately strict:

```text
OLD CONTRACT
     │
     ▼
CLOSE REQUIRED
     │
     ▼
LOCKED SNAPSHOT
     │
     ▼
SETTLEMENT
     │
     ▼
NEXT CONTRACT
     │
     ▼
COMPLETED
```

---

### 2. Transition instructions

A transition needs an explicit instruction for every affected partner.

```java
public enum PartnerTransitionAction {
    RETAIN,
    EXIT,
    ENTER,
    REPLACE
}
```

```java
public record PartnerTransitionInstruction(
        String partyId,
        PartnerTransitionAction action,
        BigDecimal capitalAtTransition,
        BigDecimal currentAccountAtTransition,
        boolean retainedInNextContract
) {
    public PartnerTransitionInstruction {
        requireText(partyId, "partyId");

        requireNonNegative(
                capitalAtTransition,
                "capitalAtTransition");

        requireNonNegative(
                currentAccountAtTransition,
                "currentAccountAtTransition");

        if (action == PartnerTransitionAction.EXIT
                && retainedInNextContract) {
            throw new IllegalArgumentException(
                    "An exiting partner cannot be retained in the next contract");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }
    }

    private static void requireNonNegative(
            BigDecimal value,
            String field) {

        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(
                    field + " cannot be negative");
        }
    }
}
```

This avoids inferring partner entry/exit from changes in capital.

---

### 3. Result disposition

The period result may either be distributed or retained.

```java
public enum ResultDisposition {
    DISTRIBUTE,
    RETAIN_IN_BUSINESS
}
```

```java
public record SyirkahResultDisposition(
        String calculationId,
        ResultDisposition disposition,
        String approvedBy
) {
    public SyirkahResultDisposition {
        if (calculationId == null || calculationId.isBlank()) {
            throw new IllegalArgumentException(
                    "calculationId must not be blank");
        }

        Objects.requireNonNull(disposition);

        if (approvedBy == null || approvedBy.isBlank()) {
            throw new IllegalArgumentException(
                    "approvedBy must not be blank");
        }
    }
}
```

Important:

```text
RETAIN_IN_BUSINESS
        ≠
partner capital automatically increases
```

The retained amount remains business funds unless the **next contract explicitly defines it as partner capital**.

---

### 4. Opening position of the next contract

```java
public record NextContractOpeningPosition(
        String partyId,
        BigDecimal capitalContribution,
        BigDecimal retainedBusinessBalance
) {
    public NextContractOpeningPosition {
        if (partyId == null || partyId.isBlank()) {
            throw new IllegalArgumentException(
                    "partyId must not be blank");
        }

        if (capitalContribution == null
                || capitalContribution.signum() < 0) {
            throw new IllegalArgumentException(
                    "capitalContribution cannot be negative");
        }

        if (retainedBusinessBalance == null
                || retainedBusinessBalance.signum() < 0) {
            throw new IllegalArgumentException(
                    "retainedBusinessBalance cannot be negative");
        }
    }
}
```

This gives us a clean boundary:

```text
OLD CONTRACT
    │
    ├── partner position
    ├── retained business balance
    └── settlement
             │
             ▼
      NEXT CONTRACT
             │
             ├── capital contribution
             └── retained business balance
```

---

### 5. Partner replacement

Replacement needs one more explicit concept: transfer value.

```java
public record PartnerInterestTransfer(
        String transferId,
        String outgoingPartyId,
        String incomingPartyId,
        BigDecimal carryingValue,
        BigDecimal agreedTransferPrice,
        BigDecimal premiumOrDiscount
) {
    public PartnerInterestTransfer {
        requireText(transferId, "transferId");
        requireText(outgoingPartyId, "outgoingPartyId");
        requireText(incomingPartyId, "incomingPartyId");

        requireNonNegative(carryingValue, "carryingValue");
        requireNonNegative(agreedTransferPrice, "agreedTransferPrice");

        Objects.requireNonNull(premiumOrDiscount);

        if (outgoingPartyId.equals(incomingPartyId)) {
            throw new IllegalArgumentException(
                    "Outgoing and incoming partners must differ");
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
                agreedTransferPrice.subtract(carryingValue)
        );
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }
    }

    private static void requireNonNegative(
            BigDecimal value,
            String field) {

        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(
                    field + " cannot be negative");
        }
    }
}
```

Do **not** equate:

```text
carrying value
=
transfer price
=
new partner capital
```

Those are separate concepts.

---

### 6. Transfer restriction policy

Partner-transfer restrictions should remain outside the accounting aggregate:

```java
public interface PartnerTransferRestrictionPolicy {

    TransferDecision evaluate(
            String contractId,
            String outgoingPartyId,
            String incomingPartyId);

    record TransferDecision(
            boolean allowed,
            String reason
    ) {
        public static TransferDecision allowed() {
            return new TransferDecision(true, null);
        }

        public static TransferDecision rejected(String reason) {
            return new TransferDecision(false, reason);
        }
    }
}
```

This is the correct place for contractual restrictions and the source's discussion of partner-transfer/pre-emption considerations, rather than embedding those rules into `PartnerAccount`.

---

## 7. Transition service

The application layer can now orchestrate the complete process:

```java
public interface SyirkahTransitionService {

    Uni<SyirkahTransition> initiate(
            StartSyirkahTransitionRequest request);

    Uni<SyirkahTransition> freezePreviousPosition(
            String transitionId,
            String closeCycleId);

    Uni<SyirkahTransition> requireSettlement(
            String transitionId);

    Uni<SyirkahTransition> attachNextContract(
            String transitionId,
            String nextContractId);

    Uni<SyirkahTransition> complete(
            String transitionId);
}
```

The service should **not** directly manipulate accounting balances.

It coordinates:

```text
Transition
   ↓
Close
   ↓
Snapshot
   ↓
Settlement
   ↓
Contract creation/activation
```

---

## 8. Important source-derived implication

The supplied *Hukum-muamalah* material specifically describes a case where additional capital enters and the akad/position changes, including a new balance position after the additional capital. Hukum-muamalah

So we should not model:

```text
same contract + silently mutate partners/capital
```

as the universal transition mechanism.

Instead:

```text
Existing Syirkah
       ↓
Determine/freeze existing position
       ↓
Settlement / transition
       ↓
New Syirkah version or contract
       ↓
New opening position
```

That is particularly important for **partner entry, partner exit, and partner replacement**.

### Next after this

The next piece should be the **transition execution/repository layer plus contract activation rules**—specifically preventing a new Syirkah contract/version from becoming active until the predecessor's required snapshot/settlement conditions are satisfied.


///////////


## 1. Activation status

```java id="7r2m4k"
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

This is deliberately separate from the existing `SyirkahContract` version itself.

A contract can exist without yet being eligible to operate.

---

## 2. Activation gate

```java id="9w8c1p"
public record SyirkahContractActivationDecision(
        boolean allowed,
        List<String> blockingReasons
) {
    public SyirkahContractActivationDecision {
        blockingReasons = blockingReasons == null
                ? List.of()
                : List.copyOf(blockingReasons);
    }

    public static SyirkahContractActivationDecision allowed() {
        return new SyirkahContractActivationDecision(
                true,
                List.of());
    }

    public static SyirkahContractActivationDecision blocked(
            List<String> reasons) {

        if (reasons == null || reasons.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one blocking reason is required");
        }

        return new SyirkahContractActivationDecision(
                false,
                reasons);
    }
}
```

The policy:

```java id="2n7c5x"
public interface SyirkahContractActivationPolicy {

    SyirkahContractActivationDecision evaluate(
            SyirkahContractActivationContext context);
}
```

Context:

```java id="f0q6yn"
public record SyirkahContractActivationContext(
        String tenantId,
        String ledgerId,
        String previousContractId,
        int previousContractVersion,
        String nextContractId,
        LocalDate effectiveDate,
        boolean predecessorCloseLocked,
        boolean predecessorSnapshotFrozen,
        boolean requiredSettlementCompleted,
        boolean predecessorTerminated
) {}
```

---

## 3. Default activation policy

```java id="h4v3ps"
public final class DefaultSyirkahContractActivationPolicy
        implements SyirkahContractActivationPolicy {

    @Override
    public SyirkahContractActivationDecision evaluate(
            SyirkahContractActivationContext context) {

        List<String> reasons = new ArrayList<>();

        if (!context.predecessorCloseLocked()) {
            reasons.add("Predecessor close is not locked");
        }

        if (!context.predecessorSnapshotFrozen()) {
            reasons.add("Predecessor Syirkah position is not frozen");
        }

        if (!context.requiredSettlementCompleted()) {
            reasons.add("Required predecessor settlements are incomplete");
        }

        if (!context.predecessorTerminated()) {
            reasons.add("Predecessor contract is not terminated");
        }

        if (!reasons.isEmpty()) {
            return SyirkahContractActivationDecision.blocked(reasons);
        }

        return SyirkahContractActivationDecision.allowed();
    }
}
```

The important point is that **settlement completion is policy-driven**. We should not assume every outstanding monetary obligation makes every transition impossible.

For example:

```text
Partner exit requiring capital return
    -> capital return must be settled

Unrelated receivable still outstanding
    -> may not necessarily block next contract
```

---

## 4. Contract activation service

```java id="q8b6wz"
public interface SyirkahContractActivationService {

    Uni<SyirkahContractActivationDecision> evaluate(
            String tenantId,
            String ledgerId,
            String previousContractId,
            int previousContractVersion,
            String nextContractId,
            LocalDate effectiveDate);

    Uni<SyirkahContract> activate(
            String tenantId,
            String ledgerId,
            String nextContractId,
            String activatedBy);
}
```

Implementation flow:

```text
evaluate
   │
   ├── previous close
   ├── frozen snapshot
   ├── required settlement
   └── predecessor status
            │
            ▼
      activation decision
            │
      ┌─────┴─────┐
      ▼           ▼
   ALLOWED      BLOCKED
      │
      ▼
   activate
```

---

# 5. Repository boundary

```java id="6x5p1a"
public interface SyirkahContractLifecycleRepository {

    Uni<Optional<SyirkahContract>> findById(
            String tenantId,
            String ledgerId,
            String contractId);

    Uni<Optional<SyirkahContract>> findEffective(
            String tenantId,
            String ledgerId,
            LocalDate date);

    Uni<Void> save(
            String tenantId,
            String ledgerId,
            SyirkahContract contract);

    Uni<Boolean> hasActivePredecessor(
            String tenantId,
            String ledgerId,
            String contractId);

    Uni<Void> activate(
            String tenantId,
            String ledgerId,
            String contractId,
            String activatedBy);
}
```

The database should enforce the important lifecycle uniqueness rather than relying only on Java checks.

For example:

```sql id="5q1r9d"
CREATE UNIQUE INDEX ux_syirkah_active_contract
ON syirkah_contract (
    tenant_id,
    ledger_id
)
WHERE activation_status = 'ACTIVE';
```

If the business permits multiple simultaneously active contracts for different business units, the uniqueness key should include the appropriate business scope instead of globally restricting the ledger.

That scope should be explicit; do not invent another implicit dimension.

---

# 6. Version activation

Existing `SyirkahContract` already supports effective-dated versions.

We therefore should distinguish:

```text
Contract
    └── Version 1
    └── Version 2
    └── Version 3
```

from:

```text
Contract A
    ↓
Contract B
```

A **version change** can be used where the legal/business relationship remains the same and the existing lifecycle rules permit it.

A **new contract** should be used where the old Syirkah must actually end and a new akad/relationship begins.

The transition service decides which case applies; it should not blindly create a new version for every change.

---

# 7. Effective-date invariant

The activation service must reject:

```text
next.effectiveDate < predecessor.transitionDate
```

and, more importantly, reject overlapping active periods for the same contractual scope.

```java id="2d9k6v"
public interface SyirkahEffectivePeriodPolicy {

    boolean allowsActivation(
            String tenantId,
            String ledgerId,
            String contractId,
            LocalDate effectiveDate);
}
```

This prevents:

```text
Old contract:  Jan 1 ───────────── Dec 31
New contract:             Jun 1 ─────────────
```

unless an explicit policy says both relationships are legitimately concurrent.

---

# 8. Transition repository

```java id="w3g7fz"
public interface SyirkahTransitionRepository {

    Uni<Optional<SyirkahTransition>> findById(
            String tenantId,
            String ledgerId,
            String transitionId);

    Uni<List<SyirkahTransition>> findByPreviousContract(
            String tenantId,
            String ledgerId,
            String contractId);

    Uni<Optional<SyirkahTransition>> findPendingForContract(
            String tenantId,
            String ledgerId,
            String contractId);

    Uni<Void> save(
            SyirkahTransition transition);
}
```

Database protection:

```sql id="x6t2qk"
CREATE UNIQUE INDEX ux_syirkah_pending_transition
ON syirkah_transition (
    tenant_id,
    ledger_id,
    previous_contract_id
)
WHERE status NOT IN ('COMPLETED', 'CANCELLED');
```

This prevents multiple competing transitions from independently attempting to close the same predecessor.

---

# 9. Complete transition orchestration

The final application flow becomes:

```text
Start Transition
       │
       ▼
CLOSING_REQUIRED
       │
       ▼
Run Close Cycle
       │
       ▼
LOCKED
       │
       ▼
Create/Fix Syirkah Close Snapshot
       │
       ▼
SNAPSHOT_FROZEN
       │
       ▼
Determine Required Settlement
       │
       ▼
SETTLEMENT_REQUIRED
       │
       ▼
Required Settlement Complete
       │
       ▼
Create Next Contract
       │
       ▼
Activation Gate
       │
       ├──────────── BLOCKED
       │
       ▼
ACTIVE
       │
       ▼
Complete Transition
```

This gives us the invariant we wanted:

> **No new Syirkah relationship becomes active on top of an unresolved predecessor position.**

---

## 10. One refinement to the previous transition aggregate

`attachNextContract()` should **not** itself imply that the contract is active.

It should only attach the proposed successor:

```text
CONTRACT_PENDING
```

Then:

```java
activate(nextContract)
```

must pass the activation policy.

Only after successful activation:

```text
CONTRACT_PENDING
       ↓
COMPLETED
```

This separation is important for retries and failures.

If activation fails halfway through an infrastructure operation, we don't want the transition aggregate claiming that the new akad is already complete.

---

### Current lifecycle architecture

```text
                  SYIRKAH CONTRACT
                         │
             ┌───────────┴───────────┐
             │                       │
        Normal operation        Transition
             │                       │
             ▼                       ▼
        Period Close          Predecessor Close
             │                       │
             ▼                       ▼
       Close Snapshot          Frozen Position
                                     │
                                     ▼
                                Settlement
                                     │
                                     ▼
                              Activation Gate
                                     │
                                     ▼
                              Next Contract
```

The next major area is now **full termination/liquidation integration with this activation lifecycle**: making `FULL_TERMINATION` end with final reconciliation, final distributions/capital returns, settlement policy evaluation, and an explicit `TERMINATED` predecessor state rather than just treating termination as another ordinary transition.


/////////////

The next extension should make **full termination a first-class Syirkah lifecycle**, not merely another transition record.

The source material supports the sequence of liquidation/termination as: realize assets, settle liabilities, determine the remaining result/net position, distribute the result, return capital, then terminate the Syirkah. The exact accounting/result formula should remain policy-driven rather than universal.

## 1. Add explicit termination state to the Syirkah contract lifecycle

Do not create another generic contract status enum. The existing Syirkah activation lifecycle can remain, but termination needs an explicit domain operation.

```java
public enum SyirkahTerminationStatus {
    NOT_STARTED,
    CLOSING_REQUIRED,
    SNAPSHOT_REQUIRED,
    LIQUIDATION_REQUIRED,
    DISTRIBUTION_REQUIRED,
    CAPITAL_RETURN_REQUIRED,
    SETTLEMENT_REQUIRED,
    READY_TO_TERMINATE,
    TERMINATED,
    CANCELLED
}
```

The important point is that `TERMINATED` is reached only after the financial state is actually resolved.

---

## 2. Make `FULL_TERMINATION` different from ordinary transitions

Existing:

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

Keep this.

But add an explicit distinction:

```java
public boolean isTerminal() {
    return this == FULL_TERMINATION;
}
```

And the transition invariant becomes:

```text
PARTNER_ENTRY
PARTNER_EXIT
CAPITAL_ADDITION
CAPITAL_REDUCTION
PARTNER_REPLACEMENT
CONTRACT_RENEWAL
        |
        v
 predecessor snapshot
        |
        v
 settlement
        |
        v
 successor activation


FULL_TERMINATION
        |
        v
 predecessor snapshot
        |
        v
 liquidation
        |
        v
 final reconciliation
        |
        v
 result distribution
        |
        v
 capital return
        |
        v
 settlement
        |
        v
 contract TERMINATED
```

There is **no successor contract** in the second path.

---

# 3. Extend `SyirkahTransition`

The transition aggregate should explicitly know whether it expects a successor.

```java
public boolean requiresSuccessorContract() {
    return type != SyirkahTransitionType.FULL_TERMINATION;
}
```

Then tighten:

```java
public void attachNextContract(String nextContractId) {
    if (!requiresSuccessorContract()) {
        throw new IllegalStateException(
                "A full termination transition cannot have a successor contract");
    }

    if (status != SyirkahTransitionStatus.CONTRACT_PENDING) {
        throw new IllegalStateException(
                "Transition is not ready for successor contract");
    }

    if (nextContractId == null || nextContractId.isBlank()) {
        throw new IllegalArgumentException(
                "nextContractId must not be blank");
    }

    this.nextContractId = nextContractId;
}
```

And:

```java
public void complete() {
    if (type == SyirkahTransitionType.FULL_TERMINATION) {
        throw new IllegalStateException(
                "Full termination must be completed through liquidation completion");
    }

    if (status != SyirkahTransitionStatus.CONTRACT_PENDING) {
        throw new IllegalStateException(
                "Transition is not ready for completion");
    }

    if (nextContractId == null || nextContractId.isBlank()) {
        throw new IllegalStateException(
                "Successor contract must be attached before completion");
    }

    status = SyirkahTransitionStatus.COMPLETED;
}
```

Then add a dedicated terminal operation:

```java
public void completeTermination() {
    if (type != SyirkahTransitionType.FULL_TERMINATION) {
        throw new IllegalStateException(
                "Only full termination transitions can terminate a Syirkah");
    }

    if (status != SyirkahTransitionStatus.SETTLEMENT_REQUIRED
            && status != SyirkahTransitionStatus.READY_TO_TERMINATE) {
        throw new IllegalStateException(
                "Termination is not ready to complete");
    }

    status = SyirkahTransitionStatus.COMPLETED;
}
```

This prevents the generic transition mechanism from accidentally treating termination like contract renewal.

---

# 4. Link `SyirkahLiquidation` to the termination transition

Add:

```java
private String transitionId;
```

to `SyirkahLiquidation`.

Constructor/factory:

```java
public static SyirkahLiquidation start(
        String liquidationId,
        String transitionId,
        String tenantId,
        String ledgerId,
        String contractId,
        int contractVersion,
        String closeCycleId
) {
    if (transitionId == null || transitionId.isBlank()) {
        throw new IllegalArgumentException("transitionId must not be blank");
    }

    SyirkahLiquidation liquidation = new SyirkahLiquidation(
            liquidationId,
            tenantId,
            ledgerId,
            contractId,
            contractVersion,
            closeCycleId
    );

    liquidation.transitionId = transitionId;
    liquidation.status = LiquidationStatus.NOT_STARTED;

    return liquidation;
}
```

Getter:

```java
public String getTransitionId() {
    return transitionId;
}
```

This gives us:

```text
SyirkahTransition
       |
       +---- closeCycleId
       |
       +---- snapshotId
       |
       +---- liquidationId
```

rather than relying on IDs inferred from unrelated records.

---

# 5. Require the locked close snapshot before liquidation

This is a critical invariant.

Add a port:

```java
public interface SyirkahTerminationSnapshotProvider {

    Uni<SyirkahCloseSnapshot> requireLockedSnapshot(
            String tenantId,
            String ledgerId,
            String contractId,
            String closeCycleId);
}
```

The implementation must fail when:

- close cycle does not exist
- close cycle is not locked
- snapshot does not exist
- snapshot is not frozen
- snapshot does not belong to the contract

Then:

```java
public Uni<SyirkahLiquidation> startFullTermination(
        StartFullTerminationRequest request) {

    return transitionRepository
            .findById(
                    request.tenantId(),
                    request.ledgerId(),
                    request.transitionId())
            .onItem().ifNull()
            .failWith(() -> new IllegalArgumentException(
                    "Termination transition not found"))
            .chain(transition -> {

                if (transition.getType()
                        != SyirkahTransitionType.FULL_TERMINATION) {
                    return Uni.createFrom().failure(
                            new IllegalStateException(
                                    "Transition is not a full termination"));
                }

                return snapshotProvider
                        .requireLockedSnapshot(
                                request.tenantId(),
                                request.ledgerId(),
                                transition.getPreviousContractId(),
                                transition.getCloseCycleId())
                        .chain(snapshot -> {
                            transition.freezeSnapshot(snapshot.getSnapshotId());

                            return transitionRepository
                                    .save(transition)
                                    .chain(() ->
                                            liquidationService.start(
                                                    request.toStartLiquidationRequest(
                                                            snapshot)));
                        });
            });
}
```

This makes the old financial position immutable before liquidation begins.

---

# 6. Final liquidation result must remain policy-driven

Do **not** introduce:

```java
profitOrLoss = netRealization.subtract(originalCapital);
```

as a universal rule.

Keep:

```java
public interface SyirkahLiquidationResultPolicy {

    Uni<SyirkahLiquidationResult> determine(
            SyirkahLiquidation liquidation,
            SyirkahCloseSnapshot openingSnapshot,
            SyirkahContract.Version contractVersion);
}
```

And:

```java
public interface SyirkahLiquidationResultPolicyResolver {

    SyirkahLiquidationResultPolicy resolve(
            SyirkahContract.Version contractVersion);
}
```

The termination service therefore becomes:

```java
public Uni<SyirkahLiquidationResult> determineFinalResult(
        String tenantId,
        String ledgerId,
        String liquidationId) {

    return liquidationRepository
            .findById(tenantId, ledgerId, liquidationId)
            .chain(this::requireLiquidationReadyForResult)
            .chain(liquidation ->
                    snapshotRepository
                            .findById(
                                    tenantId,
                                    ledgerId,
                                    liquidation.getSnapshotId())
                            .chain(snapshot ->
                                    contractRepository
                                            .findEffectiveContract(
                                                    tenantId,
                                                    ledgerId,
                                                    liquidation.getContractId(),
                                                    liquidation.getTransitionDate())
                                            .chain(contract -> {

                                                SyirkahLiquidationResultPolicy policy =
                                                        policyResolver.resolve(
                                                                contract.getVersion());

                                                return policy.determine(
                                                        liquidation,
                                                        snapshot,
                                                        contract);
                                            })));
}
```

This is important because different Syirkah types can have different treatment.

---

# 7. Final reconciliation becomes mandatory

The final reconciliation should cover at least:

```text
gross realization
- liabilities settled
= net realization
```

Then separately:

```text
net realization
→ final result
→ profit distribution / loss absorption
→ capital return
→ residual balance
```

Use the existing reconciliation model:

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

Add a strict calculator:

```java
public SyirkahLiquidationReconciliation reconcile(
        SyirkahLiquidationResult result,
        List<PartnerDistributionObligation> distributions,
        List<PartnerCapitalReturnObligation> capitalReturns) {

    BigDecimal profitDistribution = distributions.stream()
            .map(PartnerDistributionObligation::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal capitalReturn = capitalReturns.stream()
            .map(PartnerCapitalReturnObligation::getCapitalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal expected = result.getNetRealization();

    BigDecimal remaining =
            expected
                    .subtract(profitDistribution)
                    .subtract(capitalReturn);

    List<String> errors = new ArrayList<>();

    if (remaining.signum() != 0) {
        errors.add(
                "Liquidation balance is not fully reconciled: "
                        + remaining);
    }

    return new SyirkahLiquidationReconciliation(
            result.getNetRealization(),
            profitDistribution,
            BigDecimal.ZERO,
            capitalReturn,
            remaining,
            errors.isEmpty(),
            List.copyOf(errors)
    );
}
```

The exact loss side should be populated from the existing loss-allocation/responsibility model rather than pretending every loss is a distribution.

---

# 8. Introduce a final liquidation completion gate

We already have:

```java
public interface SyirkahLiquidationCompletionPolicy {
    CompletionDecision evaluate(
            SyirkahLiquidation liquidation,
            SyirkahLiquidationReconciliation liquidationReconciliation,
            SyirkahSettlementReconciliation settlementReconciliation);
}
```

Now use it as the **only gate** for termination.

```java
public Uni<Void> completeFullTermination(
        String tenantId,
        String ledgerId,
        String liquidationId) {

    return liquidationRepository
            .findById(tenantId, ledgerId, liquidationId)
            .chain(liquidation ->
                    reconciliationRepository
                            .findByLiquidationId(
                                    tenantId,
                                    ledgerId,
                                    liquidationId)
                            .chain(liquidationReconciliation ->
                                    settlementService
                                            .reconcileLiquidation(
                                                    tenantId,
                                                    ledgerId,
                                                    liquidationId)
                                            .chain(settlementReconciliation -> {

                                                CompletionDecision decision =
                                                        completionPolicy.evaluate(
                                                                liquidation,
                                                                liquidationReconciliation,
                                                                settlementReconciliation);

                                                if (!decision.allowed()) {
                                                    return Uni.createFrom()
                                                            .failure(
                                                                    new IllegalStateException(
                                                                            decision.reason()));
                                                }

                                                return transitionService
                                                        .terminatePredecessor(
                                                                tenantId,
                                                                ledgerId,
                                                                liquidation.getTransitionId());
                                            })));
}
```

The key invariant is:

```text
liquidation completed
       AND
final reconciliation balanced
       AND
settlement policy satisfied
       ↓
contract TERMINATED
```

Not merely:

```text
liquidation status == COMPLETED
       ↓
contract TERMINATED
```

---

# 9. Terminate the predecessor explicitly

Add to the lifecycle repository:

```java
public interface SyirkahContractLifecycleRepository {

    Uni<Optional<SyirkahContract>> findById(
            String tenantId,
            String ledgerId,
            String contractId);

    Uni<Void> save(
            String tenantId,
            String ledgerId,
            SyirkahContract contract);

    Uni<Void> activate(
            String tenantId,
            String ledgerId,
            String contractId);

    Uni<Void> terminate(
            String tenantId,
            String ledgerId,
            String contractId);

    Uni<Boolean> hasActivePredecessor(
            String tenantId,
            String ledgerId,
            String contractId);
}
```

The actual termination operation should be atomic:

```java
public Uni<Void> terminatePredecessor(
        String tenantId,
        String ledgerId,
        String contractId) {

    return contractRepository
            .findById(tenantId, ledgerId, contractId)
            .chain(optional -> {

                if (optional.isEmpty()) {
                    return Uni.createFrom().failure(
                            new IllegalArgumentException(
                                    "Syirkah contract not found"));
                }

                SyirkahContract contract = optional.get();

                if (!contract.isActive()) {
                    return Uni.createFrom().failure(
                            new IllegalStateException(
                                    "Only an active Syirkah contract can be terminated"));
                }

                contract.terminate();

                return contractRepository.save(
                        tenantId,
                        ledgerId,
                        contract);
            });
}
```

The database operation should additionally enforce the state transition atomically.

---

# 10. Add an explicit termination orchestrator

This is the main application-level API I recommend.

```java
public interface SyirkahTerminationService {

    Uni<SyirkahTransition> initiate(
            StartTerminationRequest request);

    Uni<SyirkahLiquidation> startLiquidation(
            String transitionId);

    Uni<SyirkahLiquidationResult> determineResult(
            String liquidationId);

    Uni<SyirkahLiquidationReconciliation> reconcile(
            String liquidationId);

    Uni<List<PartnerDistributionObligation>> prepareDistributions(
            String liquidationId);

    Uni<List<PartnerCapitalReturnObligation>> prepareCapitalReturns(
            String liquidationId);

    Uni<Void> postAccounting(
            String liquidationId);

    Uni<SyirkahSettlementReconciliation> reconcileSettlements(
            String liquidationId);

    Uni<Void> complete(
            String liquidationId);
}
```

The lifecycle becomes very explicit:

```text
initiate
   ↓
locked predecessor snapshot
   ↓
start liquidation
   ↓
asset realization
   ↓
liability settlement
   ↓
final reconciliation
   ↓
determine result
   ↓
prepare obligations
   ↓
maker/checker approval
   ↓
accounting posting
   ↓
cash settlement
   ↓
settlement reconciliation
   ↓
completion policy
   ↓
TERMINATED
```

---

# 11. Accounting must remain downstream of the business facts

For termination, keep the same architecture:

```text
Liquidation realization
        ↓
Liquidation result
        ↓
Partner distribution obligation
Partner capital-return obligation
Loss/recovery obligation
        ↓
Accounting policy
        ↓
JournalEntry
        ↓
Settlement
```

Never:

```text
Liquidation
   ↓
direct account.debit()
```

For example:

```java
public interface SyirkahLiquidationAccountingPolicy {

    JournalMapping mapProfitDistribution(
            SyirkahLiquidationResult result,
            List<PartnerDistributionObligation> obligations);

    JournalMapping mapLossRecovery(
            SyirkahLiquidationResult result,
            List<LossRecoveryObligation> obligations);

    JournalMapping mapCapitalReturn(
            List<PartnerCapitalReturnObligation> obligations);
}
```

Each mapping becomes a normal `PostJournalEntryCommand`.

The existing GL source/idempotency mechanism should then use:

```text
SYIRKAH_LIQUIDATION_PROFIT_DISTRIBUTION
SYIRKAH_LIQUIDATION_LOSS_RECOVERY
SYIRKAH_LIQUIDATION_CAPITAL_RETURN
```

with:

```text
tenant_id
ledger_id
source_type
source_id
```

as the idempotency boundary.

---

# 12. Capital return remains separate from profit distribution

This distinction is especially important at termination.

For a partner:

```text
Profit allocation
        !=
Capital return
        !=
Cash settlement
```

So a final liquidation may produce:

```text
Partner A
  Profit allocation     12,000
  Capital return       100,000
  Cash settled          80,000
  Outstanding           32,000
```

Those are four different business facts.

Do not create one giant `LiquidationDistribution` amount that hides these distinctions.

---

# 13. Full termination has no successor activation

The existing activation policy should explicitly reject successor semantics for a terminal transition:

```java
if (context.transitionType()
        == SyirkahTransitionType.FULL_TERMINATION) {

    return SyirkahContractActivationDecision.blocked(
            List.of(
                    "Full termination has no successor contract"));
}
```

More importantly, the application service should never call:

```java
activate(nextContract)
```

for a full termination.

This prevents an accidental:

```text
terminate old contract
+
activate unrelated new contract
```

from being treated as one atomic Syirkah lifecycle.

If a new Syirkah is later established, that is a **new business process**.

---

# 14. Database invariants

I would add these constraints around the existing tables.

### One active liquidation per contract

```sql
CREATE UNIQUE INDEX ux_syirkah_active_liquidation
ON syirkah_liquidation (
    tenant_id,
    ledger_id,
    contract_id
)
WHERE status NOT IN ('COMPLETED', 'CANCELLED');
```

### One full-termination transition per predecessor

```sql
CREATE UNIQUE INDEX ux_syirkah_full_termination_transition
ON syirkah_transition (
    tenant_id,
    ledger_id,
    previous_contract_id
)
WHERE type = 'FULL_TERMINATION'
  AND status NOT IN ('COMPLETED', 'CANCELLED');
```

### One final snapshot

Already consistent with the snapshot rule:

```sql
UNIQUE (
    tenant_id,
    ledger_id,
    close_cycle_id
)
```

### Accounting idempotency

For every liquidation posting:

```sql
UNIQUE (
    tenant_id,
    ledger_id,
    source_type,
    source_id
)
```

This matters because retries are expected.

---

# 15. The final invariant

The most important invariant after this extension is:

```text
A Syirkah contract may become TERMINATED only if:

1. The predecessor close is LOCKED.
2. The close snapshot is FROZEN.
3. The liquidation belongs to that contract/transition.
4. All required assets have been realized.
5. Required liabilities have been settled.
6. Final liquidation reconciliation is balanced.
7. The final result has been determined using the contract-specific policy.
8. Profit/loss/capital-return obligations have been determined.
9. Required accounting entries have successfully POSTED.
10. Required settlements satisfy the configured completion policy.
11. The termination transition is completed.
```

That gives us a clean terminal state:

```text
ACTIVE
  |
  | FULL_TERMINATION
  v
CLOSING
  |
  v
SNAPSHOT_FROZEN
  |
  v
LIQUIDATING
  |
  v
RESULT_DETERMINED
  |
  v
ALLOCATED
  |
  v
ACCOUNTED
  |
  v
SETTLEMENT_RECONCILED
  |
  v
TERMINATED
```

And importantly, this does **not** rebuild the existing ERP/accounting implementation. It adds the missing Syirkah-specific lifecycle around it.

### What this completes

At this point the Syirkah architecture has the major financial lifecycle:

```text
AKAD
  ↓
PARTNERS + CAPITAL + NISBAH
  ↓
OPERATING PERIOD
  ↓
PERIOD CLOSE
  ↓
RESULT
  ├── PROFIT
  │     ↓
  │  ALLOCATION
  │     ↓
  │  ACCOUNTING
  │     ↓
  │  SETTLEMENT
  │
  └── LOSS
        ├── NORMAL BUSINESS LOSS
        └── RESPONSIBILITY
              ↓
           RECOVERY
  ↓
TRANSITION
  ├── PARTNER ENTRY
  ├── PARTNER EXIT
  ├── CAPITAL CHANGE
  └── REPLACEMENT
  ↓
FULL TERMINATION
  ↓
LIQUIDATION
  ↓
FINAL RESULT
  ↓
DISTRIBUTION + CAPITAL RETURN
  ↓
SETTLEMENT
  ↓
TERMINATED
```

The next logical layer after this is **Syirkah audit/compliance and immutable lifecycle evidence**: making every calculation, approval, accounting posting, settlement, transition, and termination reconstructable for Sharia review and financial audit without changing the transactional domain.

//////////////

Continue to the **Syirkah audit/compliance layer**.

The framework already has an `AccountingEvent` contract with tenant, ledger, correlation, and causation identifiers, plus an outbox and projection mechanism. syirkah-framework It also already supports compliance plugins/rules and event projections, so we should extend those mechanisms rather than introduce a second audit/event system. syirkah-framework(1)

## 1. Define Syirkah lifecycle evidence

The audit record should capture a **business fact**, not merely "user changed status".

```java
public enum SyirkahEvidenceType {

    CONTRACT_CREATED,
    CONTRACT_VERSION_ACTIVATED,

    CLOSE_STARTED,
    CLOSE_RESULT_CALCULATED,
    CLOSE_ALLOCATION_CALCULATED,
    DISTRIBUTION_PREPARED,
    DISTRIBUTION_APPROVED,
    DISTRIBUTION_POSTED,

    LOSS_RESPONSIBILITY_DETERMINED,
    LOSS_RECOVERY_APPROVED,
    LOSS_RECOVERY_POSTED,

    LIQUIDATION_STARTED,
    ASSET_REALIZED,
    LIABILITY_SETTLED,
    LIQUIDATION_RESULT_DETERMINED,
    LIQUIDATION_RECONCILED,

    CAPITAL_RETURN_APPROVED,
    CAPITAL_RETURN_POSTED,

    SETTLEMENT_PREPARED,
    SETTLEMENT_APPROVED,
    SETTLEMENT_EXECUTED,
    SETTLEMENT_RECONCILED,

    TRANSITION_CREATED,
    SNAPSHOT_FROZEN,
    SUCCESSOR_CONTRACT_ACTIVATED,

    CONTRACT_TERMINATED
}
```

This gives us a controlled vocabulary rather than arbitrary audit strings.

---

# 2. Immutable `SyirkahAuditEvidence`

Create this in the Syirkah accounting domain:

```java
public record SyirkahAuditEvidence(
        UUID evidenceId,
        String tenantId,
        String ledgerId,
        SyirkahEvidenceType evidenceType,

        String contractId,
        Integer contractVersion,

        String closeCycleId,
        String calculationId,
        String transitionId,
        String liquidationId,

        String sourceType,
        String sourceId,

        String actorId,
        Instant occurredAt,

        String correlationId,
        String causationId,

        String evidenceReference,
        String previousEvidenceHash,
        String evidenceHash
) {

    public SyirkahAuditEvidence {
        Objects.requireNonNull(evidenceId);
        requireText(tenantId, "tenantId");
        requireText(ledgerId, "ledgerId");
        Objects.requireNonNull(evidenceType);
        requireText(actorId, "actorId");
        Objects.requireNonNull(occurredAt);
        requireText(correlationId, "correlationId");
        requireText(evidenceHash, "evidenceHash");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " must not be blank");
        }
    }
}
```

Notice that this record does **not** contain mutable business state.

It is an evidence record.

---

# 3. Do not store arbitrary mutable JSON as the authoritative evidence

It is tempting to add:

```java
Map<String, Object> payload;
```

to the audit record.

I would avoid making that the authoritative representation.

Instead:

```text
Business aggregate
       ↓
Domain event
       ↓
Immutable evidence
       ↓
Hash
       ↓
Outbox
       ↓
Audit projection
```

The actual domain snapshot remains in the proper aggregate/snapshot repository.

The audit evidence answers:

> What happened, when, to which Syirkah, by whom, and what immutable business object caused it?

---

# 4. Hash-chain the evidence

For financial/Sharia auditability, add tamper-evidence.

```java
public interface SyirkahAuditEvidenceHasher {

    String hash(
            SyirkahAuditEvidence evidence,
            String previousEvidenceHash);
}
```

A canonical implementation can hash:

```text
tenantId
ledgerId
evidenceType
contractId
contractVersion
closeCycleId
calculationId
transitionId
liquidationId
sourceType
sourceId
actorId
occurredAt
correlationId
causationId
evidenceReference
previousEvidenceHash
```

using SHA-256.

```java
public final class Sha256SyirkahAuditEvidenceHasher
        implements SyirkahAuditEvidenceHasher {

    @Override
    public String hash(
            SyirkahAuditEvidence evidence,
            String previousEvidenceHash) {

        String canonical = String.join("|",
                value(evidence.tenantId()),
                value(evidence.ledgerId()),
                evidence.evidenceType().name(),
                value(evidence.contractId()),
                value(evidence.contractVersion()),
                value(evidence.closeCycleId()),
                value(evidence.calculationId()),
                value(evidence.transitionId()),
                value(evidence.liquidationId()),
                value(evidence.sourceType()),
                value(evidence.sourceId()),
                value(evidence.actorId()),
                evidence.occurredAt().toString(),
                value(evidence.correlationId()),
                value(evidence.causationId()),
                value(evidence.evidenceReference()),
                value(previousEvidenceHash));

        return sha256(canonical);
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] bytes = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 is not available", e);
        }
    }
}
```

This is **tamper evidence**, not cryptographic proof that the entire database is immutable. The database still needs access control and append-only enforcement.

---

# 5. Repository must be append-only

```java
public interface SyirkahAuditEvidenceRepository {

    Uni<Void> append(
            SyirkahAuditEvidence evidence);

    Uni<List<SyirkahAuditEvidence>> findByContract(
            String tenantId,
            String ledgerId,
            String contractId);

    Uni<List<SyirkahAuditEvidence>> findByCloseCycle(
            String tenantId,
            String ledgerId,
            String closeCycleId);

    Uni<List<SyirkahAuditEvidence>> findByLiquidation(
            String tenantId,
            String ledgerId,
            String liquidationId);

    Uni<List<SyirkahAuditEvidence>> findByTransition(
            String tenantId,
            String ledgerId,
            String transitionId);

    Uni<Optional<SyirkahAuditEvidence>> findByEvidenceId(
            String tenantId,
            String ledgerId,
            UUID evidenceId);
}
```

Notice there is deliberately no:

```java
update(...)
delete(...)
```

The audit evidence is append-only.

---

# 6. Add database protection

At minimum:

```sql
CREATE TABLE syirkah_audit_evidence (
    evidence_id UUID PRIMARY KEY,

    tenant_id VARCHAR(128) NOT NULL,
    ledger_id VARCHAR(128) NOT NULL,

    evidence_type VARCHAR(100) NOT NULL,

    contract_id VARCHAR(128),
    contract_version INTEGER,

    close_cycle_id VARCHAR(128),
    calculation_id VARCHAR(128),
    transition_id VARCHAR(128),
    liquidation_id VARCHAR(128),

    source_type VARCHAR(128),
    source_id VARCHAR(128),

    actor_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,

    correlation_id VARCHAR(128) NOT NULL,
    causation_id VARCHAR(128),

    evidence_reference VARCHAR(512),

    previous_evidence_hash VARCHAR(128),
    evidence_hash VARCHAR(128) NOT NULL,

    created_at TIMESTAMP NOT NULL
);
```

Useful indexes:

```sql
CREATE INDEX ix_syirkah_audit_contract
ON syirkah_audit_evidence (
    tenant_id,
    ledger_id,
    contract_id,
    occurred_at
);

CREATE INDEX ix_syirkah_audit_close_cycle
ON syirkah_audit_evidence (
    tenant_id,
    ledger_id,
    close_cycle_id,
    occurred_at
);

CREATE INDEX ix_syirkah_audit_liquidation
ON syirkah_audit_evidence (
    tenant_id,
    ledger_id,
    liquidation_id,
    occurred_at
);

CREATE INDEX ix_syirkah_audit_transition
ON syirkah_audit_evidence (
    tenant_id,
    ledger_id,
    transition_id,
    occurred_at
);
```

---

# 7. Add an audit service

```java
public interface SyirkahAuditService {

    Uni<SyirkahAuditEvidence> record(
            SyirkahAuditEvidenceRequest request);

    Uni<List<SyirkahAuditEvidence>> contractHistory(
            String tenantId,
            String ledgerId,
            String contractId);

    Uni<List<SyirkahAuditEvidence>> closeHistory(
            String tenantId,
            String ledgerId,
            String closeCycleId);

    Uni<List<SyirkahAuditEvidence>> liquidationHistory(
            String tenantId,
            String ledgerId,
            String liquidationId);

    Uni<List<SyirkahAuditEvidence>> transitionHistory(
            String tenantId,
            String ledgerId,
            String transitionId);
}
```

The implementation obtains the previous hash for the same tenant/ledger chain and creates the next evidence record.

---

# 8. Create Syirkah-specific domain events

The framework already has `AccountingEvent`, which is exactly the right integration point. syirkah-framework

Start with the major events.

### Result calculated

```java
public record SyirkahResultCalculated(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        LedgerId ledgerId,

        String closeCycleId,
        String calculationId,

        String contractId,
        int contractVersion,

        BigDecimal netResult,
        ResultType resultType,

        String correlationId,
        String causationId
) implements AccountingEvent {

    public enum ResultType {
        PROFIT,
        LOSS,
        BREAK_EVEN
    }
}
```

### Distribution approved

```java
public record SyirkahDistributionApproved(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        LedgerId ledgerId,

        String calculationId,
        String contractId,
        int contractVersion,

        String approvedBy,

        String correlationId,
        String causationId
) implements AccountingEvent {
}
```

### Distribution posted

```java
public record SyirkahDistributionPosted(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        LedgerId ledgerId,

        String calculationId,
        String contractId,
        int contractVersion,

        String journalEntryId,

        String postedBy,

        String correlationId,
        String causationId
) implements AccountingEvent {
}
```

### Liquidation result

```java
public record SyirkahLiquidationResultDetermined(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        LedgerId ledgerId,

        String transitionId,
        String liquidationId,

        String contractId,
        int contractVersion,

        BigDecimal grossRealization,
        BigDecimal liabilitiesSettled,
        BigDecimal netRealization,
        BigDecimal profitOrLoss,

        String correlationId,
        String causationId
) implements AccountingEvent {
}
```

### Contract terminated

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

        String terminatedBy,

        String correlationId,
        String causationId
) implements AccountingEvent {
}
```

These are business facts, not audit records.

The audit projection can consume them.

---

# 9. Use the existing outbox

The framework already has an `OutboxRepository`, including `save`, `findUnprocessed`, and `markProcessed`. syirkah-framework(1)

Therefore the transaction should become:

```text
DATABASE TRANSACTION
    |
    +-- mutate Syirkah aggregate
    |
    +-- persist aggregate
    |
    +-- persist domain/business event
    |
    +-- persist outbox record
    |
    COMMIT
         |
         v
      dispatcher
         |
         +-- audit projection
         +-- reporting projection
         +-- external integration
         +-- notifications
```

Do **not** make audit persistence an unrelated asynchronous HTTP call.

For financial state transitions, losing the audit event because an external audit service was temporarily unavailable is unacceptable.

---

# 10. Add idempotency to the audit projection

Event delivery can happen more than once.

Therefore:

```java
public interface SyirkahAuditEventReceiptRepository {

    Uni<Boolean> tryAcquire(
            String tenantId,
            String ledgerId,
            UUID eventId);
}
```

Database:

```sql
CREATE UNIQUE INDEX ux_syirkah_audit_event_receipt
ON syirkah_audit_event_receipt (
    tenant_id,
    ledger_id,
    event_id
);
```

Projection:

```java
public final class SyirkahAuditProjection
        implements Projection<AccountingEvent> {

    private final SyirkahAuditEventReceiptRepository receipts;
    private final SyirkahAuditService auditService;

    @Override
    public Uni<Void> project(AccountingEvent event) {

        return receipts.tryAcquire(
                event.tenantId().value(),
                event.ledgerId().value(),
                event.eventId())
                .chain(acquired -> {

                    if (!acquired) {
                        return Uni.createFrom().voidItem();
                    }

                    return record(event);
                });
    }

    private Uni<Void> record(AccountingEvent event) {
        // Map supported Syirkah events to immutable audit evidence.
        return Uni.createFrom().voidItem();
    }
}
```

This follows the framework's existing projection architecture rather than introducing another event-processing mechanism. syirkah-framework(1)

---

# 11. Add Sharia compliance rules

The audit layer should not itself decide fiqh/accounting policy.

Instead, create explicit compliance rules.

```java
public interface SyirkahComplianceRule {

    String code();

    ComplianceDecision evaluate(
            SyirkahComplianceContext context);
}
```

Context:

```java
public record SyirkahComplianceContext(
        String tenantId,
        String ledgerId,
        String contractId,
        int contractVersion,

        SyirkahType syirkahType,

        BigDecimal totalCapital,
        BigDecimal totalProfitSharingRatio,

        boolean closeLocked,
        boolean snapshotFrozen,
        boolean reconciliationBalanced,

        boolean accountingPosted,
        boolean settlementReconciled
) {
}
```

---

# 12. First compliance rules

### Nisbah must equal 100%

```java
public final class ProfitSharingRatioTotalRule
        implements SyirkahComplianceRule {

    @Override
    public String code() {
        return "SYIRKAH-NISBAH-001";
    }

    @Override
    public ComplianceDecision evaluate(
            SyirkahComplianceContext context) {

        if (context.totalProfitSharingRatio()
                .compareTo(new BigDecimal("100.00")) != 0) {

            return ComplianceDecision.fail(
                    code(),
                    "Total profit-sharing ratio must equal 100%");
        }

        return ComplianceDecision.pass(code());
    }
}
```

### Closed period cannot be redistributed

```java
public final class LockedPeriodMutationRule
        implements SyirkahComplianceRule {

    @Override
    public String code() {
        return "SYIRKAH-CLOSE-001";
    }

    @Override
    public ComplianceDecision evaluate(
            SyirkahComplianceContext context) {

        if (context.closeLocked()
                && !context.reconciliationBalanced()) {

            return ComplianceDecision.fail(
                    code(),
                    "A locked Syirkah period cannot remain unreconciled");
        }

        return ComplianceDecision.pass(code());
    }
}
```

### Termination requires frozen snapshot

```java
public final class TerminationSnapshotRule
        implements SyirkahComplianceRule {

    @Override
    public String code() {
        return "SYIRKAH-TERM-001";
    }

    @Override
    public ComplianceDecision evaluate(
            SyirkahComplianceContext context) {

        if (!context.snapshotFrozen()) {
            return ComplianceDecision.fail(
                    code(),
                    "Syirkah termination requires a frozen predecessor snapshot");
        }

        return ComplianceDecision.pass(code());
    }
}
```

### Accounting must actually be posted

```java
public final class AccountingPostingRule
        implements SyirkahComplianceRule {

    @Override
    public String code() {
        return "SYIRKAH-ACCOUNTING-001";
    }

    @Override
    public ComplianceDecision evaluate(
            SyirkahComplianceContext context) {

        if (!context.accountingPosted()) {
            return ComplianceDecision.fail(
                    code(),
                    "Required Syirkah accounting entries are not posted");
        }

        return ComplianceDecision.pass(code());
    }
}
```

---

# 13. Separate compliance failure from business failure

This distinction matters.

For example:

```text
Business failure:
    Journal posting failed because account does not exist.

Compliance failure:
    Journal posted, but required Syirkah reconciliation is unbalanced.

Workflow failure:
    Distribution has not yet been approved.

Authorization failure:
    User is not permitted to approve distribution.

```

Do not collapse all four into:

```java
IllegalStateException("Invalid Syirkah state")
```

Use structured decisions.

```java
public record ComplianceDecision(
        boolean passed,
        String ruleCode,
        String message
) {

    public static ComplianceDecision pass(String ruleCode) {
        return new ComplianceDecision(true, ruleCode, null);
    }

    public static ComplianceDecision fail(
            String ruleCode,
            String message) {

        return new ComplianceDecision(
                false,
                ruleCode,
                message);
    }
}
```

---

# 14. Add a compliance report for every close

Create:

```java
public record SyirkahComplianceReport(
        String tenantId,
        String ledgerId,
        String contractId,
        int contractVersion,
        String closeCycleId,

        boolean compliant,

        List<ComplianceFinding> findings,

        Instant evaluatedAt,
        String evaluatedBy
) {
}
```

And:

```java
public record ComplianceFinding(
        String ruleCode,
        Severity severity,
        boolean passed,
        String message
) {

    public enum Severity {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }
}
```

This becomes the formal answer to:

> "Why was this Syirkah period allowed to close?"

---

# 15. Maker-checker evidence

We already introduced maker-checker for distribution approval.

Now make the evidence explicit:

```text
CALCULATED
   |
   | actor A
   v
APPROVED
   |
   | actor B
   v
POSTED
```

Reject:

```text
actor A = actor B
```

when the tenant's segregation-of-duties policy requires two people.

Add:

```java
public interface SyirkahApprovalPolicy {

    ApprovalDecision evaluate(
            String operation,
            String maker,
            String checker);
}
```

Default:

```java
public final class SeparateApprovalPolicy
        implements SyirkahApprovalPolicy {

    @Override
    public ApprovalDecision evaluate(
            String operation,
            String maker,
            String checker) {

        if (maker != null
                && maker.equals(checker)) {

            return ApprovalDecision.rejected(
                    "Maker and checker must be different users");
        }

        return ApprovalDecision.approved();
    }
}
```

This should be configurable because not every deployment has identical governance requirements.

---

# 16. Evidence must link accounting and cash

For every important financial action, the audit trail should form a chain:

```text
Calculation
    |
    v
Obligation
    |
    v
Accounting Journal
    |
    v
Settlement
```

Example:

```text
calculationId = CALC-001

distribution obligation
    sourceId = CALC-001

journal
    sourceType = SYIRKAH_DISTRIBUTION
    sourceId   = CALC-001

settlement
    sourceType = SYIRKAH_DISTRIBUTION
    sourceId   = CALC-001
```

Then an auditor can reconstruct the entire flow without interpreting journal descriptions.

---

# 17. Add an audit reconstruction service

This is more valuable than a generic "audit log".

```java
public interface SyirkahAuditReconstructionService {

    Uni<SyirkahAuditTrail> reconstructContract(
            String tenantId,
            String ledgerId,
            String contractId);

    Uni<SyirkahAuditTrail> reconstructClose(
            String tenantId,
            String ledgerId,
            String closeCycleId);

    Uni<SyirkahAuditTrail> reconstructLiquidation(
            String tenantId,
            String ledgerId,
            String liquidationId);
}
```

Output:

```java
public record SyirkahAuditTrail(
        String tenantId,
        String ledgerId,

        String contractId,
        Integer contractVersion,

        List<SyirkahAuditEvidence> evidence,

        Optional<SyirkahCloseSnapshot> closeSnapshot,

        Optional<SyirkahLiquidationResult> liquidationResult,

        List<PartnerDistributionObligation> distributions,

        List<PartnerCapitalReturnObligation> capitalReturns,

        List<SyirkahSettlementObligation> settlements,

        List<String> journalEntryIds,

        boolean evidenceChainValid
) {
}
```

This becomes extremely useful for:

- Sharia supervisory review
- financial audit
- dispute resolution
- internal control
- regulatory review
- debugging
- customer support
- external ecosystem integrations.

---

# 18. Evidence-chain validation

```java
public interface SyirkahAuditChainValidator {

    Uni<SyirkahAuditChainValidation> validate(
            String tenantId,
            String ledgerId,
            String contractId);
}
```

Result:

```java
public record SyirkahAuditChainValidation(
        boolean valid,
        int evidenceCount,
        int invalidLinks,
        List<String> errors
) {
}
```

Validation:

```text
E1
 ↓ hash(E1)
E2
 ↓ hash(E2)
E3
 ↓ hash(E3)
E4
```

If E3 is modified:

```text
E3 hash != E4.previousEvidenceHash
```

The chain becomes invalid.

Again, this is **tamper detection**, not a replacement for database security.

---

# 19. Register it through the existing module architecture

The framework already allows financial modules to register commands, queries, and projections through `ModuleContext`. syirkah-framework(1)

So add:

```java
public final class SyirkahAccountingModule
        implements FinancialModule {

    @Override
    public String code() {
        return "syirkah-accounting";
    }

    @Override
    public String name() {
        return "Syirkah Accounting and Sharia Compliance";
    }

    @Override
    public void register(ModuleContext context) {

        context.registerProjection(
                new SyirkahAuditProjection(
                        auditEventReceiptRepository,
                        auditService));

        context.registerProjection(
                new SyirkahComplianceProjection(
                        complianceRepository));
    }
}
```

This keeps Syirkah as an extension of the existing accounting platform.

---

# 20. Final architecture after this layer

The important architecture is now:

```text
                         SYIRKAH
                            |
        +-------------------+-------------------+
        |                   |                   |
     Contract             Close             Transition
        |                   |                   |
        |              Calculation             |
        |                   |                   |
        |              Allocation               |
        |                   |                   |
        |              Obligation               |
        |                   |                   |
        |              Accounting               |
        |                   |                   |
        |              Settlement               |
        |                   |                   |
        +-------------------+-------------------+
                            |
                       Domain Events
                            |
                       Existing Outbox
                            |
             +--------------+--------------+
             |              |              |
           Audit        Reporting      Integrations
             |
       Compliance Evidence
             |
       Sharia Review / Audit
```

The framework's existing event/outbox/projection architecture is therefore reused rather than duplicated. syirkah-framework(1)

## Most important invariant

From now on, every material Syirkah financial state transition should be reconstructable as:

```text
WHO
  ↓
DID WHAT
  ↓
TO WHICH AKAD
  ↓
IN WHICH TENANT / LEDGER
  ↓
BASED ON WHICH CALCULATION
  ↓
UNDER WHICH CONTRACT VERSION
  ↓
WHICH OBLIGATION WAS CREATED
  ↓
WHICH JOURNAL WAS POSTED
  ↓
WHICH CASH SETTLEMENT OCCURRED
  ↓
WHICH APPROVAL AUTHORIZED IT
```

That is the foundation for a serious **Sharia-governed financial platform**, rather than simply an ERP with a `SyirkahType` field.

The next layer after this should be **Sharia policy/version management**: effective-dated rules for nisbah, loss treatment, retained profit, responsibility/recovery, accounting treatment, and settlement/termination policy, so that an old closed period remains explainable under the policy that was actually effective at that time.

///////////////


Yes — **`TenantId` should be used instead of raw `String tenantId`** at the domain/application boundary.

The earlier `String tenantId` was an inconsistency in the proposed interface, not a deliberate architectural choice.

For example, instead of:

```java
Uni<Boolean> tryAcquire(
    String tenantId,
    String ledgerId,
    UUID eventId
);
```

prefer:

```java
Uni<Boolean> tryAcquire(
    TenantId tenantId,
    LedgerId ledgerId,
    UUID eventId
);
```

And consistently:

```java
public interface SyirkahAuditEventReceiptRepository {

    Uni<Boolean> tryAcquire(
            TenantId tenantId,
            LedgerId ledgerId,
            UUID eventId);
}
```

### Why this is better

You already have `TenantId` and `LedgerId` as domain concepts. Using them gives us:

- compile-time type safety
- no accidental tenant/ledger ID mixing
- consistent validation
- consistent equality/value semantics
- clearer domain boundaries
- easier refactoring later

For example, this should be impossible:

```java
tryAcquire(ledgerId, tenantId, eventId); // compile-time error
```

whereas two `String` parameters allow the mistake.

### I would apply this consistently

For Syirkah domain/application ports, prefer:

```java
TenantId tenantId
LedgerId ledgerId
```

rather than:

```java
String tenantId
String ledgerId
```

So interfaces such as:

```java
public interface SyirkahAuditEvidenceRepository {

    Uni<Void> append(SyirkahAuditEvidence evidence);

    Uni<List<SyirkahAuditEvidence>> findByContract(
            TenantId tenantId,
            LedgerId ledgerId,
            String contractId);

    Uni<List<SyirkahAuditEvidence>> findByCloseCycle(
            TenantId tenantId,
            LedgerId ledgerId,
            String closeCycleId);
}
```

and:

```java
public interface SyirkahTerminationSnapshotProvider {

    Uni<SyirkahCloseSnapshot> requireLockedSnapshot(
            TenantId tenantId,
            LedgerId ledgerId,
            String contractId,
            String closeCycleId);
}
```

### One nuance

I would **not blindly replace every `String` in the framework**.

There is a useful distinction:

```text
TenantId / LedgerId
    = established domain identity types

ContractId / CloseCycleId / CalculationId / TransitionId
    = should ideally use their existing domain ID types where they already exist

partyId / accountId / externalReference
    = use String only when the framework does not yet have a dedicated value type
```

So the next implementation should follow the **existing Syirkah/framework ID types**, rather than introducing new wrappers unnecessarily.

In particular, for the policy-version layer we're about to build, I would carry `TenantId` and `LedgerId` throughout the domain/application ports and only convert to database/string representations at the infrastructure boundary.


/////////////

The framework confirms the existing direction: `TenantId` and `LedgerId` are already first-class types in accounting queries/events, so the Sharia policy layer should follow that convention. syirkah-framework(1) syirkah-framework(1)

## 1. Do not create one giant `SyirkahPolicy`

I recommend **composable, versioned policies**.

```text
SyirkahPolicySet
 ├── ProfitSharingPolicy
 ├── LossAllocationPolicy
 ├── ResultDispositionPolicy
 ├── DistributionAccountingPolicy
 ├── LossRecoveryAccountingPolicy
 ├── SettlementCompletionPolicy
 └── LiquidationResultPolicy
```

Each policy can evolve independently.

This is important because a change to, for example, settlement completion rules should not create a new version of the profit-sharing calculation unnecessarily.

---

## 2. Policy identity

Introduce a dedicated policy identifier:

```java
public record ShariaPolicyId(String value) {

    public ShariaPolicyId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ShariaPolicyId cannot be blank");
        }
    }

    public static ShariaPolicyId of(String value) {
        return new ShariaPolicyId(value);
    }
}
```

And a policy version identifier:

```java
public record ShariaPolicyVersionId(
        ShariaPolicyId policyId,
        int version
) {

    public ShariaPolicyVersionId {
        if (policyId == null) {
            throw new IllegalArgumentException("policyId cannot be null");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
    }
}
```

This gives us an explicit historical reference:

```text
SYIRKAH-MUDHARABAH-LOSS / version 3
```

rather than simply:

```text
"Mudharabah loss policy"
```

---

## 3. Policy status

A policy version needs its own lifecycle:

```java
public enum ShariaPolicyVersionStatus {
    DRAFT,
    APPROVED,
    EFFECTIVE,
    RETIRED,
    CANCELLED
}
```

Important distinction:

```text
DRAFT
   ↓
APPROVED
   ↓
EFFECTIVE
   ↓
RETIRED
```

An `EFFECTIVE` policy is not necessarily the newest policy.

For example:

```text
Policy v1
effective: 2026-01-01 → 2026-06-30

Policy v2
effective: 2026-07-01 → present
```

A June close must continue resolving to v1 even after v2 becomes effective.

---

## 4. Scope

The policy context should use the actual domain identity types:

```java
public record SyirkahPolicyContext(
        TenantId tenantId,
        LedgerId ledgerId,
        String contractId,
        int contractVersion,
        SyirkahType syirkahType,
        LocalDate effectiveDate,
        String currencyCode
) {
    public SyirkahPolicyContext {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(ledgerId, "ledgerId cannot be null");
        Objects.requireNonNull(syirkahType, "syirkahType cannot be null");
        Objects.requireNonNull(effectiveDate, "effectiveDate cannot be null");

        if (contractId == null || contractId.isBlank()) {
            throw new IllegalArgumentException("contractId cannot be blank");
        }

        if (contractVersion <= 0) {
            throw new IllegalArgumentException("contractVersion must be positive");
        }
    }
}
```

This is deliberately contextual.

A policy can therefore be resolved according to:

```text
Tenant
  ↓
Ledger
  ↓
Contract
  ↓
Contract Version
  ↓
Syirkah Type
  ↓
Effective Date
```

But I would **not encode that precedence directly into every policy**. Put resolution rules in one resolver.

---

## 5. Policy definition

The policy definition should describe **what kind of policy** it is, independently of its implementation.

```java
public enum ShariaPolicyType {
    PROFIT_SHARING,
    LOSS_ALLOCATION,
    RESULT_DISPOSITION,
    DISTRIBUTION_ACCOUNTING,
    LOSS_RECOVERY_ACCOUNTING,
    SETTLEMENT_COMPLETION,
    LIQUIDATION_RESULT
}
```

Then:

```java
public record ShariaPolicyDefinition(
        ShariaPolicyId policyId,
        ShariaPolicyType policyType,
        String name,
        String description
) {
    public ShariaPolicyDefinition {
        Objects.requireNonNull(policyId, "policyId cannot be null");
        Objects.requireNonNull(policyType, "policyType cannot be null");

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
    }
}
```

---

## 6. Versioned policy

The actual version contains the effective rules/configuration.

```java
public record ShariaPolicyVersion(
        ShariaPolicyVersionId id,
        ShariaPolicyDefinition definition,
        ShariaPolicyVersionStatus status,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil,
        String ruleConfiguration,
        String sourceReference,
        String approvedBy,
        Instant approvedAt
) {
    public ShariaPolicyVersion {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom cannot be null");

        if (effectiveUntil != null && effectiveUntil.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException(
                    "effectiveUntil cannot be before effectiveFrom");
        }
    }

    public boolean appliesOn(LocalDate date) {
        if (date.isBefore(effectiveFrom)) {
            return false;
        }

        return effectiveUntil == null || !date.isAfter(effectiveUntil);
    }
}
```

### One important design point

`ruleConfiguration` should **not become arbitrary executable Java**.

It should be structured configuration or a reference to a registered policy implementation.

For example:

```text
policy:
    SYIRKAH-MUDHARABAH-LOSS
version:
    3
implementation:
    MUDHARABAH_LOSS_ALLOCATION_V3
configuration:
    ...
```

The engine remains trusted code.

---

# 7. Policy resolution

Create one resolver:

```java
public interface ShariaPolicyResolver {

    Uni<ShariaPolicyVersion> resolve(
            TenantId tenantId,
            LedgerId ledgerId,
            String contractId,
            int contractVersion,
            ShariaPolicyType policyType,
            LocalDate effectiveDate);
}
```

Then specialized application ports can be cleaner:

```java
public interface SyirkahPolicySetResolver {

    Uni<SyirkahPolicySet> resolve(
            SyirkahPolicyContext context);
}
```

Where:

```java
public record SyirkahPolicySet(
        ShariaPolicyVersion profitSharingPolicy,
        ShariaPolicyVersion lossAllocationPolicy,
        ShariaPolicyVersion resultDispositionPolicy,
        ShariaPolicyVersion distributionAccountingPolicy,
        ShariaPolicyVersion lossRecoveryAccountingPolicy,
        ShariaPolicyVersion settlementCompletionPolicy,
        ShariaPolicyVersion liquidationResultPolicy
) {}
```

This gives the close engine one immutable policy set for its calculation.

---

# 8. Do not resolve policies repeatedly during one close

This is extremely important.

Bad:

```text
calculate result
   ↓
resolve policy

allocate result
   ↓
resolve policy again

prepare distribution
   ↓
resolve policy again

post accounting
   ↓
resolve policy again
```

A policy could have changed between those operations.

Instead:

```text
Close starts
    ↓
Resolve policy set
    ↓
Freeze policy references
    ↓
Calculate
    ↓
Allocate
    ↓
Distribution
    ↓
Accounting
```

The close calculation should therefore carry policy references.

---

# 9. Policy snapshot

I recommend an immutable snapshot:

```java
public record SyirkahPolicySnapshot(
        TenantId tenantId,
        LedgerId ledgerId,
        String contractId,
        int contractVersion,
        LocalDate effectiveDate,

        ShariaPolicyVersionId profitSharingPolicy,
        ShariaPolicyVersionId lossAllocationPolicy,
        ShariaPolicyVersionId resultDispositionPolicy,
        ShariaPolicyVersionId distributionAccountingPolicy,
        ShariaPolicyVersionId lossRecoveryAccountingPolicy,
        ShariaPolicyVersionId settlementCompletionPolicy,
        ShariaPolicyVersionId liquidationResultPolicy,

        String snapshotHash
) {}
```

Then:

```text
Syirkah Close
      │
      ├── Financial result
      ├── Allocation
      ├── Obligations
      ├── Accounting
      │
      └── Policy Snapshot
             ├── Profit policy v2
             ├── Loss policy v3
             ├── Distribution policy v1
             └── Settlement policy v4
```

That makes historical reconstruction deterministic.

---

# 10. Add the snapshot to the existing close snapshot

The existing `SyirkahCloseSnapshot` should reference:

```java
SyirkahPolicySnapshot policySnapshot
```

rather than storing only a generic policy name.

Then:

```text
Close Snapshot
 ├── contractId
 ├── contractVersion
 ├── financialResult
 ├── allocations
 ├── partner positions
 ├── responsibilities
 ├── reconciliation
 └── policySnapshot
```

This is particularly important for **tutup buku**.

Suppose today's policy says:

```text
Mudharabah loss policy = v5
```

but the March 2026 close used:

```text
Mudharabah loss policy = v3
```

The March snapshot remains explainable.

---

# 11. Policy repositories

Keep persistence behind ports:

```java
public interface ShariaPolicyRepository {

    Uni<Optional<ShariaPolicyDefinition>> findDefinition(
            TenantId tenantId,
            LedgerId ledgerId,
            ShariaPolicyId policyId);

    Uni<Optional<ShariaPolicyVersion>> findVersion(
            TenantId tenantId,
            LedgerId ledgerId,
            ShariaPolicyVersionId versionId);

    Uni<List<ShariaPolicyVersion>> findEffectiveVersions(
            TenantId tenantId,
            LedgerId ledgerId,
            ShariaPolicyType policyType,
            LocalDate effectiveDate);

    Uni<Void> saveVersion(
            TenantId tenantId,
            LedgerId ledgerId,
            ShariaPolicyVersion version);
}
```

The database should enforce what the domain expects.

For example:

```text
UNIQUE (
    tenant_id,
    ledger_id,
    policy_id,
    version
)
```

And effective periods for the same policy scope should not overlap.

---

# 12. Policy approval must be auditable

A policy change is materially different from ordinary configuration.

We should have:

```text
Policy created
      ↓
Policy approved
      ↓
Policy becomes effective
      ↓
Policy retired
```

Each transition should produce audit evidence.

The existing audit architecture can therefore use:

```java
public enum SyirkahEvidenceType {
    // existing values...

    SHARIA_POLICY_CREATED,
    SHARIA_POLICY_APPROVED,
    SHARIA_POLICY_EFFECTIVE,
    SHARIA_POLICY_RETIRED
}
```

No second audit system is necessary.

---

# 13. Policy source metadata

Because this is Islamic-law-related functionality, we should preserve **source provenance**, but not pretend the software itself is the religious authority.

For example:

```text
sourceReference:
    PSAK 105, section ...

jurisdiction:
    Indonesia

school:
    null / configured

externalReference:
    ...
```

The platform should record:

```text
"This accounting/business policy was approved for this tenant."
```

rather than claiming:

```text
"This software determines the universally correct Islamic ruling."
```

That's an important architectural boundary.

---

# 14. How this connects to the policies we already created

The existing concrete policies become implementations behind the version resolver.

For example:

```text
LossAllocationPolicy
        ↑
        │
MudharabahLossAllocationPolicy
CapitalProportionLossAllocationPolicy
```

becomes:

```text
ShariaPolicyVersion
        │
        └── implementationKey
                │
                ├── MUDHARABAH_LOSS_V1
                ├── MUDHARABAH_LOSS_V2
                └── CAPITAL_PROPORTION_LOSS_V1
```

Likewise:

```text
SyirkahLiquidationResultPolicy
SyirkahDistributionAccountingPolicy
SyirkahSettlementCompletionPolicy
```

remain **interfaces/strategies**, not database records containing arbitrary code.

The database says **which approved version applies**; the application contains the trusted implementation.

---

# 15. Final architecture

This gives us a clean separation:

```text
                 ┌──────────────────────────┐
                 │ Sharia Policy Registry   │
                 └────────────┬─────────────┘
                              │
                     effective policy
                              │
                              ▼
                 ┌──────────────────────────┐
                 │ Policy Resolver          │
                 └────────────┬─────────────┘
                              │
                       Policy Snapshot
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
   Result Calculation   Distribution        Liquidation
          │                   │                   │
          ▼                   ▼                   ▼
     Close Snapshot      Accounting          Termination
          │
          ▼
      Audit Evidence
```

And the historical guarantee becomes:

```text
Current Policy
     ≠
Historical Close Policy

Historical Close
     └── immutable PolicySnapshot
              └── exact policy versions used
```

That is the key reason for introducing this layer **before** we add more Sharia business rules.

Next, I would implement the **policy registry + resolver + immutable policy snapshot**, then wire it into the existing close/liquidation flow. This avoids adding more hardcoded Sharia behavior to the aggregates.

//////////////

Next, the natural layer is **policy governance and effective-dated resolution**: turning the policy model into something operationally safe—approval, activation, retirement, conflict detection, and deterministic resolution.

The key rule is:

> **A policy version must never silently become effective merely because it exists.**

### 1. Policy governance state machine

```text
DRAFT
  │
  ▼
APPROVED
  │
  ▼
EFFECTIVE
  │
  ▼
RETIRED
```

Invalid transitions should fail:

```text
DRAFT      → EFFECTIVE   ❌
DRAFT      → RETIRED     ❌
RETIRED    → EFFECTIVE   ❌
CANCELLED  → EFFECTIVE   ❌
```

This gives us:

```java
public final class ShariaPolicyVersionLifecycle {

    public void approve(String approver) {
        // DRAFT -> APPROVED
    }

    public void makeEffective(LocalDate effectiveFrom) {
        // APPROVED -> EFFECTIVE
    }

    public void retire(LocalDate retirementDate) {
        // EFFECTIVE -> RETIRED
    }

    public void cancel() {
        // DRAFT/APPROVED -> CANCELLED
    }
}
```

I would keep this lifecycle inside the policy aggregate rather than allowing repositories/services to mutate the status directly.

---

### 2. Effective-date conflict detection

Suppose we have:

```text
Policy v1: 2026-01-01 → 2026-06-30
Policy v2: 2026-06-15 → 2026-12-31
```

That must be rejected if both belong to the same scope.

The repository/application layer should provide:

```java
public interface ShariaPolicyConflictChecker {

    Uni<Boolean> hasEffectiveDateConflict(
            TenantId tenantId,
            LedgerId ledgerId,
            ShariaPolicyId policyId,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            ShariaPolicyVersionId excludedVersion);
}
```

The important part is that **overlap detection is not merely an application convention**.

The database should enforce it where practical as well.

---

### 3. Policy scope must be explicit

We should avoid an ambiguous resolution hierarchy.

Define scope explicitly:

```java
public enum ShariaPolicyScope {
    TENANT,
    LEDGER,
    CONTRACT,
    CONTRACT_VERSION
}
```

Then a policy version can say:

```java
public record ShariaPolicyScopeReference(
        ShariaPolicyScope scope,
        String contractId,
        Integer contractVersion
) {
}
```

Rules:

```text
TENANT
    → applies to tenant

LEDGER
    → applies to tenant + ledger

CONTRACT
    → applies to specific contract

CONTRACT_VERSION
    → applies to one exact akad version
```

This matters because a new contract version may intentionally change the applicable policy without changing the tenant-wide default.

---

### 4. Deterministic precedence

The resolver can then use:

```text
CONTRACT_VERSION
      ↓
CONTRACT
      ↓
LEDGER
      ↓
TENANT
```

The first applicable approved/effective policy wins.

But there is an important safety rule:

> **If two policies exist at the same precedence level, fail closed.**

Do not arbitrarily select one.

For example:

```text
CONTRACT_VERSION
    v3 → LossPolicy A
    v3 → LossPolicy B
```

Result:

```text
POLICY_CONFIGURATION_ERROR
```

not:

```text
"take the newest record"
```

This is particularly important for financial close.

---

### 5. Resolution result

Rather than returning only the implementation, return the complete resolution:

```java
public record ShariaPolicyResolution(
        ShariaPolicyVersion version,
        ShariaPolicyScope scope,
        boolean inherited
) {
}
```

Then the system knows not only:

```text
Loss policy = v3
```

but:

```text
Loss policy = v3
scope = CONTRACT
inherited = true
```

That is useful for audit and troubleshooting.

---

### 6. Policy resolver should be deterministic

Conceptually:

```java
public interface ShariaPolicyResolver {

    Uni<ShariaPolicyResolution> resolve(
            SyirkahPolicyContext context,
            ShariaPolicyType policyType);
}
```

Resolution:

```text
1. Find effective CONTRACT_VERSION policies
2. If exactly one → use it
3. If >1 → configuration error
4. Otherwise find CONTRACT
5. Then LEDGER
6. Then TENANT
7. If none → policy missing
```

No fallback to a hidden hardcoded default for financial close.

A default can exist for **policy setup**, but not silently govern a production close.

---

## 7. Policy approval should have separation of duties

We already established maker/checker for financial operations.

Apply the same principle to policy governance:

```java
public interface ShariaPolicyApprovalPolicy {

    ApprovalDecision evaluate(
            String operation,
            String maker,
            String checker);
}
```

Default:

```text
maker == checker
      ↓
REJECT
```

So:

```text
Alice creates policy
Bob approves policy
```

is valid.

```text
Alice creates policy
Alice approves policy
```

is rejected when maker-checker is required.

---

## 8. Policy effective activation

Making a policy effective should perform several checks:

```text
Policy APPROVED
      │
      ├── valid effective date
      ├── no overlapping version
      ├── approved by authorized actor
      ├── maker != checker where required
      └── policy implementation exists
                │
                ▼
            EFFECTIVE
```

The implementation registry is important.

For example:

```java
public interface ShariaPolicyImplementationRegistry {

    boolean supports(
            ShariaPolicyType policyType,
            String implementationKey);
}
```

So the database cannot activate:

```text
implementationKey = "WHATEVER_UNKNOWN_POLICY"
```

---

## 9. Separate policy data from policy implementation

This distinction is fundamental:

```text
Database
────────
Policy ID
Version
Scope
Effective dates
Status
Implementation key
Configuration
Source reference
Approval metadata
```

versus:

```text
Application code
────────────────
MUDHARABAH_LOSS_V1
MUDHARABAH_LOSS_V2
CAPITAL_PROPORTION_LOSS_V1
...
```

The database chooses **which trusted algorithm applies**.

It does not dynamically execute arbitrary code stored in the database.

---

## 10. Policy snapshot must be created at calculation boundary

For `tutup buku`:

```text
Begin close
    ↓
Resolve all required policies
    ↓
Validate policies
    ↓
Create immutable policy snapshot
    ↓
Calculate result
```

If policy resolution fails:

```text
Close calculation = BLOCKED
```

not:

```text
Continue using defaults
```

This is one of the strongest safeguards we should add.

---

## 11. Recalculation behavior

Suppose:

```text
March close
policy v2
```

was calculated but not yet locked.

Then policy v3 becomes effective.

What happens?

The existing calculation should **retain v2** because the calculation already has a policy snapshot.

If the operator explicitly requests a new calculation:

```text
Recalculate
    ↓
new policy resolution
    ↓
new calculation ID
    ↓
new policy snapshot
```

Never mutate the old calculation's policy reference.

Therefore:

```text
Calculation A
    policy v2

Calculation B
    policy v3
```

Both remain explainable.

---

## 12. Policy snapshot hash

We already proposed `snapshotHash`.

I would make it canonical:

```text
tenantId
ledgerId
contractId
contractVersion
effectiveDate
policyType
policyId
policyVersion
scope
implementationKey
configuration hash
```

Then hash the canonical representation.

This gives:

```text
PolicySnapshot
      ↓
SHA-256
      ↓
snapshotHash
```

The hash is useful in audit evidence:

```text
CLOSE_RESULT_CALCULATED
    policySnapshotHash = abc123...
```

Later we can prove that the recorded policy snapshot has not changed.

---

# 13. Policy changes must not mutate history

This should become an explicit invariant:

```text
Historical close
    ↓
historical policy snapshot
    ↓
immutable
```

Never do:

```java
existingPolicy.setVersion(4);
```

Instead:

```text
Policy v3 → RETIRED

Policy v4 → new record
```

Likewise, changing:

- nisbah rules
- loss treatment
- retained-profit treatment
- accounting mapping
- settlement requirements

creates a **new policy version**, not an update to an old effective version.

---

# 14. Compliance integration

The existing framework already has accounting compliance configuration and Sharia compliance concepts, including `ComplianceConfiguration` with `enforceShariaContracts`, Zakat support, strict period locking, and Sharia/SAK compliance standards. syirkah-framework(1)

We should therefore **extend that compliance mechanism**, not create a second compliance framework.

Add Syirkah-specific rules such as:

```text
SYIRKAH-POLICY-001
    Every close must reference an effective policy set.

SYIRKAH-POLICY-002
    Policy versions must be approved before effectiveness.

SYIRKAH-POLICY-003
    Historical close must retain its policy snapshot.

SYIRKAH-POLICY-004
    Overlapping effective policy versions are prohibited.

SYIRKAH-POLICY-005
    Unknown policy implementation cannot become effective.
```

---

# 15. Final policy lifecycle

The resulting flow is:

```text
                 ┌──────────────┐
                 │    DRAFT     │
                 └──────┬───────┘
                        │ approve
                        ▼
                 ┌──────────────┐
                 │   APPROVED   │
                 └──────┬───────┘
                        │ effective
                        ▼
                 ┌──────────────┐
                 │   EFFECTIVE  │
                 └──────┬───────┘
                        │ retire
                        ▼
                 ┌──────────────┐
                 │   RETIRED    │
                 └──────────────┘
```

And for closing:

```text
Contract
   │
   ▼
Policy Resolver
   │
   ├── Profit policy
   ├── Loss policy
   ├── Distribution policy
   ├── Recovery policy
   ├── Settlement policy
   └── Liquidation policy
   │
   ▼
Immutable Policy Snapshot
   │
   ▼
Syirkah Calculation
   │
   ▼
Close Snapshot
   │
   ▼
Audit Evidence
```

### The architectural result

We now have three separate concepts that should **never be conflated**:

```text
Islamic source / legal reference
        ↓
Sharia policy approved by the organization
        ↓
Executable policy implementation
```

That separation will make Syirkah much safer to evolve across different jurisdictions, organizational interpretations, PSAK/SAK requirements, and future policy revisions.

**The next layer after this should be the actual `SyirkahPolicyRegistry` / resolver implementation and its integration into `SyirkahCloseProcessor`, including idempotent policy snapshots.**

////////////

Good. Let’s make this concrete now.

## 1. Policy registry should be a domain/application port

I would place the policy model in the existing Islamic capability rather than creating another module:

```text
finance/accounting/accounting-capabilities/
└── syirkah-accounting-islamic/
    └── src/main/java/
        .../domain/policy/
```

The registry port:

```java
public interface ShariaPolicyRepository {

    Uni<Optional<ShariaPolicyDefinition>> findDefinition(
            TenantId tenantId,
            LedgerId ledgerId,
            ShariaPolicyId policyId);

    Uni<Optional<ShariaPolicyVersion>> findVersion(
            TenantId tenantId,
            LedgerId ledgerId,
            ShariaPolicyVersionId versionId);

    Uni<List<ShariaPolicyVersion>> findCandidates(
            TenantId tenantId,
            LedgerId ledgerId,
            ShariaPolicyType policyType,
            LocalDate effectiveDate);

    Uni<Void> saveDefinition(
            TenantId tenantId,
            LedgerId ledgerId,
            ShariaPolicyDefinition definition);

    Uni<Void> saveVersion(
            TenantId tenantId,
            LedgerId ledgerId,
            ShariaPolicyVersion version);
}
```

Notice again: **`TenantId` and `LedgerId`, not `String`**.

---

# 2. Add explicit scope to the version

Rather than making the resolver infer scope from nullable fields, make it explicit.

```java
public record ShariaPolicyScopeReference(
        ShariaPolicyScope scope,
        String contractId,
        Integer contractVersion
) {

    public ShariaPolicyScopeReference {
        Objects.requireNonNull(scope, "scope cannot be null");

        switch (scope) {
            case TENANT, LEDGER -> {
                if (contractId != null || contractVersion != null) {
                    throw new IllegalArgumentException(
                            "Tenant and ledger policies cannot contain contract scope");
                }
            }
            case CONTRACT -> {
                if (contractId == null || contractId.isBlank()) {
                    throw new IllegalArgumentException(
                            "Contract policy requires contractId");
                }
                if (contractVersion != null) {
                    throw new IllegalArgumentException(
                            "Contract policy cannot contain contractVersion");
                }
            }
            case CONTRACT_VERSION -> {
                if (contractId == null || contractId.isBlank()) {
                    throw new IllegalArgumentException(
                            "Contract version policy requires contractId");
                }
                if (contractVersion == null || contractVersion <= 0) {
                    throw new IllegalArgumentException(
                            "Contract version policy requires positive contractVersion");
                }
            }
        }
    }
}
```

This prevents invalid states from reaching the resolver.

---

# 3. Policy version becomes immutable

The policy version should not expose setters.

```java
public final class ShariaPolicyVersion {

    private final ShariaPolicyVersionId id;
    private final ShariaPolicyDefinition definition;
    private final ShariaPolicyScopeReference scope;
    private final LocalDate effectiveFrom;
    private final LocalDate effectiveUntil;
    private final String implementationKey;
    private final String ruleConfiguration;
    private final String sourceReference;

    private ShariaPolicyVersionStatus status;

    private String approvedBy;
    private Instant approvedAt;

    public ShariaPolicyVersion(
            ShariaPolicyVersionId id,
            ShariaPolicyDefinition definition,
            ShariaPolicyScopeReference scope,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            String implementationKey,
            String ruleConfiguration,
            String sourceReference) {

        this.id = Objects.requireNonNull(id);
        this.definition = Objects.requireNonNull(definition);
        this.scope = Objects.requireNonNull(scope);
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom);

        if (effectiveUntil != null && effectiveUntil.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException(
                    "effectiveUntil cannot be before effectiveFrom");
        }

        if (implementationKey == null || implementationKey.isBlank()) {
            throw new IllegalArgumentException(
                    "implementationKey cannot be blank");
        }

        this.effectiveUntil = effectiveUntil;
        this.implementationKey = implementationKey;
        this.ruleConfiguration = ruleConfiguration;
        this.sourceReference = sourceReference;
        this.status = ShariaPolicyVersionStatus.DRAFT;
    }
}
```

The lifecycle methods then control state changes.

---

# 4. Approval

```java
public void approve(String approver) {
    requireNonBlank(approver, "approver");

    if (status != ShariaPolicyVersionStatus.DRAFT) {
        throw new IllegalStateException(
                "Only draft policy versions can be approved");
    }

    status = ShariaPolicyVersionStatus.APPROVED;
    approvedBy = approver;
    approvedAt = Instant.now();
}
```

Then effectiveness:

```java
public void makeEffective() {
    if (status != ShariaPolicyVersionStatus.APPROVED) {
        throw new IllegalStateException(
                "Only approved policy versions can become effective");
    }

    status = ShariaPolicyVersionStatus.EFFECTIVE;
}
```

But **the application service must perform conflict checking before calling `makeEffective()`**.

---

# 5. Policy implementation registry

We need a trusted registry:

```java
public interface ShariaPolicyImplementationRegistry {

    boolean supports(
            ShariaPolicyType policyType,
            String implementationKey);

    Object resolve(
            ShariaPolicyType policyType,
            String implementationKey);
}
```

I would eventually make this strongly typed instead of returning `Object`.

For example:

```java
public interface ShariaPolicyImplementationRegistry {

    LossAllocationPolicy resolveLossAllocation(String implementationKey);

    SyirkahDistributionAccountingPolicy resolveDistributionAccounting(
            String implementationKey);

    SyirkahLiquidationResultPolicy resolveLiquidationResult(
            String implementationKey);

    SyirkahSettlementCompletionPolicy resolveSettlementCompletion(
            String implementationKey);
}
```

That is safer.

---

# 6. Resolver algorithm

The resolver is now straightforward:

```text
CONTRACT_VERSION
      │
      ├── exactly 1 → use
      ├── >1        → fail
      │
      ▼
CONTRACT
      │
      ├── exactly 1 → use
      ├── >1        → fail
      │
      ▼
LEDGER
      │
      ├── exactly 1 → use
      ├── >1        → fail
      │
      ▼
TENANT
      │
      ├── exactly 1 → use
      ├── >1        → fail
      │
      ▼
NO POLICY → fail
```

Implementation:

```java
@ApplicationScoped
public class DefaultShariaPolicyResolver
        implements ShariaPolicyResolver {

    private final ShariaPolicyRepository repository;

    public DefaultShariaPolicyResolver(
            ShariaPolicyRepository repository) {
        this.repository = repository;
    }

    @Override
    public Uni<ShariaPolicyResolution> resolve(
            SyirkahPolicyContext context,
            ShariaPolicyType policyType) {

        return repository.findCandidates(
                context.tenantId(),
                context.ledgerId(),
                policyType,
                context.effectiveDate()
        ).map(candidates ->
                resolveCandidate(candidates, context));
    }

    private ShariaPolicyResolution resolveCandidate(
            List<ShariaPolicyVersion> candidates,
            SyirkahPolicyContext context) {

        for (ShariaPolicyScope scope : precedence()) {

            List<ShariaPolicyVersion> matches =
                    candidates.stream()
                            .filter(policy ->
                                    policy.scope().scope() == scope)
                            .filter(policy ->
                                    policy.appliesOn(context.effectiveDate()))
                            .toList();

            if (matches.size() > 1) {
                throw new IllegalStateException(
                        "Multiple effective Sharia policies found for "
                                + scope + " and policy type");
            }

            if (matches.size() == 1) {
                return new ShariaPolicyResolution(
                        matches.getFirst(),
                        scope,
                        scope != ShariaPolicyScope.CONTRACT_VERSION
                );
            }
        }

        throw new IllegalStateException(
                "No effective Sharia policy found for "
                        + policyType);
    }

    private List<ShariaPolicyScope> precedence() {
        return List.of(
                ShariaPolicyScope.CONTRACT_VERSION,
                ShariaPolicyScope.CONTRACT,
                ShariaPolicyScope.LEDGER,
                ShariaPolicyScope.TENANT
        );
    }
}
```

One refinement: the repository should ideally already filter by tenant/ledger/effective date, while the resolver performs the final scope/ambiguity validation.

---

# 7. Resolve the complete policy set

The close process should not call the resolver seven times independently.

Create:

```java
public interface SyirkahPolicySetResolver {

    Uni<SyirkahPolicySetResolution> resolve(
            SyirkahPolicyContext context);
}
```

And:

```java
public record SyirkahPolicySetResolution(
        SyirkahPolicySnapshot snapshot,
        LossAllocationPolicy lossAllocationPolicy,
        SyirkahDistributionAccountingPolicy distributionAccountingPolicy,
        SyirkahSettlementCompletionPolicy settlementCompletionPolicy,
        SyirkahLiquidationResultPolicy liquidationResultPolicy
) {}
```

Other policies can be added as their executable implementations are introduced.

---

# 8. Snapshot creation

The resolver creates the immutable snapshot **once**:

```java
public interface SyirkahPolicySnapshotFactory {

    SyirkahPolicySnapshot create(
            SyirkahPolicyContext context,
            Map<ShariaPolicyType, ShariaPolicyResolution> resolutions);
}
```

The snapshot contains only references and canonical metadata.

It should not contain executable objects.

```text
Snapshot
    ↓
policyId/version/scope/implementationKey
```

not:

```text
Snapshot
    ↓
Java object / strategy instance
```

---

# 9. Policy snapshot persistence

Add:

```java
public interface SyirkahPolicySnapshotRepository {

    Uni<Optional<SyirkahPolicySnapshot>> findByCloseCycle(
            TenantId tenantId,
            LedgerId ledgerId,
            CloseCycleId closeCycleId);

    Uni<Optional<SyirkahPolicySnapshot>> findByCalculation(
            TenantId tenantId,
            LedgerId ledgerId,
            SyirkahCalculationId calculationId);

    Uni<Void> save(
            TenantId tenantId,
            LedgerId ledgerId,
            CloseCycleId closeCycleId,
            SyirkahCalculationId calculationId,
            SyirkahPolicySnapshot snapshot);
}
```

And enforce:

```text
UNIQUE (
    tenant_id,
    ledger_id,
    close_cycle_id
)
```

and:

```text
UNIQUE (
    tenant_id,
    ledger_id,
    calculation_id
)
```

---

# 10. Important idempotency behavior

Suppose the close request is retried.

```text
calculate(closeCycle-123)
```

First request:

```text
no snapshot
    ↓
resolve policies
    ↓
create snapshot
    ↓
save
    ↓
calculate
```

Second request:

```text
snapshot already exists
    ↓
return existing snapshot
    ↓
use existing policy versions
```

It must **not resolve today's policies again**.

This is particularly important for distributed workers.

---

# 11. Integrate into `SyirkahCloseProcessor`

The existing processor should become conceptually:

```java
public Uni<CloseCalculation> calculateResult(
        CloseRequest request) {

    SyirkahPolicyContext context =
            policyContextFactory.create(request);

    return policySetResolver.resolve(context)
            .chain(policySet ->
                    policySnapshotRepository
                            .findByCloseCycle(
                                    request.tenantId(),
                                    request.ledgerId(),
                                    CloseCycleId.of(request.closeCycleId()))
                            .map(existing -> new ResolvedPolicy(
                                    existing,
                                    policySet)))
            .chain(resolved ->
                    calculateUsingPolicy(request, resolved));
}
```

But there is one subtle issue.

**Snapshot creation and calculation must be transactionally coordinated.**

Otherwise:

```text
snapshot saved
     ↓
calculation fails
```

and retry behavior becomes ambiguous.

So the production implementation should eventually use the same transaction boundary as the close calculation persistence.

---

# 12. Close calculation must reference policy snapshot

The calculation should carry:

```java
public record CloseCalculation(
        SyirkahCalculationId calculationId,
        String closeCycleId,
        String contractId,
        int contractVersion,

        BigDecimal revenue,
        BigDecimal directCosts,
        BigDecimal operatingCosts,
        BigDecimal otherCosts,
        BigDecimal netResult,

        ProfitSharingResult.ResultType resultType,

        List<PartnerAllocation> allocations,

        String policySnapshotHash
) {}
```

The hash is enough for quick correlation, while the actual snapshot remains separately queryable.

---

# 13. Historical replay

This gives us two distinct operations.

### Normal calculation

```text
Close request
    ↓
Resolve current effective policies
    ↓
Freeze policy snapshot
    ↓
Calculate
```

### Historical reconstruction

```text
Close cycle
    ↓
Load frozen policy snapshot
    ↓
Load exact policy versions
    ↓
Reconstruct calculation
```

Historical reconstruction must **not** call the current policy resolver.

That distinction is essential.

---

# 14. The same mechanism applies to liquidation

For termination:

```text
Locked Close Snapshot
        ↓
Resolve / load policy snapshot
        ↓
Liquidation
        ↓
Liquidation Result Policy
        ↓
Distribution
        ↓
Capital Return
        ↓
Settlement
```

If the liquidation already has a frozen policy snapshot, it must use that snapshot.

No policy drift halfway through termination.

---

# 15. Database model

At minimum:

```text
sharia_policy_definition
────────────────────────
tenant_id
ledger_id
policy_id
policy_type
name
description
created_at


sharia_policy_version
─────────────────────
tenant_id
ledger_id
policy_id
version
scope
contract_id
contract_version
effective_from
effective_until
status
implementation_key
rule_configuration
source_reference
approved_by
approved_at
created_at
```

Constraints:

```text
UNIQUE (
    tenant_id,
    ledger_id,
    policy_id,
    version
)
```

And indexes:

```text
(
    tenant_id,
    ledger_id,
    policy_type,
    status,
    effective_from
)
```

plus scope-specific lookup indexes.

---

# 16. One thing I would NOT do

Don't put all Sharia configuration into:

```text
compliance_configuration
```

The existing `ComplianceConfiguration` is appropriate for broad accounting compliance/feature settings, such as Sharia enforcement, Zakat, period locking, and dual-ledger behavior. syirkah-framework(1)

But this:

```text
"enforceShariaContracts = true"
```

is fundamentally different from:

```text
"for Mudharabah v3, normal business loss is allocated according to..."
```

So:

```text
ComplianceConfiguration
    ↓
whether/how Sharia compliance is enabled

ShariaPolicyRegistry
    ↓
which specific business/accounting policy applies
```

They complement each other.

---

## Resulting architecture

We now have:

```text
                    TenantId
                       │
                    LedgerId
                       │
                       ▼
              ┌─────────────────┐
              │ Compliance      │
              │ Configuration   │
              └────────┬────────┘
                       │
                       ▼
              ┌─────────────────┐
              │ Sharia Policy   │
              │ Registry        │
              └────────┬────────┘
                       │
                 deterministic
                   resolution
                       │
                       ▼
              ┌─────────────────┐
              │ Policy Snapshot │
              └────────┬────────┘
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
       Close       Distribution  Liquidation
          │            │            │
          └────────────┼────────────┘
                       ▼
                  Audit Evidence
```

The next thing I'd tackle is **the policy execution adapters themselves**: mapping `implementationKey` to the existing `LossAllocationPolicy`, `SyirkahDistributionAccountingPolicy`, `SyirkahSettlementCompletionPolicy`, and `SyirkahLiquidationResultPolicy` without introducing duplicate policy interfaces. That is where we make the new registry actually control the Syirkah behavior already implemented.

////////////

Next is the **policy execution adapter layer**: connect the new versioned policy registry to the Syirkah policies we already designed, without duplicating the business rules.

The principle is:

```text
Policy Registry
      ↓
implementationKey
      ↓
Trusted Adapter / Factory
      ↓
Existing Policy Interface
      ↓
Existing Syirkah Engine
```

### 1. Do not create duplicate policy interfaces

Keep the existing interfaces:

```java
LossAllocationPolicy
SyirkahDistributionAccountingPolicy
SyirkahSettlementCompletionPolicy
SyirkahLiquidationResultPolicy
```

The registry should select **which implementation** is used.

For example:

```text
SYIRKAH-MUDHARABAH-LOSS-V1
        ↓
MudharabahLossAllocationPolicy

SYIRKAH-CAPITAL-LOSS-V1
        ↓
CapitalProportionLossAllocationPolicy
```

---

### 2. Typed implementation registry

Instead of returning `Object`, make the registry strongly typed:

```java
public interface SyirkahPolicyImplementationRegistry {

    LossAllocationPolicy resolveLossAllocation(
            String implementationKey);

    SyirkahDistributionAccountingPolicy resolveDistributionAccounting(
            String implementationKey);

    SyirkahSettlementCompletionPolicy resolveSettlementCompletion(
            String implementationKey);

    SyirkahLiquidationResultPolicy resolveLiquidationResult(
            String implementationKey);
}
```

Unknown implementations must fail:

```java
throw new IllegalStateException(
    "Unsupported loss allocation policy implementation: "
        + implementationKey);
```

No silent fallback.

---

### 3. Implementation keys are stable identifiers

For example:

```java
public final class SyirkahPolicyImplementationKeys {

    public static final String MUDHARABAH_LOSS_V1 =
            "MUDHARABAH_LOSS_V1";

    public static final String CAPITAL_PROPORTION_LOSS_V1 =
            "CAPITAL_PROPORTION_LOSS_V1";

    private SyirkahPolicyImplementationKeys() {
    }
}
```

Do not use Java class names as database configuration:

```text
com.foo.accounting.MudharabahLossAllocationPolicy
```

because refactoring a package would then become a data migration.

---

### 4. Registry implementation

Conceptually:

```java
@ApplicationScoped
public class DefaultSyirkahPolicyImplementationRegistry
        implements SyirkahPolicyImplementationRegistry {

    private final Map<String, LossAllocationPolicy> lossPolicies;
    private final Map<String, SyirkahDistributionAccountingPolicy>
            distributionPolicies;

    // ...

    @Override
    public LossAllocationPolicy resolveLossAllocation(
            String implementationKey) {

        LossAllocationPolicy policy =
                lossPolicies.get(implementationKey);

        if (policy == null) {
            throw new IllegalStateException(
                    "Unknown loss allocation policy: "
                            + implementationKey);
        }

        return policy;
    }
}
```

In the actual CDI/Quarkus implementation, these can be injected as qualified beans rather than manually instantiated.

---

## 5. The important part: policy configuration

The versioned database record can now distinguish:

```text
Policy
────────────────────────────
policyId:
    SYIRKAH-MUDHARABAH-LOSS

version:
    3

policyType:
    LOSS_ALLOCATION

implementationKey:
    MUDHARABAH_LOSS_V2
```

The implementation is trusted application code.

The version can contain configuration parameters where necessary.

For example:

```json
{
  "roundingScale": 2,
  "roundingMode": "HALF_UP"
}
```

But only parameters that the implementation explicitly understands should be accepted.

---

# 6. Don't let arbitrary configuration alter legal semantics

This is an important boundary.

We should **not** allow:

```json
{
  "lossRule": "managerPaysEverything"
}
```

to dynamically change the meaning of the policy.

Instead:

```text
implementationKey
    ↓
trusted implementation
    ↓
validated configuration
```

For example:

```text
MUDHARABAH_LOSS_V2
    ├── normal business loss
    ├── negligence
    ├── breach
    └── misappropriation
```

The implementation already knows how those cases work.

Configuration can adjust safe operational parameters, but should not turn the database into executable business logic.

---

# 7. Adapt the existing `LossAllocationPolicyResolver`

We already have:

```java
public interface LossAllocationPolicyResolver {

    LossAllocationPolicy resolve(
            SyirkahContract.Version contractVersion,
            LossAllocationContext context);
}
```

We should **not delete it**.

Instead, introduce a policy-aware implementation:

```java
public interface VersionedLossAllocationPolicyResolver {

    LossAllocationPolicy resolve(
            SyirkahPolicyResolution policyResolution);
}
```

Then:

```text
ShariaPolicyResolver
        ↓
ShariaPolicyVersion
        ↓
implementationKey
        ↓
PolicyImplementationRegistry
        ↓
LossAllocationPolicy
```

The existing `SyirkahResultCalculator` continues to work with `LossAllocationPolicy`.

This is exactly what we want: **the domain engine does not need to know where the policy came from.**

---

# 8. Result calculator becomes policy-source agnostic

Existing:

```text
SyirkahResultCalculator
        ↓
LossAllocationPolicyResolver
```

becomes conceptually:

```text
SyirkahResultCalculator
        ↓
Resolved policy
        ↓
LossAllocationPolicy
```

The calculator should not know whether that policy came from:

- default configuration
- tenant configuration
- contract version
- Sharia policy registry
- test fixture

That keeps the domain engine clean.

---

# 9. Same pattern for distribution accounting

Existing:

```java
public interface SyirkahDistributionAccountingPolicy {
    JournalMapping map(
            List<PartnerDistributionObligation> obligations);
}
```

Registry:

```text
SYIRKAH-DISTRIBUTION-MAPPING-V1
        ↓
DefaultSyirkahDistributionAccountingPolicy
```

Then the posting service does:

```text
Distribution obligations
        ↓
Resolved accounting policy
        ↓
JournalMapping
        ↓
PostJournalEntryCommand
        ↓
GeneralLedgerService
```

The distribution service should never hardcode:

```text
Debit X
Credit Y
```

because that is precisely what the accounting policy abstraction was designed to prevent.

---

# 10. Settlement completion

Same mechanism:

```text
SETTLEMENT-ALL-REQUIRED-V1
        ↓
AllSettlementsRequiredPolicy
```

or:

```text
SETTLEMENT-OUTSTANDING-ALLOWED-V1
        ↓
OutstandingSettlementsAllowedPolicy
```

Then termination remains policy-driven:

```text
Liquidation
   ↓
Settlement reconciliation
   ↓
Resolved completion policy
   ↓
CompletionDecision
```

This is important because different organizations may legitimately configure whether outstanding obligations prevent final termination.

---

# 11. Liquidation result policy

Likewise:

```text
MUDHARABAH-LIQUIDATION-V1
        ↓
Mudharabah-specific result policy

MUSYARAKAH-LIQUIDATION-V1
        ↓
Musyarakah-specific result policy
```

This preserves the earlier invariant:

> Never universally calculate final partner return as `netRealization - originalCapital`.

The policy implementation determines the correct interpretation.

---

# 12. Policy set resolution

Now the complete flow becomes:

```java
public Uni<SyirkahPolicySetResolution> resolve(
        SyirkahPolicyContext context) {

    return Uni.combine().all()
            .unis(
                resolver.resolve(
                    context,
                    ShariaPolicyType.PROFIT_SHARING),

                resolver.resolve(
                    context,
                    ShariaPolicyType.LOSS_ALLOCATION),

                resolver.resolve(
                    context,
                    ShariaPolicyType.RESULT_DISPOSITION),

                resolver.resolve(
                    context,
                    ShariaPolicyType.DISTRIBUTION_ACCOUNTING),

                resolver.resolve(
                    context,
                    ShariaPolicyType.SETTLEMENT_COMPLETION),

                resolver.resolve(
                    context,
                    ShariaPolicyType.LIQUIDATION_RESULT)
            )
            .asTuple()
            .map(this::buildPolicySet);
}
```

However, there's a performance/consistency refinement I'd make in the real implementation:

**retrieve the candidate policies in one repository query**, then resolve all policy types from that consistent set.

That avoids six independent database reads observing different policy states.

---

# 13. One policy resolution transaction

For close calculation:

```text
DATABASE TRANSACTION
────────────────────────────────

Load effective policy candidates
        ↓
Resolve all required policies
        ↓
Validate implementations
        ↓
Create policy snapshot
        ↓
Calculate Syirkah result
        ↓
Persist calculation
        ↓
Persist policy snapshot

COMMIT
```

If any policy is missing:

```text
ROLLBACK
```

No partial close calculation.

---

# 14. Policy snapshot and executable policy are different

This distinction is worth preserving:

```text
SyirkahPolicySnapshot
    = historical record

ResolvedPolicySet
    = runtime execution objects
```

So:

```text
Snapshot
 ├── Policy A v3
 ├── Policy B v2
 └── Policy C v5

Runtime
 ├── MudharabahLossAllocationPolicy
 ├── DistributionAccountingPolicy
 └── SettlementCompletionPolicy
```

The runtime objects can disappear/restart/redeploy.

The snapshot remains the historical record.

---

# 15. This also solves versioned policy migration

Suppose:

```text
2026-01 → MUDHARABAH_LOSS_V1
2026-07 → MUDHARABAH_LOSS_V2
```

Then:

```text
March Close
    ↓
Policy Snapshot
    ↓
MUDHARABAH_LOSS_V1

September Close
    ↓
Policy Snapshot
    ↓
MUDHARABAH_LOSS_V2
```

Both calculations remain reproducible.

---

# 16. Add compatibility validation

Before a policy becomes effective, validate:

```text
Policy type
      ↓
Implementation key
      ↓
Implementation exists?
      ↓
Implementation supports policy type?
      ↓
Configuration valid?
      ↓
Effective period conflict?
      ↓
Approved?
      ↓
EFFECTIVE
```

A dedicated validator:

```java
public interface ShariaPolicyVersionValidator {

    List<String> validate(
            ShariaPolicyVersion version);
}
```

This should return findings rather than immediately throwing, because policy administration benefits from showing **all configuration errors at once**.

---

# 17. Example validation

For:

```text
policyType = LOSS_ALLOCATION
implementationKey = MUDHARABAH_LOSS_V2
```

the validator checks:

```text
✓ implementation exists
✓ implementation supports LOSS_ALLOCATION
✓ configuration schema valid
✓ source reference supplied
✓ effective dates valid
✓ no overlap
✓ approved
```

If:

```text
implementationKey = MUSYARAKAH_DISTRIBUTION_V1
```

is registered as `DISTRIBUTION_ACCOUNTING`, then:

```text
❌ implementation does not support LOSS_ALLOCATION
```

and it cannot become effective.

---

# 18. Testing strategy

This layer deserves strong tests because a policy-resolution bug can change financial results.

At minimum:

```text
PolicyResolutionTest
 ├── resolves contract-version policy
 ├── falls back to contract policy
 ├── falls back to ledger policy
 ├── falls back to tenant policy
 ├── rejects duplicate same-scope policies
 ├── rejects missing policy
 ├── ignores future policy
 ├── ignores retired policy
 └── rejects unsupported implementation

PolicySnapshotTest
 ├── snapshot is immutable
 ├── same calculation returns same snapshot
 ├── policy changes don't alter snapshot
 └── snapshot hash is deterministic

PolicyLifecycleTest
 ├── draft → approved
 ├── approved → effective
 ├── effective → retired
 ├── invalid transitions rejected
 └── maker/checker enforced
```

And most importantly:

```text
ClosePolicyReproducibilityTest
```

which verifies:

```text
calculate(close)
   ↓
policy snapshot V1

change current policy to V2

reconstruct(close)
   ↓
still uses V1
```

---

## Where we are now

The architecture has reached this point:

```text
                    ┌─────────────────────┐
                    │ Sharia Policy       │
                    │ Definitions/Versions│
                    └──────────┬──────────┘
                               │
                         governance
                               │
                               ▼
                    ┌─────────────────────┐
                    │ Policy Resolver     │
                    └──────────┬──────────┘
                               │
                         implementationKey
                               │
                               ▼
                    ┌─────────────────────┐
                    │ Implementation      │
                    │ Registry            │
                    └──────────┬──────────┘
                               │
          ┌────────────────────┼────────────────────┐
          ▼                    ▼                    ▼
   LossAllocation       Distribution         Liquidation
      Policy              Accounting            Result
          │                    │                    │
          └────────────────────┼────────────────────┘
                               ▼
                    ┌─────────────────────┐
                    │ Syirkah Engine      │
                    └──────────┬──────────┘
                               │
                               ▼
                    Immutable Policy Snapshot
                               │
                               ▼
                         Close / Audit
```

The **next major layer** should now be the **policy administration application service**: create policy version → validate → submit → approve → activate → retire, with maker/checker, audit events, repository transactions, and effective-date conflict protection. That will complete the operational lifecycle of the policy registry before we return to the deeper Syirkah accounting flows.


//////////////

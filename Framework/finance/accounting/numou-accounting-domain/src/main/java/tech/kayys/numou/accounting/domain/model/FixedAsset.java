package tech.kayys.syirkah.accounting.domain.model;

import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.identifier.FixedAssetId;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

public final class FixedAsset extends AbstractAggregateRoot<FixedAssetId> {
    public enum DepreciationMethod implements ValueObject {
        STRAIGHT_LINE,
        DECLINING_BALANCE
    }

    private final FixedAssetId id;
    private final String assetNumber;
    private final String name;
    private final AccountId assetAccountId;
    private final AccountId accumulatedDepreciationAccountId;
    private final AccountId depreciationExpenseAccountId;
    private final Money acquisitionCost;
    private final Money salvageValue;
    private final int usefulLifeMonths;
    private final DepreciationMethod depreciationMethod;
    private final LocalDate acquisitionDate;
    private Money currentBookValue;
    private int depreciatedMonths;

    public FixedAsset(
            FixedAssetId id,
            String assetNumber,
            String name,
            AccountId assetAccountId,
            AccountId accumulatedDepreciationAccountId,
            AccountId depreciationExpenseAccountId,
            Money acquisitionCost,
            Money salvageValue,
            int usefulLifeMonths,
            DepreciationMethod depreciationMethod,
            LocalDate acquisitionDate
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.assetNumber = Objects.requireNonNull(assetNumber);
        this.name = Objects.requireNonNull(name);
        this.assetAccountId = Objects.requireNonNull(assetAccountId);
        this.accumulatedDepreciationAccountId = Objects.requireNonNull(accumulatedDepreciationAccountId);
        this.depreciationExpenseAccountId = Objects.requireNonNull(depreciationExpenseAccountId);
        this.acquisitionCost = Objects.requireNonNull(acquisitionCost);
        this.salvageValue = Objects.requireNonNull(salvageValue);
        this.usefulLifeMonths = usefulLifeMonths;
        this.depreciationMethod = Objects.requireNonNull(depreciationMethod);
        this.acquisitionDate = Objects.requireNonNull(acquisitionDate);
        this.currentBookValue = acquisitionCost;
        this.depreciatedMonths = 0;
    }

    @Override
    public FixedAssetId id() { return id; }
    public FixedAssetId getId() { return id; }
    public String getAssetNumber() { return assetNumber; }
    public String getName() { return name; }
    public AccountId getAssetAccountId() { return assetAccountId; }
    public AccountId getAccumulatedDepreciationAccountId() { return accumulatedDepreciationAccountId; }
    public AccountId getDepreciationExpenseAccountId() { return depreciationExpenseAccountId; }
    public Money getAcquisitionCost() { return acquisitionCost; }
    public Money getSalvageValue() { return salvageValue; }
    public int getUsefulLifeMonths() { return usefulLifeMonths; }
    public DepreciationMethod getDepreciationMethod() { return depreciationMethod; }
    public LocalDate getAcquisitionDate() { return acquisitionDate; }
    public Money getCurrentBookValue() { return currentBookValue; }
    public int getDepreciatedMonths() { return depreciatedMonths; }

    /**
     * Calculates monthly depreciation amount according to configured method.
     */
    public Money calculateMonthlyDepreciation() {
        if (depreciatedMonths >= usefulLifeMonths) {
            return Money.zero(acquisitionCost.currency());
        }

        Money depreciableBase = acquisitionCost.subtract(salvageValue);
        if (depreciableBase.isZero() || depreciableBase.isNegative()) {
            return Money.zero(acquisitionCost.currency());
        }

        if (depreciationMethod == DepreciationMethod.STRAIGHT_LINE) {
            BigDecimal monthly = depreciableBase.amount().divide(BigDecimal.valueOf(usefulLifeMonths), 2, RoundingMode.HALF_UP);
            return Money.of(monthly, acquisitionCost.currency());
        } else {
            // Declining balance: 2x straight-line rate applied to current book value
            BigDecimal rate = BigDecimal.valueOf(2.0 / usefulLifeMonths);
            BigDecimal monthly = currentBookValue.amount().multiply(rate).setScale(2, RoundingMode.HALF_UP);
            return Money.of(monthly, acquisitionCost.currency());
        }
    }

    public void applyMonthlyDepreciation(Money depreciationAmount) {
        this.currentBookValue = this.currentBookValue.subtract(depreciationAmount);
        this.depreciatedMonths++;
    }
}

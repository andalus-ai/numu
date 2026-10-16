package tech.kayys.syirkah.asset.domain.fixedasset;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Schedule of depreciation entries for a single (asset, book) pair.
 */
public final class DepreciationSchedule {

    private final BookId bookId;
    private final DepreciationMethod method;
    private final int usefulLifeMonths;
    private final BigDecimal residualValue;
    private final BigDecimal initialCost;

    private final List<DepreciationEntry> entries = new ArrayList<>();
    private BigDecimal accumulated = BigDecimal.ZERO;

    public DepreciationSchedule(BookId bookId,
                                DepreciationMethod method,
                                int usefulLifeMonths,
                                BigDecimal residualValue,
                                BigDecimal initialCost) {
        this.bookId = Objects.requireNonNull(bookId, "bookId must not be null");
        this.method = Objects.requireNonNull(method, "method must not be null");
        this.residualValue = Objects.requireNonNull(residualValue, "residualValue must not be null");
        this.initialCost = Objects.requireNonNull(initialCost, "initialCost must not be null");
        if (usefulLifeMonths < 0) throw new IllegalArgumentException("usefulLifeMonths must be >= 0");
        if (residualValue.signum() < 0) throw new IllegalArgumentException("residualValue must be >= 0");
        if (initialCost.signum() < 0) throw new IllegalArgumentException("initialCost must be >= 0");
        this.usefulLifeMonths = usefulLifeMonths;
    }

    public BookId bookId() { return bookId; }
    public DepreciationMethod method() { return method; }
    public int usefulLifeMonths() { return usefulLifeMonths; }
    public BigDecimal residualValue() { return residualValue; }
    public BigDecimal initialCost() { return initialCost; }
    public BigDecimal accumulated() { return accumulated; }
    public BigDecimal carryingValue() { return initialCost.subtract(accumulated); }
    public List<DepreciationEntry> entries() { return List.copyOf(entries); }

    public boolean isFullyDepreciated() {
        return carryingValue().compareTo(residualValue) <= 0;
    }

    public DepreciationEntry append(BigDecimal amount, int year, int month) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() < 0) throw new IllegalArgumentException("amount must be >= 0");

        BigDecimal maxDepreciable = carryingValue().subtract(residualValue).max(BigDecimal.ZERO);
        BigDecimal actualAmount = amount.min(maxDepreciable);

        this.accumulated = this.accumulated.add(actualAmount);
        BigDecimal newCarryingValue = carryingValue();

        DepreciationEntry entry = new DepreciationEntry(
                DepreciationEntryId.generate(),
                year,
                month,
                actualAmount,
                this.accumulated,
                newCarryingValue,
                Instant.now()
        );
        this.entries.add(entry);
        return entry;
    }
}

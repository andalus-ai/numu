package tech.kayys.syirkah.accounting.domain.cost;

import java.math.BigDecimal;
import java.util.Objects;

/** Overhead cost pool aggregating shared indirect costs prior to step-down allocation. */
public final class CostPool {

    private final CostPoolId id;
    private final String code;
    private final String name;
    private final AllocationBasis defaultBasis;
    private BigDecimal accumulatedAmount;

    public CostPool(CostPoolId id, String code, String name, AllocationBasis basis) {
        this.id = Objects.requireNonNull(id);
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.defaultBasis = Objects.requireNonNull(basis);
        this.accumulatedAmount = BigDecimal.ZERO;
    }

    public void accumulate(BigDecimal amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("Cost accumulation must be non-negative");
        this.accumulatedAmount = this.accumulatedAmount.add(amount);
    }

    public void clear() {
        this.accumulatedAmount = BigDecimal.ZERO;
    }

    public CostPoolId id() { return id; }
    public String code() { return code; }
    public String name() { return name; }
    public AllocationBasis defaultBasis() { return defaultBasis; }
    public BigDecimal accumulatedAmount() { return accumulatedAmount; }
}

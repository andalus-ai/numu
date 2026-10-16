package tech.kayys.syirkah.asset.domain.fixedasset;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An identifiable component of a complex asset (e.g. Aircraft Engine, Building HVAC).
 */
public final class AssetComponent {

    private final ComponentId id;
    private final String name;
    private final BigDecimal cost;
    private final int usefulLifeMonths;
    private final BigDecimal salvageValue;
    private BigDecimal accumulatedDepreciation;

    public AssetComponent(ComponentId id, String name, BigDecimal cost,
                          int usefulLifeMonths, BigDecimal salvageValue) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.cost = Objects.requireNonNull(cost, "cost must not be null");
        this.salvageValue = salvageValue != null ? salvageValue : BigDecimal.ZERO;
        if (cost.signum() <= 0) throw new IllegalArgumentException("cost must be > 0");
        if (usefulLifeMonths <= 0) throw new IllegalArgumentException("usefulLifeMonths must be > 0");
        this.usefulLifeMonths = usefulLifeMonths;
        this.accumulatedDepreciation = BigDecimal.ZERO;
    }

    public ComponentId id() { return id; }
    public String name() { return name; }
    public BigDecimal cost() { return cost; }
    public int usefulLifeMonths() { return usefulLifeMonths; }
    public BigDecimal salvageValue() { return salvageValue; }
    public BigDecimal accumulatedDepreciation() { return accumulatedDepreciation; }
    public BigDecimal bookValue() { return cost.subtract(accumulatedDepreciation); }

    public void applyDepreciation(BigDecimal amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be > 0");
        BigDecimal maxDepreciable = bookValue().subtract(salvageValue).max(BigDecimal.ZERO);
        this.accumulatedDepreciation = this.accumulatedDepreciation.add(amount.min(maxDepreciable));
    }
}

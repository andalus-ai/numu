package tech.kayys.syirkah.construction.domain.variation;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public final class ChangeOrderItem extends AbstractAggregateRoot<ChangeOrderItemId> {
    private final UUID changeOrderId;
    private final UUID boqItemId;
    private final VariationType variationType;
    private final BigDecimal quantityDelta;
    private final BigDecimal unitRate;
    private final String justification;

    private ChangeOrderItem(
            ChangeOrderItemId id,
            UUID changeOrderId,
            UUID boqItemId,
            VariationType variationType,
            BigDecimal quantityDelta,
            BigDecimal unitRate,
            String justification
    ) {
        super(id);
        this.changeOrderId = Objects.requireNonNull(changeOrderId, "Change order id cannot be null");
        this.boqItemId = boqItemId;
        this.variationType = Objects.requireNonNull(variationType, "Variation type cannot be null");
        this.quantityDelta = Objects.requireNonNull(quantityDelta, "Quantity delta cannot be null");
        this.unitRate = Objects.requireNonNull(unitRate, "Unit rate cannot be null");
        this.justification = justification;
    }

    public static ChangeOrderItem create(
            UUID changeOrderId,
            UUID boqItemId,
            VariationType variationType,
            BigDecimal quantityDelta,
            BigDecimal unitRate,
            String justification
    ) {
        return new ChangeOrderItem(ChangeOrderItemId.generate(), changeOrderId, boqItemId, variationType, quantityDelta, unitRate, justification);
    }

    public BigDecimal costImpact() {
        return quantityDelta.multiply(unitRate);
    }

    public UUID changeOrderId() { return changeOrderId; }
    public UUID boqItemId() { return boqItemId; }
    public VariationType variationType() { return variationType; }
    public BigDecimal quantityDelta() { return quantityDelta; }
    public BigDecimal unitRate() { return unitRate; }
    public String justification() { return justification; }
}

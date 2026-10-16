package tech.kayys.syirkah.construction.domain.boq;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public final class BoqItem extends AbstractAggregateRoot<BoqItemId> {
    private final UUID boqId;
    private final UUID wbsNodeId;
    private final String itemCode;
    private String description;
    private BoqItemType itemType;
    private BoqQuantity quantity;
    private BoqRate rate;

    private BoqItem(
            BoqItemId id,
            UUID boqId,
            UUID wbsNodeId,
            String itemCode,
            String description,
            BoqItemType itemType,
            BoqQuantity quantity,
            BoqRate rate
    ) {
        super(id);
        this.boqId = Objects.requireNonNull(boqId, "BOQ id cannot be null");
        this.wbsNodeId = Objects.requireNonNull(wbsNodeId, "WBS node id cannot be null");
        this.itemCode = Objects.requireNonNull(itemCode, "BOQ item code cannot be blank");
        this.description = Objects.requireNonNull(description, "BOQ item description cannot be blank");
        this.itemType = Objects.requireNonNull(itemType, "Item type cannot be null");
        this.quantity = Objects.requireNonNull(quantity, "Quantity cannot be null");
        this.rate = Objects.requireNonNull(rate, "Rate cannot be null");
    }

    public static BoqItem create(
            UUID boqId,
            UUID wbsNodeId,
            String itemCode,
            String description,
            BoqItemType itemType,
            BoqQuantity quantity,
            BoqRate rate
    ) {
        return new BoqItem(BoqItemId.generate(), boqId, wbsNodeId, itemCode, description, itemType, quantity, rate);
    }

    public BigDecimal amount() {
        return quantity.value().multiply(rate.amount());
    }

    public UUID boqId() { return boqId; }
    public UUID wbsNodeId() { return wbsNodeId; }
    public String itemCode() { return itemCode; }
    public String description() { return description; }
    public BoqItemType itemType() { return itemType; }
    public BoqQuantity quantity() { return quantity; }
    public BoqRate rate() { return rate; }
}

package tech.kayys.syirkah.construction.domain.cost;

import tech.kayys.syirkah.construction.domain.cost.event.ConstructionCostRecorded;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ConstructionCostEntry extends AbstractAggregateRoot<ConstructionCostEntryId> {
    private final UUID projectId;
    private final UUID siteId;
    private final UUID wbsNodeId;
    private final ConstructionCostType costType;
    private final ConstructionCostSource costSource;
    private final BigDecimal amount;

    private ConstructionCostEntry(
            ConstructionCostEntryId id,
            UUID projectId,
            UUID siteId,
            UUID wbsNodeId,
            ConstructionCostType costType,
            ConstructionCostSource costSource,
            BigDecimal amount
    ) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId);
        this.siteId = siteId;
        this.wbsNodeId = wbsNodeId;
        this.costType = Objects.requireNonNull(costType);
        this.costSource = Objects.requireNonNull(costSource);
        this.amount = Objects.requireNonNull(amount);
    }

    public static ConstructionCostEntry record(
            UUID projectId,
            UUID siteId,
            UUID wbsNodeId,
            ConstructionCostType costType,
            ConstructionCostSource costSource,
            BigDecimal amount
    ) {
        var entry = new ConstructionCostEntry(ConstructionCostEntryId.generate(), projectId, siteId, wbsNodeId, costType, costSource, amount);
        entry.raise(new ConstructionCostRecorded(UUID.randomUUID(), Instant.now(), entry.id().value(), projectId, costType, amount));
        return entry;
    }

    public UUID projectId() { return projectId; }
    public UUID siteId() { return siteId; }
    public UUID wbsNodeId() { return wbsNodeId; }
    public ConstructionCostType costType() { return costType; }
    public ConstructionCostSource costSource() { return costSource; }
    public BigDecimal amount() { return amount; }
}

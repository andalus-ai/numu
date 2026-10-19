package tech.kayys.numou.construction.domain.cost.event;

import tech.kayys.numou.construction.domain.cost.ConstructionCostType;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ConstructionCostRecorded(
        UUID eventId,
        Instant occurredAt,
        UUID entryId,
        UUID projectId,
        ConstructionCostType costType,
        BigDecimal amount
) implements DomainEvent {
    @Override public String eventType() { return "construction.cost-recorded"; }
}

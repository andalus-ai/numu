package tech.kayys.syirkah.construction.domain.cost.event;

import tech.kayys.syirkah.construction.domain.cost.ConstructionCostType;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
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

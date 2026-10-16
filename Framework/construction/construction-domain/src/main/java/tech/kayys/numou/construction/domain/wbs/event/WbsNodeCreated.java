package tech.kayys.numou.construction.domain.wbs.event;

import tech.kayys.numou.construction.domain.wbs.WbsNodeType;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record WbsNodeCreated(
        UUID eventId,
        Instant occurredAt,
        UUID nodeId,
        UUID projectId,
        String code,
        WbsNodeType type
) implements DomainEvent {
    @Override public String eventType() { return "construction.wbs-node-created"; }
}

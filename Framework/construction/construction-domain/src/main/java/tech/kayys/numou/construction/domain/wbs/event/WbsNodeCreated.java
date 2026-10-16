package tech.kayys.syirkah.construction.domain.wbs.event;

import tech.kayys.syirkah.construction.domain.wbs.WbsNodeType;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
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

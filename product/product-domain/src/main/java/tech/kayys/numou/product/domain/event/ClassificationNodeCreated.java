package tech.kayys.numou.product.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.product.domain.classification.ClassificationNodeId;
import tech.kayys.numou.product.domain.classification.ClassificationSchemeId;

import java.time.Instant;
import java.util.UUID;

public record ClassificationNodeCreated(
        UUID eventId,
        Instant occurredAt,
        ClassificationNodeId nodeId,
        ClassificationSchemeId schemeId,
        ClassificationNodeId parentNodeId,
        String code,
        String name
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.classification-node-created";
    }
}

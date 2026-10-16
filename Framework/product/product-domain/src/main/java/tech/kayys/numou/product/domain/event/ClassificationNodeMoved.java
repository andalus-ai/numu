package tech.kayys.syirkah.product.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;

import java.time.Instant;
import java.util.UUID;

public record ClassificationNodeMoved(
        UUID eventId,
        Instant occurredAt,
        ClassificationNodeId nodeId,
        ClassificationNodeId oldParentNodeId,
        ClassificationNodeId newParentNodeId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.classification-node-moved";
    }
}

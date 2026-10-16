package tech.kayys.syirkah.product.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;

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

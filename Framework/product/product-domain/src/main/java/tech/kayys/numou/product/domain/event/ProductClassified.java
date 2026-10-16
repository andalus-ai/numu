package tech.kayys.numou.product.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.product.domain.classification.ClassificationNodeId;
import tech.kayys.numou.product.domain.classification.ClassificationSchemeId;
import tech.kayys.numou.product.domain.product.ProductId;

import java.time.Instant;
import java.util.UUID;

public record ProductClassified(
        UUID eventId,
        Instant occurredAt,
        ProductId productId,
        ClassificationSchemeId schemeId,
        ClassificationNodeId nodeId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.product-classified";
    }
}

package tech.kayys.syirkah.product.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;
import tech.kayys.syirkah.product.domain.product.ProductId;

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

package tech.kayys.numou.product.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.product.domain.classification.ClassificationSchemeId;

import java.time.Instant;
import java.util.UUID;

public record ClassificationSchemeArchived(
        UUID eventId,
        Instant occurredAt,
        ClassificationSchemeId schemeId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.classification-scheme-archived";
    }
}

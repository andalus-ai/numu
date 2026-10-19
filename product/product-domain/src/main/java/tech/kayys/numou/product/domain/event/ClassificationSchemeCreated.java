package tech.kayys.numou.product.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.product.domain.classification.ClassificationSchemeId;
import tech.kayys.numou.product.domain.classification.ClassificationSchemeType;

import java.time.Instant;
import java.util.UUID;

public record ClassificationSchemeCreated(
        UUID eventId,
        Instant occurredAt,
        ClassificationSchemeId schemeId,
        String code,
        String name,
        ClassificationSchemeType type
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.classification-scheme-created";
    }
}

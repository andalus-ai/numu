package tech.kayys.syirkah.product.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;

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

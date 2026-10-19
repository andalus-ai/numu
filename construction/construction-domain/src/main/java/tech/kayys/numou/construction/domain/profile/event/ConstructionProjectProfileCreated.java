package tech.kayys.numou.construction.domain.profile.event;

import tech.kayys.numou.construction.domain.profile.ConstructionContractType;
import tech.kayys.numou.construction.domain.profile.ConstructionType;
import tech.kayys.numou.construction.domain.profile.DeliveryMethod;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record ConstructionProjectProfileCreated(
        UUID eventId,
        Instant occurredAt,
        UUID projectId,
        UUID profileId,
        ConstructionType constructionType,
        DeliveryMethod deliveryMethod,
        ConstructionContractType contractType
) implements DomainEvent {
    @Override public String eventType() { return "construction.project-profile-created"; }
}

package tech.kayys.syirkah.construction.domain.profile.event;

import tech.kayys.syirkah.construction.domain.profile.ConstructionContractType;
import tech.kayys.syirkah.construction.domain.profile.ConstructionType;
import tech.kayys.syirkah.construction.domain.profile.DeliveryMethod;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
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

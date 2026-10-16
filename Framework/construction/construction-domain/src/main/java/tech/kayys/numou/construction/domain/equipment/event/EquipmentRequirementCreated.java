package tech.kayys.syirkah.construction.domain.equipment.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record EquipmentRequirementCreated(
        UUID eventId,
        Instant occurredAt,
        UUID requirementId,
        UUID projectId,
        String equipmentType
) implements DomainEvent {
    @Override public String eventType() { return "construction.equipment-requirement-created"; }
}

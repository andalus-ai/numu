package tech.kayys.syirkah.construction.domain.equipment;

import tech.kayys.syirkah.construction.domain.equipment.event.EquipmentRequirementCreated;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class EquipmentRequirement extends AbstractAggregateRoot<EquipmentRequirementId> {
    private final UUID projectId;
    private final UUID siteId;
    private final String equipmentType;
    private final int quantityRequired;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private EquipmentRequirementStatus status;
    private UUID allocatedAssetRefId;

    private EquipmentRequirement(
            EquipmentRequirementId id,
            UUID projectId,
            UUID siteId,
            String equipmentType,
            int quantityRequired,
            LocalDate startDate,
            LocalDate endDate
    ) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId);
        this.siteId = Objects.requireNonNull(siteId);
        this.equipmentType = Objects.requireNonNull(equipmentType);
        this.quantityRequired = quantityRequired;
        this.startDate = Objects.requireNonNull(startDate);
        this.endDate = Objects.requireNonNull(endDate);
        this.status = EquipmentRequirementStatus.DRAFT;
    }

    public static EquipmentRequirement create(
            UUID projectId,
            UUID siteId,
            String equipmentType,
            int quantityRequired,
            LocalDate startDate,
            LocalDate endDate
    ) {
        var req = new EquipmentRequirement(EquipmentRequirementId.generate(), projectId, siteId, equipmentType, quantityRequired, startDate, endDate);
        req.raise(new EquipmentRequirementCreated(UUID.randomUUID(), Instant.now(), req.id().value(), projectId, equipmentType));
        return req;
    }

    public void allocate(UUID assetRefId) {
        this.allocatedAssetRefId = Objects.requireNonNull(assetRefId);
        this.status = EquipmentRequirementStatus.ALLOCATED;
    }

    public UUID projectId() { return projectId; }
    public UUID siteId() { return siteId; }
    public String equipmentType() { return equipmentType; }
    public int quantityRequired() { return quantityRequired; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
    public EquipmentRequirementStatus status() { return status; }
    public UUID allocatedAssetRefId() { return allocatedAssetRefId; }
}

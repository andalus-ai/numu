package tech.kayys.numou.construction.application.equipment.command;

import tech.kayys.numou.foundation.application.command.Command;
import java.time.LocalDate;
import java.util.UUID;

public record CreateEquipmentRequirementCommand(
        UUID projectId,
        UUID siteId,
        String equipmentType,
        int quantityRequired,
        LocalDate startDate,
        LocalDate endDate
) implements Command {}

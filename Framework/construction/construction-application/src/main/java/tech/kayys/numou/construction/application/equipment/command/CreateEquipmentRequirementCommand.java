package tech.kayys.syirkah.construction.application.equipment.command;

import tech.kayys.syirkah.foundation.application.command.Command;
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

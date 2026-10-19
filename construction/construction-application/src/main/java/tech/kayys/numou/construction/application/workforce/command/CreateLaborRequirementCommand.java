package tech.kayys.numou.construction.application.workforce.command;

import tech.kayys.numou.foundation.application.command.Command;
import java.time.LocalDate;
import java.util.UUID;

public record CreateLaborRequirementCommand(
        UUID projectId,
        UUID siteId,
        String trade,
        int workersRequired,
        LocalDate requiredDate
) implements Command {}

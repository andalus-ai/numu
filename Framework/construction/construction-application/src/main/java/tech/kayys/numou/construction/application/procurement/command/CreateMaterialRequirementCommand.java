package tech.kayys.numou.construction.application.procurement.command;

import tech.kayys.numou.foundation.application.command.Command;
import java.util.UUID;

public record CreateMaterialRequirementCommand(UUID projectId, UUID siteId) implements Command {}

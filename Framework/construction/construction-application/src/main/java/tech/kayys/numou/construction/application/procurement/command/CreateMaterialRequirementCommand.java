package tech.kayys.syirkah.construction.application.procurement.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import java.util.UUID;

public record CreateMaterialRequirementCommand(UUID projectId, UUID siteId) implements Command {}

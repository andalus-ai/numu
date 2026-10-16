package tech.kayys.syirkah.construction.application.quality.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import java.util.UUID;

public record CreateQualityPlanCommand(UUID projectId, String title) implements Command {}

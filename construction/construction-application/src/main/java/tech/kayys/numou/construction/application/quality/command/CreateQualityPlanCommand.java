package tech.kayys.numou.construction.application.quality.command;

import tech.kayys.numou.foundation.application.command.Command;
import java.util.UUID;

public record CreateQualityPlanCommand(UUID projectId, String title) implements Command {}

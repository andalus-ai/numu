package tech.kayys.numou.construction.application.hse.command;

import tech.kayys.numou.foundation.application.command.Command;
import java.util.UUID;

public record CreateSafetyProfileCommand(UUID projectId, UUID siteId, String name) implements Command {}

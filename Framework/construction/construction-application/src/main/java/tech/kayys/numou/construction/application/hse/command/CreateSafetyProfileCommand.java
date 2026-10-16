package tech.kayys.syirkah.construction.application.hse.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import java.util.UUID;

public record CreateSafetyProfileCommand(UUID projectId, UUID siteId, String name) implements Command {}

package tech.kayys.syirkah.construction.application.boq.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import java.util.UUID;

public record CreateBoqCommand(UUID projectId, String name) implements Command {}

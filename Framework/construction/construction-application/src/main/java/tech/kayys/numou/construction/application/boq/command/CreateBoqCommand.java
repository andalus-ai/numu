package tech.kayys.numou.construction.application.boq.command;

import tech.kayys.numou.foundation.application.command.Command;
import java.util.UUID;

public record CreateBoqCommand(UUID projectId, String name) implements Command {}

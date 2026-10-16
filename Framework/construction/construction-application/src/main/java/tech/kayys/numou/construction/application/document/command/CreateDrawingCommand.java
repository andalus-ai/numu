package tech.kayys.syirkah.construction.application.document.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import java.util.UUID;

public record CreateDrawingCommand(
        UUID projectId,
        String drawingNumber,
        String title,
        String discipline,
        String revision
) implements Command {}

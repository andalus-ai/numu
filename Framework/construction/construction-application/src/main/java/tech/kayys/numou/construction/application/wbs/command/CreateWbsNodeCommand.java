package tech.kayys.syirkah.construction.application.wbs.command;

import tech.kayys.syirkah.construction.domain.wbs.WbsNodeType;
import tech.kayys.syirkah.foundation.application.command.Command;
import java.util.UUID;

public record CreateWbsNodeCommand(
        UUID projectId,
        UUID parentNodeId,
        String code,
        String name,
        WbsNodeType type
) implements Command {}

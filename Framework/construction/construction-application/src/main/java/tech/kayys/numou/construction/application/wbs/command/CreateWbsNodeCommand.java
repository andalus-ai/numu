package tech.kayys.numou.construction.application.wbs.command;

import tech.kayys.numou.construction.domain.wbs.WbsNodeType;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.UUID;

public record CreateWbsNodeCommand(
        UUID projectId,
        UUID parentNodeId,
        String code,
        String name,
        WbsNodeType type
) implements Command {}

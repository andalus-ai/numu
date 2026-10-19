package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.position.PositionId;
import tech.kayys.numou.workforce.domain.talent.Criticality;

import java.util.Objects;

public record DesignateCriticalPositionCommand(
        TenantId tenantId,
        PositionId positionId,
        Criticality criticality,
        String reason
) implements Command {
    public DesignateCriticalPositionCommand {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(positionId, "positionId must not be null");
        Objects.requireNonNull(criticality, "criticality must not be null");
    }
}

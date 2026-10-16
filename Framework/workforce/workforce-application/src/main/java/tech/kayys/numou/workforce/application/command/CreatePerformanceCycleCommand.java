package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;
import java.util.Objects;

public record CreatePerformanceCycleCommand(
        TenantId tenantId,
        String code,
        String name,
        LocalDate periodStart,
        LocalDate periodEnd
) implements Command {
    public CreatePerformanceCycleCommand {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");
    }
}

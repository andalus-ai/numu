package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;
import java.util.Objects;

public record CreatePayrollPeriodCommand(
        TenantId tenantId,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate paymentDate
) implements Command {
    public CreatePayrollPeriodCommand {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
        Objects.requireNonNull(paymentDate, "paymentDate must not be null");
    }
}

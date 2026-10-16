package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.payroll.PayrollPeriodId;

import java.util.Objects;

public record CreatePayrollRunCommand(
        TenantId tenantId,
        PayrollPeriodId periodId
) implements Command {
    public CreatePayrollRunCommand {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(periodId, "periodId must not be null");
    }
}

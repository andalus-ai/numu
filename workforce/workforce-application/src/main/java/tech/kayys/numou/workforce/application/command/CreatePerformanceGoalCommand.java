package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.performance.GoalType;
import tech.kayys.numou.workforce.domain.performance.PerformanceCycleId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record CreatePerformanceGoalCommand(
        TenantId tenantId,
        PerformanceCycleId cycleId,
        WorkerId workerId,
        EmploymentId employmentId,
        String title,
        String description,
        GoalType type,
        LocalDate startDate,
        LocalDate dueDate,
        BigDecimal targetValue,
        String targetUnit
) implements Command {
    public CreatePerformanceGoalCommand {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(cycleId, "cycleId must not be null");
        Objects.requireNonNull(workerId, "workerId must not be null");
        Objects.requireNonNull(employmentId, "employmentId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }
}

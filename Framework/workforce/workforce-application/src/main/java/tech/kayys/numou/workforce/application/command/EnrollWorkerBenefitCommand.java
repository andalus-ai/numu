package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.workforce.domain.benefit.BenefitId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.LocalDate;
import java.util.Objects;

public record EnrollWorkerBenefitCommand(
        WorkerId workerId,
        EmploymentId employmentId, // optional/nullable
        BenefitId benefitId,
        LocalDate effectiveFrom
) implements Command {
    public EnrollWorkerBenefitCommand {
        Objects.requireNonNull(workerId, "workerId must not be null");
        Objects.requireNonNull(benefitId, "benefitId must not be null");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
    }
}

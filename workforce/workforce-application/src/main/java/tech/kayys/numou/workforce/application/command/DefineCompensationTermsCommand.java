package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.workforce.domain.compensation.PayFrequency;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;

import java.time.LocalDate;
import java.util.Objects;

public record DefineCompensationTermsCommand(
        EmploymentId employmentId,
        Money basePay,
        PayFrequency payFrequency,
        LocalDate effectiveFrom
) implements Command {
    public DefineCompensationTermsCommand {
        Objects.requireNonNull(employmentId, "employmentId must not be null");
        Objects.requireNonNull(basePay, "basePay must not be null");
        Objects.requireNonNull(payFrequency, "payFrequency must not be null");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
    }
}

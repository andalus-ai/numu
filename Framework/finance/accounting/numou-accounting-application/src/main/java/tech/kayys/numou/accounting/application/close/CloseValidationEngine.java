package tech.kayys.syirkah.accounting.application.close;

import tech.kayys.syirkah.accounting.domain.close.CloseCycle;
import tech.kayys.syirkah.accounting.domain.close.CloseTask;
import tech.kayys.syirkah.accounting.domain.close.CloseTaskStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates financial close preconditions and task completion.
 */
public final class CloseValidationEngine {

    public record ValidationResult(
            boolean valid,
            List<String> errors
    ) {}

    public ValidationResult validate(CloseCycle cycle) {
        List<String> errors = new ArrayList<>();

        for (CloseTask task : cycle.tasks()) {
            if (task.isMandatory() && task.status() != CloseTaskStatus.COMPLETED) {
                errors.add("Mandatory close task incomplete: " + task.name() + " (" + task.taskCode() + ")");
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }
}

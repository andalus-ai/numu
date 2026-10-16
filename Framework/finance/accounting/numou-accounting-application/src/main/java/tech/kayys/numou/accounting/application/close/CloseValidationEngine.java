package tech.kayys.numou.accounting.application.close;

import tech.kayys.numou.accounting.domain.close.CloseCycle;
import tech.kayys.numou.accounting.domain.close.CloseTask;
import tech.kayys.numou.accounting.domain.close.CloseTaskStatus;

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

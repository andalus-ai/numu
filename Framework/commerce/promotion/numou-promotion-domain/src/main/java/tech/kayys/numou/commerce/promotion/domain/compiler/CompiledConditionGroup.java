package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Compiled boolean composition of conditions (product04.md section 12).
 *
 * <p>Supports AND ({@link LogicalOperator#ALL}), OR ({@link LogicalOperator#ANY})
 * and NOT ({@link LogicalOperator#NOT}). The composed result aggregates the
 * child {@link ConditionEvaluationResult}s so the caller can explain which
 * sub-condition failed.</p>
 */
public record CompiledConditionGroup(
        LogicalOperator operator,
        List<CompiledCondition> conditions
) implements CompiledCondition {

    public CompiledConditionGroup {
        Objects.requireNonNull(operator, "operator cannot be null");
        Objects.requireNonNull(conditions, "conditions cannot be null");
        conditions = List.copyOf(conditions);
    }

    @Override
    public boolean evaluate(PromotionEvaluationContext context) {
        return switch (operator) {
            case ALL -> conditions.stream().allMatch(c -> c.evaluate(context));
            case ANY -> conditions.stream().anyMatch(c -> c.evaluate(context));
            case NOT -> {
                if (conditions.size() != 1) {
                    throw new IllegalStateException("NOT requires exactly one child");
                }
                yield !conditions.getFirst().evaluate(context);
            }
        };
    }

    @Override
    public ConditionEvaluationResult evaluateResult(PromotionEvaluationContext context) {
        List<ConditionEvaluationResult> results = conditions.stream()
                .map(c -> c.evaluateResult(context))
                .collect(Collectors.toList());

        boolean allPassed = results.stream()
                .allMatch(r -> r.status() == ConditionEvaluationStatus.PASSED);
        boolean anyPassed = results.stream()
                .anyMatch(r -> r.status() == ConditionEvaluationStatus.PASSED);
        boolean anyUnknown = results.stream()
                .anyMatch(r -> r.status() == ConditionEvaluationStatus.UNKNOWN);
        boolean anyInvalid = results.stream()
                .anyMatch(r -> r.status() == ConditionEvaluationStatus.INVALID);

        if (anyInvalid) {
            return ConditionEvaluationResult.invalid("COMPOSITION_INVALID");
        }

        boolean composed = switch (operator) {
            case ALL -> allPassed;
            case ANY -> anyPassed;
            case NOT -> !anyPassed;
        };

        if (composed) {
            return ConditionEvaluationResult.passed("COMPOSITION_MET");
        }

        // Surface the first failing child's reason code so callers can
        // explain why the composition failed.
        String reason = results.stream()
                .filter(r -> r.status() == ConditionEvaluationStatus.FAILED)
                .map(r -> r.reasonCode().orElse("CONDITION_NOT_MET"))
                .findFirst()
                .orElse("COMPOSITION_NOT_MET");

        if (anyUnknown) {
            return ConditionEvaluationResult.unknown(reason);
        }
        return ConditionEvaluationResult.failed(reason);
    }

    @Override
    public String capability() {
        return operator.name().toLowerCase();
    }
}

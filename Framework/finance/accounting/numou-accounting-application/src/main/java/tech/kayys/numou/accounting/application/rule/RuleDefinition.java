package tech.kayys.syirkah.accounting.application.rule;

import java.math.BigDecimal;
import java.util.Objects;

/** Declarative rule definition. */
public record RuleDefinition(
        RuleId id,
        RuleCode code,
        String name,
        String contextProperty,     // e.g. "amount"
        String operator,            // e.g. ">", "<=", "=="
        BigDecimal threshold,       // numerical threshold
        RuleResult.Severity severity,
        RuleResult.RuleAction action,
        RuleStatus status
) {
    public RuleDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(code);
        Objects.requireNonNull(name);
        Objects.requireNonNull(contextProperty);
        Objects.requireNonNull(operator);
        Objects.requireNonNull(severity);
        Objects.requireNonNull(action);
        Objects.requireNonNull(status);
    }

    public boolean isApplicable() {
        return status == RuleStatus.ACTIVE;
    }
}

package tech.kayys.syirkah.accounting.application.rule;

import java.util.Objects;

/** Human-readable business code for a rule, e.g. PAYMENT_LIMIT. */
public record RuleCode(String value) {
    public RuleCode {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("RuleCode must not be blank");
    }
    public static RuleCode of(String v) { return new RuleCode(v); }
    @Override public String toString() { return value; }
}

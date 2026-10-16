package tech.kayys.syirkah.accounting.application.rule;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a rule instance. */
public record RuleId(String value) {
    public RuleId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("RuleId must not be blank");
    }
    public static RuleId generate() { return new RuleId(UUID.randomUUID().toString()); }
    public static RuleId of(String v) { return new RuleId(v); }
}

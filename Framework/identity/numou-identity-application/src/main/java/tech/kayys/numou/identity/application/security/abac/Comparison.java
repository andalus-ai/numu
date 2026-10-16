package tech.kayys.syirkah.identity.application.security.abac;

import java.util.Objects;

public record Comparison(
        AttributeReference left,
        ComparisonOperator operator,
        Object right
) implements PolicyCondition {
    public Comparison {
        Objects.requireNonNull(left, "left cannot be null");
        Objects.requireNonNull(operator, "operator cannot be null");
        right = PolicyLiteral.requireSupported(right);
    }
}

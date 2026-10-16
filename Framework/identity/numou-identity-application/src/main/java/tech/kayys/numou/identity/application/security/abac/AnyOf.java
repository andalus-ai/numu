package tech.kayys.syirkah.identity.application.security.abac;

import java.util.List;

public record AnyOf(List<PolicyCondition> conditions) implements PolicyCondition {
    public AnyOf {
        conditions = List.copyOf(conditions);
        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("AnyOf requires at least one condition");
        }
    }
}

package tech.kayys.syirkah.identity.application.security.abac;

import java.util.List;

public record AllOf(List<PolicyCondition> conditions) implements PolicyCondition {
    public AllOf {
        conditions = List.copyOf(conditions);
        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("AllOf requires at least one condition");
        }
    }
}

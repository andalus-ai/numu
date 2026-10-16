package tech.kayys.syirkah.identity.application.security.abac;

import java.util.Objects;

public record Not(PolicyCondition condition) implements PolicyCondition {
    public Not {
        Objects.requireNonNull(condition, "condition cannot be null");
    }
}

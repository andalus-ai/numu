package tech.kayys.syirkah.identity.application.security.abac;

import java.util.Objects;

public record Contains(AttributeReference collection, Object value) implements PolicyCondition {
    public Contains {
        Objects.requireNonNull(collection, "collection cannot be null");
        value = PolicyLiteral.requireSupported(value);
    }
}

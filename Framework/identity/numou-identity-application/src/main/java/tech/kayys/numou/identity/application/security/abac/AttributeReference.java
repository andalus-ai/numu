package tech.kayys.syirkah.identity.application.security.abac;

import java.util.Objects;

public record AttributeReference(AttributeNamespace namespace, String name) {
    public AttributeReference {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        if (!name.matches("[A-Za-z][A-Za-z0-9_.-]*")) {
            throw new IllegalArgumentException("Invalid attribute name");
        }
    }
}

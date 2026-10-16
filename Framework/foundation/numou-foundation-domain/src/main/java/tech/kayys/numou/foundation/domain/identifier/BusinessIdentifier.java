package tech.kayys.numou.foundation.domain.identifier;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable, validated, printable enterprise business number (config03.md §P4-15 #5).
 * Distinct from technical identity (DomainId) and external IDs.
 */
public record BusinessIdentifier(
        String namespace,
        String value
) implements Serializable {

    public BusinessIdentifier {
        namespace = requireText(namespace, "namespace");
        value = requireText(value, "value");
    }

    public static BusinessIdentifier of(String namespace, String value) {
        return new BusinessIdentifier(namespace, value);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }

        return normalized;
    }

    @Override
    public String toString() {
        return value;
    }
}

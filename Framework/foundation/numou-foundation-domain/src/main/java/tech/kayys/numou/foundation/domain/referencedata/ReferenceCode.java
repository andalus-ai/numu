package tech.kayys.numou.foundation.domain.referencedata;

import java.io.Serializable;
import java.util.Objects;

/**
 * Namespace-qualified business reference code (config03.md §P4-16 #4).
 * Combines setKey (namespace) and code value.
 */
public record ReferenceCode(
        String setKey,
        String value
) implements Serializable {

    public ReferenceCode {
        setKey = requireText(setKey, "setKey");
        value = requireText(value, "value");
    }

    public static ReferenceCode of(String setKey, String value) {
        return new ReferenceCode(setKey, value);
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
        return setKey + ":" + value;
    }
}

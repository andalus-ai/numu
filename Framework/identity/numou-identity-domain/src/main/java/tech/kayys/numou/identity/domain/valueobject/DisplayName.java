package tech.kayys.syirkah.identity.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.util.Objects;

public record DisplayName(String value) implements ValueObject {

    private static final int MAX_LENGTH = 120;

    public DisplayName {
        Objects.requireNonNull(value, "Display name cannot be null");

        value = value.trim();

        if (value.isBlank()) {
            throw new IllegalArgumentException("Display name cannot be blank");
        }

        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Display name cannot exceed " + MAX_LENGTH + " characters"
            );
        }
    }

    public static DisplayName of(String value) {
        return new DisplayName(value);
    }

}

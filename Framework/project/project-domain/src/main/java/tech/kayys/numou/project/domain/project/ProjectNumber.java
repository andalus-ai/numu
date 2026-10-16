package tech.kayys.syirkah.project.domain.project;

import java.util.Objects;

/** Human-readable business identifier for a project. */
public record ProjectNumber(String value) {

    public ProjectNumber {
        Objects.requireNonNull(
                value,
                "Project number cannot be null"
        );

        value = value.trim();

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Project number cannot be blank"
            );
        }

        if (value.length() > 50) {
            throw new IllegalArgumentException(
                    "Project number cannot exceed 50 characters"
            );
        }
    }

    public static ProjectNumber of(String value) {
        return new ProjectNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
package tech.kayys.syirkah.project.domain.task;

import java.util.Objects;

public record TaskNumber(String value) {

    public TaskNumber {
        Objects.requireNonNull(
                value,
                "Task number cannot be null"
        );

        value = value.trim();

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Task number cannot be blank"
            );
        }

        if (value.length() > 50) {
            throw new IllegalArgumentException(
                    "Task number cannot exceed 50 characters"
            );
        }
    }

    public static TaskNumber of(String value) {
        return new TaskNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

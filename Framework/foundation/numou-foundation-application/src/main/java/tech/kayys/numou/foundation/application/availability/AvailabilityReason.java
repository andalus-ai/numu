package tech.kayys.numou.foundation.application.availability;

import java.util.Objects;

/**
 * Descriptive reason explaining current availability or degradation (config01.md §P4-06 #4).
 */
public record AvailabilityReason(
        String code,
        String message
) {
    public AvailabilityReason {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
    }

    public static AvailabilityReason of(String code, String message) {
        return new AvailabilityReason(code, message);
    }
}

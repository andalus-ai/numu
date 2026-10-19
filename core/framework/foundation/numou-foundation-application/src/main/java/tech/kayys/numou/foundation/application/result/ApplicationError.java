package tech.kayys.numou.foundation.application.result;

import java.util.Objects;

/**
 * A structured application-level error: a stable machine-readable
 * code plus a human-readable message.
 *
 * Deliberately not tied to any transport (HTTP status, gRPC status
 * code, ...) - that mapping belongs to the inbound adapter.
 */
public record ApplicationError(String code, String message) {

    public ApplicationError {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(message, "message cannot be null");

        if (code.isBlank()) {
            throw new IllegalArgumentException("code cannot be blank");
        }
    }

    public static ApplicationError of(String code, String message) {
        return new ApplicationError(code, message);
    }

    public ApplicationErrorException toException() {
        return new ApplicationErrorException(this);
    }

}

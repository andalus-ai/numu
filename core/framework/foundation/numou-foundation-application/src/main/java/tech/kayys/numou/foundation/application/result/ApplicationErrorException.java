package tech.kayys.numou.foundation.application.result;

import java.util.Objects;

/**
 * Thrown when {@link Result#orElseThrow()} is called on a failed
 * result. Carries the structured {@link ApplicationError} so an
 * inbound adapter can translate it into an HTTP/gRPC/message error
 * without parsing strings.
 */
public final class ApplicationErrorException extends RuntimeException {

    private final ApplicationError error;

    public ApplicationErrorException(ApplicationError error) {
        super(error.code() + ": " + error.message());
        this.error = Objects.requireNonNull(error, "error cannot be null");
    }

    public ApplicationError error() {
        return error;
    }

}

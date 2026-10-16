package tech.kayys.numou.foundation.domain.exception;

/**
 * Base exception for domain-level failures.
 */
public class DomainException
        extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }

}

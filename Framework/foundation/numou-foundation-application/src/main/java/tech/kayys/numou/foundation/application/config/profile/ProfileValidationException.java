package tech.kayys.numou.foundation.application.config.profile;

/**
 * Exception thrown when profile definition, inheritance, or resolution is invalid.
 */
public class ProfileValidationException extends RuntimeException {

    public ProfileValidationException(String message) {
        super(message);
    }

    public ProfileValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

package tech.kayys.numou.foundation.domain.exception;

/**
 * Raised when an aggregate or domain object is in a state that does
 * not allow the requested operation.
 *
 * Example: "Order is already cancelled".
 *
 * Distinct from BusinessRuleViolation: this is about the object's
 * current state disallowing the operation, not a business policy
 * being broken.
 */
public class InvalidStateException
        extends DomainException {

    public InvalidStateException(String message) {
        super(message);
    }

}

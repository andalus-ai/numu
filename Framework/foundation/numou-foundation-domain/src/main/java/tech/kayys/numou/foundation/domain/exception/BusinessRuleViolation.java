package tech.kayys.numou.foundation.domain.exception;

/**
 * Raised when a domain business rule is violated.
 *
 * Example: "Cannot confirm an empty order",
 *          "Customer cannot purchase beyond credit limit".
 */
public class BusinessRuleViolation
        extends DomainException {

    public BusinessRuleViolation(String message) {
        super(message);
    }

}

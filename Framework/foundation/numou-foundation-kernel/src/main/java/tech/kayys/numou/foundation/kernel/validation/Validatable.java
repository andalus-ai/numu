package tech.kayys.numou.foundation.kernel.validation;

/**
 * Self-validating object contract for Commands, Value Objects, and Requests.
 */
public interface Validatable {

    /**
     * Asserts that invariants and preconditions are satisfied.
     * Throws an exception or violation if invalid.
     */
    void validate();
}

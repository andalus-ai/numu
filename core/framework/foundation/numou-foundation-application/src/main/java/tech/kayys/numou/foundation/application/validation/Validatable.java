package tech.kayys.numou.foundation.application.validation;

/**
 * Self-validating object contract for Commands, Queries, and Requests.
 */
public interface Validatable {

    /**
     * Asserts that invariants and preconditions are satisfied.
     * Throws an exception or error if invalid.
     */
    void validate();
}

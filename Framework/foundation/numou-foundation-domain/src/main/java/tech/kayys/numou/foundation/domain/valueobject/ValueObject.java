package tech.kayys.numou.foundation.domain.valueobject;

/**
 * Marker interface for immutable domain value objects.
 *
 * Value objects are defined by their attributes and have no identity.
 * They should be: immutable, self-validating, side-effect free, and
 * compared by value. Java records satisfy most of this for free.
 */
public interface ValueObject {

    /**
     * Optional validation hook for value objects.
     */
    default void validate() {
        // Default: no validation
    }
}

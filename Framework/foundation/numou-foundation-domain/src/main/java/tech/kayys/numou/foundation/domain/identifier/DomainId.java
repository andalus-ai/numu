package tech.kayys.numou.foundation.domain.identifier;

/**
 * Marker abstraction for strongly typed domain identifiers.
 *
 * <p>This is the <em>single</em> canonical ID mechanism: semantic identifiers are
 * expressed as records implementing this interface, e.g.</p>
 *
 * <pre>{@code
 * public record ProductId(UUID value) implements DomainId<UUID> { ... }
 * }</pre>
 *
 * @param <T> the identifier value type
 */
public interface DomainId<T> {

    /**
     * The underlying value of this identifier.
     */
    T value();

    /**
     * Migration bridge for callers of the retired {@code Identifier<T>} base class.
     *
     * <p>New code must use {@link #value()}. This accessor exists only so the
     * {@code Identifier<T>} -> {@code record implements DomainId<T>} migration can
     * land without rewriting every call site in the same change; it is scheduled
     * for removal once all callers use {@link #value()}.</p>
     *
     * @deprecated use {@link #value()} instead
     */
    @Deprecated
    default T getValue() {
        return value();
    }

}

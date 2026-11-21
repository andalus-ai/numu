package tech.kayys.numou.runtime;

/**
 * A host-managed runtime instance for one resolved capability provider.
 *
 * <p>Implementations are infrastructure/application adapters. Business invariants
 * remain in the capability and are not moved into the runtime host.</p>
 */
public interface CapabilityRuntime {
    /** Stable provider identifier matching the application capability catalog. */
    String providerId();

    /** Initialize external resources and prepare the capability for execution. */
    default void start() throws Exception {
    }

    /** Release resources. Implementations should be safe to call after partial startup. */
    default void stop() throws Exception {
    }
}

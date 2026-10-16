package tech.kayys.numou.foundation.application.runtime;

/**
 * Composition Root abstraction that assembles the application runtime (config01.md §P4-01 #5).
 */
public interface ApplicationCompositionRoot {

    ApplicationRuntime build(ApplicationRuntimeConfiguration configuration);
}

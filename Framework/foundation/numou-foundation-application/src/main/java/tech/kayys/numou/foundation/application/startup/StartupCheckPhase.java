package tech.kayys.numou.foundation.application.startup;

/**
 * Deterministic execution phase of startup checks (config01.md §P4-04 #25).
 */
public enum StartupCheckPhase {
    CONFIGURATION,
    PROFILE,
    MODULES,
    COMPONENTS,
    INFRASTRUCTURE,
    SECURITY,
    OBSERVABILITY,
    RUNTIME
}

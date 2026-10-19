package tech.kayys.numou.foundation.application.tracing;

/**
 * Execution status for distributed trace spans (enhance05.md §6).
 */
public enum TraceStatus {
    UNSET,
    OK,
    ERROR
}

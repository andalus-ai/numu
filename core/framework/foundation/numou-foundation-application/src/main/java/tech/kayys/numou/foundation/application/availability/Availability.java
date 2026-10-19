package tech.kayys.numou.foundation.application.availability;

/**
 * Composite availability contract encapsulating Liveness and Readiness (config01.md §P4-06 #4).
 */
public interface Availability {

    Liveness liveness();

    Readiness readiness();
}

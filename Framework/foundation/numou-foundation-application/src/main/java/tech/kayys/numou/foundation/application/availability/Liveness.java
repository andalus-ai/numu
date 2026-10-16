package tech.kayys.numou.foundation.application.availability;

/**
 * Liveness contract (config01.md §P4-06 #5).
 */
public interface Liveness {

    LivenessStatus livenessStatus();

    default boolean isAlive() {
        return livenessStatus() == LivenessStatus.ALIVE;
    }
}

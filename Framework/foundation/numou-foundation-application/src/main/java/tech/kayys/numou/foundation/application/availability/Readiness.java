package tech.kayys.numou.foundation.application.availability;

import java.util.List;

/**
 * Readiness contract (config01.md §P4-06 #6).
 */
public interface Readiness {

    ReadinessStatus readinessStatus();

    default boolean isReady() {
        return readinessStatus() == ReadinessStatus.READY;
    }

    List<AvailabilityReason> reasons();
}

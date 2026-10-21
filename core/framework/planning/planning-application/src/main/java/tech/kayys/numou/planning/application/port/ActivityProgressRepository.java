package tech.kayys.numou.planning.application.port;

import tech.kayys.numou.planning.domain.ActivityId;
import tech.kayys.numou.planning.domain.ActivityProgressObservation;
import tech.kayys.numou.planning.domain.PlanId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence port for append-only actual-progress evidence. */
public interface ActivityProgressRepository {
    /**
     * Appends a progress observation. Returns false for an identical replay of the same
     * idempotency key; rejects reuse of that key for a different business payload.
     */
    boolean append(PlanId planId, ActivityProgressObservation observation);

    List<ActivityProgressObservation> findHistory(PlanId planId, ActivityId activityId);

    Optional<ActivityProgressObservation> findLatestAsOf(
            PlanId planId, ActivityId activityId, LocalDate asOfDate);
}

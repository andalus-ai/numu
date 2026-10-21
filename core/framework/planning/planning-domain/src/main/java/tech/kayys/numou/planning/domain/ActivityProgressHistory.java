package tech.kayys.numou.planning.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory domain model for append-only progress observations for one schedule plan.
 * Persistence adapters should enforce the same idempotency-key uniqueness per plan.
 */
public final class ActivityProgressHistory {
    private final PlanId planId;
    private final Map<String, ActivityProgressObservation> byIdempotencyKey = new HashMap<>();
    private final List<ActivityProgressObservation> observations = new ArrayList<>();

    public ActivityProgressHistory(PlanId planId) {
        this.planId = Objects.requireNonNull(planId, "Plan id cannot be null");
    }

    /**
     * Appends an observation. A byte-for-byte semantic replay of the same key is a no-op;
     * reuse of a key for different business data is rejected rather than silently discarded.
     *
     * @return true only when a new observation was appended
     */
    public boolean append(ActivityProgressObservation observation) {
        Objects.requireNonNull(observation, "Observation cannot be null");
        ActivityProgressObservation previous = byIdempotencyKey.get(observation.idempotencyKey());
        if (previous != null) {
            if (sameBusinessPayload(previous, observation)) return false;
            throw new IllegalArgumentException("Idempotency key was already used for different progress data");
        }
        byIdempotencyKey.put(observation.idempotencyKey(), observation);
        observations.add(observation);
        return true;
    }

    public List<ActivityProgressObservation> observations() {
        return observations.stream()
                .sorted(Comparator.comparing(ActivityProgressObservation::asOfDate)
                        .thenComparing(ActivityProgressObservation::recordedAt)
                        .thenComparing(o -> o.id().toString()))
                .toList();
    }

    public Optional<ActivityProgressObservation> latestAsOf(ActivityId activityId,
                                                              java.time.LocalDate asOfDate) {
        Objects.requireNonNull(activityId, "Activity id cannot be null");
        Objects.requireNonNull(asOfDate, "As-of date cannot be null");
        return observations.stream()
                .filter(o -> o.activityId().equals(activityId) && !o.asOfDate().isAfter(asOfDate))
                .max(Comparator.comparing(ActivityProgressObservation::asOfDate)
                        .thenComparing(ActivityProgressObservation::recordedAt)
                        .thenComparing(o -> o.id().toString()));
    }

    public PlanId planId() { return planId; }

    private static boolean sameBusinessPayload(ActivityProgressObservation left,
                                               ActivityProgressObservation right) {
        return left.activityId().equals(right.activityId())
                && left.asOfDate().equals(right.asOfDate())
                && left.percentComplete().compareTo(right.percentComplete()) == 0
                && left.recordedBy().equals(right.recordedBy())
                && Objects.equals(left.sourceReference(), right.sourceReference());
    }
}

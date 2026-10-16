package tech.kayys.numou.workforce.domain.learning;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

public final class LearningSession extends AbstractAggregateRoot<LearningSessionId> {

    private final LearningOfferingId offeringId;
    private final LocalDate sessionDate;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String title;
    private LearningSessionStatus status;

    private LearningSession(
            LearningSessionId id,
            LearningOfferingId offeringId,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            String title
    ) {
        super(id);
        this.offeringId = Objects.requireNonNull(offeringId, "offeringId must not be null");
        this.sessionDate = Objects.requireNonNull(sessionDate, "sessionDate must not be null");
        this.startTime = Objects.requireNonNull(startTime, "startTime must not be null");
        this.endTime = Objects.requireNonNull(endTime, "endTime must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.status = LearningSessionStatus.PLANNED;
    }

    public static LearningSession create(
            LearningSessionId id,
            LearningOfferingId offeringId,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            String title
    ) {
        return new LearningSession(id, offeringId, sessionDate, startTime, endTime, title);
    }

    public void complete() {
        this.status = LearningSessionStatus.COMPLETED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public LearningOfferingId getOfferingId() { return offeringId; }
    public LocalDate getSessionDate() { return sessionDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getTitle() { return title; }
    public LearningSessionStatus getStatus() { return status; }
}

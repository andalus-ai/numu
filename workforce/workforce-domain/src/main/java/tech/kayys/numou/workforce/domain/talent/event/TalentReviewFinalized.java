package tech.kayys.numou.workforce.domain.talent.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.talent.TalentReviewId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record TalentReviewFinalized(
        UUID eventId,
        Instant occurredAt,
        TalentReviewId id,
        WorkerId workerId
) implements DomainEvent {
    public TalentReviewFinalized(TalentReviewId id, WorkerId workerId) {
        this(UUID.randomUUID(), Instant.now(), id, workerId);
    }
    @Override public String eventType() { return "workforce.talent.review.finalized"; }
}

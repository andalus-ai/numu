package tech.kayys.numou.content.application;

import tech.kayys.numou.content.spi.EditorialOutboxMessage;
import tech.kayys.numou.content.spi.EditorialOutboxRepository;
import tech.kayys.numou.content.spi.EditorialWorkflowOrchestrator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** At-least-once workflow dispatch. Gamelan adapters must deduplicate by review identity. */
public final class EditorialOutboxDispatcher {
    private static final Duration LEASE=Duration.ofMinutes(2);
    private static final Duration MAX_BACKOFF=Duration.ofMinutes(15);
    private final EditorialOutboxRepository outbox;
    private final EditorialWorkflowOrchestrator workflow;
    private final Clock clock;
    private final UUID workerId;

    public EditorialOutboxDispatcher(EditorialOutboxRepository outbox, EditorialWorkflowOrchestrator workflow,
            Clock clock, UUID workerId) {
        this.outbox=Objects.requireNonNull(outbox); this.workflow=Objects.requireNonNull(workflow);
        this.clock=Objects.requireNonNull(clock); this.workerId=Objects.requireNonNull(workerId);
    }

    public int dispatchBatch(int limit) {
        if(limit<1 || limit>500) throw new IllegalArgumentException("Batch limit must be between 1 and 500");
        Instant now=clock.instant();
        var messages=outbox.claim(workerId,now,now.plus(LEASE),limit);
        int published=0;
        for(EditorialOutboxMessage message:messages) {
            try {
                dispatch(message);
                if(outbox.markPublished(message.tenantId(),message.eventId(),workerId,clock.instant())) published++;
            } catch(RuntimeException failure) {
                long multiplier=1L << Math.min(10,Math.max(0,message.attempts()-1));
                Duration backoff=Duration.ofSeconds(Math.min(MAX_BACKOFF.toSeconds(),30L*multiplier));
                String detail=failure.getClass().getSimpleName()+": "+String.valueOf(failure.getMessage());
                outbox.releaseForRetry(message.tenantId(),message.eventId(),workerId,
                        clock.instant().plus(backoff),detail);
            }
        }
        return published;
    }

    private void dispatch(EditorialOutboxMessage message) {
        switch(message.eventType()) {
            case "REVIEW_SUBMITTED" -> workflow.start(message.tenantId(),message.contentId(),
                    message.revisionNumber(),message.reviewId());
            case "REVIEW_APPROVED" -> workflow.complete(message.tenantId(),message.reviewId());
            case "REVIEW_CANCELLED" -> workflow.cancel(message.tenantId(),message.reviewId());
            case "REVIEW_ACTIVITY", "REVIEW_CHANGES_REQUESTED" ->
                    workflow.signal(message.tenantId(),message.reviewId(),message.eventType(),message.payload());
            default -> throw new IllegalArgumentException("Unsupported editorial outbox event: "+message.eventType());
        }
    }
}

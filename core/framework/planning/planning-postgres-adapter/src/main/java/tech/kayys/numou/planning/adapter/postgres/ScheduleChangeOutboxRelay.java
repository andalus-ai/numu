package tech.kayys.numou.planning.adapter.postgres;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.UUID;

/**
 * Scheduled at-least-once delivery of committed schedule-change events.
 *
 * <p>The transport is supplied by a {@link ScheduleChangeEventPublisher} bean. If no
 * transport is installed, the relay intentionally leaves rows untouched rather than
 * marking events delivered or silently discarding them. Deployments should alert on
 * pending outbox age and configure a publisher for the transport they use.
 */
@ApplicationScoped
public class ScheduleChangeOutboxRelay {
    private static final Logger LOG = Logger.getLogger(ScheduleChangeOutboxRelay.class);

    private final JdbcScheduleChangeOutboxRelayStore store;
    private final Instance<ScheduleChangeEventPublisher> publishers;
    private final int batchSize;
    private final long leaseSeconds;

    @Inject
    public ScheduleChangeOutboxRelay(
            JdbcScheduleChangeOutboxRelayStore store,
            Instance<ScheduleChangeEventPublisher> publishers,
            @ConfigProperty(name = "numou.planning.outbox.batch-size", defaultValue = "100") int batchSize,
            @ConfigProperty(name = "numou.planning.outbox.lease-seconds", defaultValue = "30") long leaseSeconds) {
        if (batchSize < 1) throw new IllegalArgumentException("outbox batch size must be >= 1");
        if (leaseSeconds < 1) throw new IllegalArgumentException("outbox lease must be >= 1 second");
        this.store = store;
        this.publishers = publishers;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(every = "${numou.planning.outbox.poll-interval:1s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void drain() {
        if (publishers.isUnsatisfied()) {
            LOG.debug("Schedule-change outbox relay has no publisher; pending events are retained");
            return;
        }

        Instant now = Instant.now();
        UUID claimToken = UUID.randomUUID();
        var events = store.claimDue(batchSize, now, now.plusSeconds(leaseSeconds), claimToken);
        if (events.isEmpty()) return;

        ScheduleChangeEventPublisher publisher = publishers.get();
        int published = 0;
        int failed = 0;
        for (var event : events) {
            try {
                publisher.publish(event);
                if (store.markPublished(event.outboxId(), event.claimToken(), Instant.now())) {
                    published++;
                } else {
                    // The transport may have accepted the event after this worker lost its lease.
                    // Do not mutate the row; a later attempt may deliver it again.
                    LOG.warnf("Schedule-change event %s was published but its lease was no longer owned",
                            event.outboxId());
                }
            } catch (Exception failure) {
                failed++;
                Instant failedAt = Instant.now();
                Instant retryAt = failedAt.plusSeconds(retryDelaySeconds(event.attemptCount()));
                boolean scheduled = store.recordFailure(event.outboxId(), event.claimToken(), retryAt,
                        describe(failure));
                if (!scheduled) {
                    LOG.warnf("Unable to reschedule failed schedule-change event %s because its lease expired",
                            event.outboxId());
                }
            }
        }
        LOG.debugf("Schedule-change outbox batch completed: claimed=%d published=%d failed=%d",
                events.size(), published, failed);
    }

    static long retryDelaySeconds(int attemptCount) {
        if (attemptCount < 1) throw new IllegalArgumentException("attemptCount must be >= 1");
        // 2, 4, 8, ... seconds, capped at five minutes to avoid overflow and retry storms.
        return Math.min(300L, 1L << Math.min(attemptCount, 8));
    }

    private static String describe(Exception failure) {
        String message = failure.getMessage();
        String detail = failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return detail.length() <= 2000 ? detail : detail.substring(0, 2000);
    }
}

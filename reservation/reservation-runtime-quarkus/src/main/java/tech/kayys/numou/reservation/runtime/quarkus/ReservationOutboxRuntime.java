package tech.kayys.numou.reservation.runtime.quarkus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import tech.kayys.numou.reservation.adapter.postgres.PgReservationOutboxRelay;
import tech.kayys.numou.reservation.adapter.postgres.ReservationOutboxPublisher;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Quarkus composition root for the PostgreSQL outbox relay.
 *
 * <p>Include this optional module in a host application only when the host also configures
 * the reactive PostgreSQL client and the {@code reservation-outbox-events} Kafka channel.</p>
 */
@ApplicationScoped
public class ReservationOutboxRuntime {
    private static final Logger LOG = Logger.getLogger(ReservationOutboxRuntime.class);

    @ConfigProperty(name = "numou.reservation.outbox.batch-size", defaultValue = "50")
    int batchSize;
    @ConfigProperty(name = "numou.reservation.outbox.max-attempts", defaultValue = "12")
    int maxAttempts;
    @ConfigProperty(name = "numou.reservation.outbox.lease", defaultValue = "PT30S")
    Duration lease;
    @ConfigProperty(name = "numou.reservation.outbox.initial-backoff", defaultValue = "PT1S")
    Duration initialBackoff;
    @ConfigProperty(name = "numou.reservation.outbox.max-backoff", defaultValue = "PT5M")
    Duration maxBackoff;
    @ConfigProperty(name = "numou.reservation.outbox.shutdown-drain-timeout", defaultValue = "PT10S")
    Duration shutdownDrainTimeout;

    @Inject
    PgPool pool;
    @Inject
    ReservationOutboxPublisher publisher;
    @Inject
    MeterRegistry meterRegistry;

    private PgReservationOutboxRelay relay;
    private Counter pollCounter;
    private Counter publishedCounter;
    private Counter failureCounter;
    private Counter shutdownDrainTimeoutCounter;
    private Counter backlogRefreshFailureCounter;
    private final AtomicLong pendingEvents = new AtomicLong();
    private final AtomicLong processingEvents = new AtomicLong();
    private final AtomicLong deadEvents = new AtomicLong();
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();

    // Guarded by this: prevents a scheduled poll from starting after shutdown begins.
    private boolean stopping;
    private CompletableFuture<Void> inFlight;

    @PostConstruct
    void initialize() {
        relay = new PgReservationOutboxRelay(
                pool, publisher, batchSize, maxAttempts, lease, initialBackoff, maxBackoff);
        pollCounter = meterRegistry.counter("numou.reservation.outbox.polls");
        publishedCounter = meterRegistry.counter("numou.reservation.outbox.events.published");
        failureCounter = meterRegistry.counter("numou.reservation.outbox.poll.failures");
        shutdownDrainTimeoutCounter = meterRegistry.counter("numou.reservation.outbox.shutdown.drain.timeouts");
        backlogRefreshFailureCounter = meterRegistry.counter("numou.reservation.outbox.backlog.refresh.failures");
        meterRegistry.gauge("numou.reservation.outbox.events.pending", pendingEvents);
        meterRegistry.gauge("numou.reservation.outbox.events.processing", processingEvents);
        meterRegistry.gauge("numou.reservation.outbox.events.dead", deadEvents);
        meterRegistry.gauge("numou.reservation.outbox.oldest.pending.age.seconds", oldestPendingAgeSeconds);
    }

    /**
     * Reactive scheduled method. In-process overlapping polls are skipped; PostgreSQL leases
     * and SKIP LOCKED coordinate separate replicas.
     */
    @Scheduled(every = "{numou.reservation.outbox.poll-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    Uni<Void> poll() {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        synchronized (this) {
            if (stopping || inFlight != null) {
                return Uni.createFrom().voidItem();
            }
            inFlight = completed;
        }

        pollCounter.increment();
        return relay.pollOnce()
                .invoke(delivered -> {
                    if (delivered > 0) {
                        publishedCounter.increment(delivered);
                        LOG.debugf("Reservation outbox delivered %d event(s)", delivered);
                    }
                })
                .replaceWithVoid()
                .onFailure().invoke(failure -> {
                    failureCounter.increment();
                    LOG.error("Reservation outbox poll failed; persisted leases and retry state remain recoverable", failure);
                })
                .onTermination().invoke(() -> {
                    synchronized (this) {
                        if (inFlight == completed) {
                            inFlight = null;
                        }
                    }
                    completed.complete(null);
                });
    }


    /**
     * Refresh low-cardinality operational gauges. Counts are intentionally global to this
     * outbox table; tenant IDs are not metric labels because they create unbounded cardinality
     * and may expose tenant activity through the metrics endpoint.
     */
    @Scheduled(every = "{numou.reservation.outbox.metrics-refresh-interval}")
    Uni<Void> refreshBacklogMetrics() {
        return pool.query("""
                SELECT
                    count(*) FILTER (WHERE status = 'PENDING') AS pending_count,
                    count(*) FILTER (WHERE status = 'PROCESSING') AS processing_count,
                    count(*) FILTER (WHERE status = 'DEAD') AS dead_count,
                    COALESCE(EXTRACT(EPOCH FROM
                        (clock_timestamp() - (min(occurred_at) FILTER (WHERE status = 'PENDING')))), 0)::bigint
                        AS oldest_pending_age_seconds
                  FROM reservation_outbox_event
                """).execute()
                .invoke(rows -> {
                    var row = rows.iterator().next();
                    pendingEvents.set(row.getLong("pending_count"));
                    processingEvents.set(row.getLong("processing_count"));
                    deadEvents.set(row.getLong("dead_count"));
                    oldestPendingAgeSeconds.set(row.getLong("oldest_pending_age_seconds"));
                })
                .replaceWithVoid()
                .onFailure().invoke(failure -> {
                    backlogRefreshFailureCounter.increment();
                    LOG.error("Unable to refresh reservation outbox backlog metrics", failure);
                })
                .onFailure().recoverWithItem((Void) null);
    }

    /**
     * Stop admitting polls and allow the current reactive poll to finish within a bounded
     * shutdown window. Unacknowledged leases remain recoverable after expiry if the host exits.
     */
    @PreDestroy
    void stopAndDrain() {
        CompletableFuture<Void> active;
        synchronized (this) {
            stopping = true;
            active = inFlight;
        }
        if (active == null) {
            return;
        }
        try {
            active.get(shutdownDrainTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            shutdownDrainTimeoutCounter.increment();
            LOG.warnf("Reservation outbox shutdown drain exceeded %d ms; remaining leases will recover after expiry",
                    shutdownDrainTimeout.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while draining reservation outbox poll; remaining leases will recover after expiry");
        } catch (ExecutionException impossible) {
            // The completion signal is always completed normally; retain defensive handling.
            LOG.warn("Reservation outbox poll completion signal failed during shutdown", impossible);
        }
    }
}

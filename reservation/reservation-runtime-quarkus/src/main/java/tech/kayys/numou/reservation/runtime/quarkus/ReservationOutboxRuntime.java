package tech.kayys.numou.reservation.runtime.quarkus;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import tech.kayys.numou.reservation.adapter.postgres.PgReservationOutboxRelay;
import tech.kayys.numou.reservation.adapter.postgres.ReservationOutboxPublisher;

import java.time.Duration;

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

    @Inject
    PgPool pool;
    @Inject
    ReservationOutboxPublisher publisher;

    private PgReservationOutboxRelay relay;

    @PostConstruct
    void initialize() {
        relay = new PgReservationOutboxRelay(
                pool, publisher, batchSize, maxAttempts, lease, initialBackoff, maxBackoff);
    }

    /**
     * Reactive scheduled method: the scheduler does not block a worker thread while the poll
     * is in flight and skips overlapping invocations in this process. PostgreSQL leases and
     * SKIP LOCKED continue to coordinate separate replicas.
     */
    @Scheduled(every = "{numou.reservation.outbox.poll-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    Uni<Void> poll() {
        return relay.pollOnce()
                .invoke(delivered -> {
                    if (delivered > 0) {
                        LOG.debugf("Reservation outbox delivered %d event(s)", delivered);
                    }
                })
                .replaceWithVoid()
                .onFailure().invoke(failure ->
                        LOG.error("Reservation outbox poll failed; pending events remain recoverable", failure));
    }
}

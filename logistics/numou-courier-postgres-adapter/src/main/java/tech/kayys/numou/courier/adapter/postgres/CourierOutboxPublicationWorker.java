package tech.kayys.numou.courier.adapter.postgres;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import java.util.UUID;

/** Polls only when a real deployment transport adapter is installed; never silently discards events. */
@ApplicationScoped
public class CourierOutboxPublicationWorker {
    private static final Logger LOG = Logger.getLogger(CourierOutboxPublicationWorker.class);
    private final JdbcCourierOutboxPublisher relay;
    private final Instance<CourierEventPublisher> publisher;
    private final String destination;
    private final int batchSize;

    @Inject
    public CourierOutboxPublicationWorker(
            JdbcCourierOutboxPublisher relay,
            Instance<CourierEventPublisher> publisher,
            @ConfigProperty(name = "numou.courier.outbox.destination", defaultValue = "default") String destination,
            @ConfigProperty(name = "numou.courier.outbox.batch-size", defaultValue = "100") int batchSize) {
        this.relay = relay;
        this.publisher = publisher;
        this.destination = destination;
        this.batchSize = batchSize;
    }

    @Scheduled(every = "{numou.courier.outbox.interval:5s}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void publish() {
        if (publisher.isUnsatisfied()) {
            LOG.debug("Courier outbox publication skipped: no CourierEventPublisher transport adapter is configured");
            return;
        }
        try {
            int count = relay.publishBatch(destination, "courier-relay-" + UUID.randomUUID(), batchSize, publisher.get());
            if (count > 0) LOG.infov("Published {0} courier outbox events to {1}", count, destination);
        } catch (RuntimeException failure) {
            LOG.error("Courier outbox publication batch failed", failure);
        }
    }
}

package tech.kayys.numou.transport.runtime;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import tech.kayys.numou.transport.application.DepartureBookingService;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * Drives bounded hold expiration in a host runtime. The application service remains usable
 * without Quarkus; this bean is only an optional scheduling/observability adapter.
 */
@ApplicationScoped
public class HoldExpiryScheduler {
    private static final Logger LOG = Logger.getLogger(HoldExpiryScheduler.class);

    @Inject
    DepartureBookingService bookings;

    @Inject
    MeterRegistry meters;

    @ConfigProperty(name = "numou.transport.expiry.batch-size", defaultValue = "200")
    int batchSize;

    private Counter runs;
    private Counter expired;
    private Counter failures;
    private Timer duration;

    @PostConstruct
    void initializeMetrics() {
        runs = Counter.builder("numou.transport.hold.expiry.runs")
                .description("Number of hold-expiration scheduler executions").register(meters);
        expired = Counter.builder("numou.transport.hold.expiry.transitions")
                .description("Number of passenger holds transitioned to expired").register(meters);
        failures = Counter.builder("numou.transport.hold.expiry.failures")
                .description("Number of failed hold-expiration scheduler executions").register(meters);
        duration = Timer.builder("numou.transport.hold.expiry.duration")
                .description("Duration of hold-expiration scheduler executions").register(meters);
    }

    @Scheduled(every = "{numou.transport.expiry.interval:5s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
            identity = "numou-transport-hold-expiry")
    void tick() {
        long started = System.nanoTime();
        runs.increment();
        try {
            int count = bookings.expireDueHolds(batchSize);
            expired.increment(count);
            if (count > 0) {
                LOG.infof("Transport hold expiry completed: transitioned=%d batchSize=%d", count, batchSize);
            }
        } catch (RuntimeException failure) {
            failures.increment();
            LOG.error("Transport hold expiry failed; a later scheduled run will retry due inventory", failure);
        } finally {
            duration.record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }
}

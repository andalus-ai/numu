package tech.kayys.numou.transport.runtime;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import tech.kayys.numou.transport.application.TransportCheckoutService;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Optional bounded reconciliation for payment states that may change asynchronously.
 * Payment remains authoritative; this worker only asks the application saga to re-read it.
 */
@ApplicationScoped
@IfBuildProperty(name = "numou.transport.checkout.enabled", stringValue = "true")
public class TransportCheckoutReconciliationScheduler {
    private static final Logger LOG = Logger.getLogger(TransportCheckoutReconciliationScheduler.class);

    @Inject
    TransportCheckoutService checkouts;

    @Inject
    MeterRegistry meters;

    @ConfigProperty(name = "numou.transport.checkout.reconciliation.batch-size", defaultValue = "100")
    int batchSize;

    @ConfigProperty(name = "numou.transport.checkout.reconciliation.minimum-age-seconds", defaultValue = "60")
    long minimumAgeSeconds;

    private Counter runs;
    private Counter attempted;
    private Counter failed;
    private Timer duration;

    @PostConstruct
    void initializeMetrics() {
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalStateException(
                    "numou.transport.checkout.reconciliation.batch-size must be between 1 and 1000");
        }
        if (minimumAgeSeconds < 1 || minimumAgeSeconds > 86_400) {
            throw new IllegalStateException(
                    "numou.transport.checkout.reconciliation.minimum-age-seconds must be between 1 and 86400");
        }
        runs = Counter.builder("numou.transport.checkout.reconciliation.runs")
                .description("Number of checkout reconciliation scheduler executions").register(meters);
        attempted = Counter.builder("numou.transport.checkout.reconciliation.attempted")
                .description("Number of stale checkouts inspected by the reconciliation worker").register(meters);
        failed = Counter.builder("numou.transport.checkout.reconciliation.failed")
                .description("Number of stale checkouts whose reconciliation attempt failed").register(meters);
        duration = Timer.builder("numou.transport.checkout.reconciliation.duration")
                .description("Duration of checkout reconciliation scheduler executions").register(meters);
    }

    @Scheduled(every = "{numou.transport.checkout.reconciliation.interval:30s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
            identity = "numou-transport-checkout-reconciliation")
    void tick() {
        long started = System.nanoTime();
        runs.increment();
        try {
            TransportCheckoutService.ReconciliationResult result =
                    checkouts.reconcileDue(Duration.ofSeconds(minimumAgeSeconds), batchSize);
            if (result.attempted() > 0) attempted.increment(result.attempted());
            if (result.failed() > 0) {
                failed.increment(result.failed());
                LOG.warnf("Transport checkout reconciliation had failures: attempted=%d succeeded=%d failed=%d",
                        result.attempted(), result.succeeded(), result.failed());
            } else if (result.attempted() > 0) {
                LOG.infof("Transport checkout reconciliation completed: attempted=%d succeeded=%d",
                        result.attempted(), result.succeeded());
            }
        } catch (RuntimeException failure) {
            failed.increment();
            LOG.error("Transport checkout reconciliation batch failed; a later scheduled run will retry", failure);
        } finally {
            duration.record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }
}

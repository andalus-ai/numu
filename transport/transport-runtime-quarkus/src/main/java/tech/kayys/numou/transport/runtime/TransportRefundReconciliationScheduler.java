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

/** Bounded worker that re-queries pending refund operations through Payment's idempotent public contract. */
@ApplicationScoped
@IfBuildProperty(name = "numou.transport.checkout.enabled", stringValue = "true")
public class TransportRefundReconciliationScheduler {
    private static final Logger LOG = Logger.getLogger(TransportRefundReconciliationScheduler.class);

    @Inject
    TransportCheckoutService checkouts;

    @Inject
    MeterRegistry meters;

    @ConfigProperty(name = "numou.transport.checkout.refund-reconciliation.batch-size", defaultValue = "50")
    int batchSize;

    @ConfigProperty(name = "numou.transport.checkout.refund-reconciliation.minimum-age-seconds", defaultValue = "60")
    long minimumAgeSeconds;

    private Counter runs;
    private Counter attempted;
    private Counter failed;
    private Timer duration;

    @PostConstruct
    void initializeMetrics() {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalStateException(
                    "numou.transport.checkout.refund-reconciliation.batch-size must be between 1 and 500");
        }
        if (minimumAgeSeconds < 1 || minimumAgeSeconds > 86_400) {
            throw new IllegalStateException(
                    "numou.transport.checkout.refund-reconciliation.minimum-age-seconds must be between 1 and 86400");
        }
        runs = Counter.builder("numou.transport.checkout.refund-reconciliation.runs")
                .description("Number of pending refund reconciliation scheduler executions").register(meters);
        attempted = Counter.builder("numou.transport.checkout.refund-reconciliation.attempted")
                .description("Number of pending refund operations inspected").register(meters);
        failed = Counter.builder("numou.transport.checkout.refund-reconciliation.failed")
                .description("Number of refund reconciliation attempts that could not be completed").register(meters);
        duration = Timer.builder("numou.transport.checkout.refund-reconciliation.duration")
                .description("Duration of pending refund reconciliation executions").register(meters);
    }

    @Scheduled(every = "{numou.transport.checkout.refund-reconciliation.interval:60s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
            identity = "numou-transport-refund-reconciliation")
    void tick() {
        long started = System.nanoTime();
        runs.increment();
        try {
            TransportCheckoutService.ReconciliationResult result =
                    checkouts.reconcileDueRefunds(Duration.ofSeconds(minimumAgeSeconds), batchSize);
            if (result.attempted() > 0) attempted.increment(result.attempted());
            if (result.failed() > 0) {
                failed.increment(result.failed());
                LOG.warnf("Transport refund reconciliation had failures: attempted=%d processed=%d failed=%d checkoutIds=%s",
                        result.attempted(), result.succeeded(), result.failed(), result.failedCheckoutIds());
            }
        } catch (RuntimeException failure) {
            failed.increment();
            LOG.error("Transport refund reconciliation batch failed; a later scheduled run will retry", failure);
        } finally {
            duration.record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }
}

package tech.kayys.numou.pos.adapter.outbox;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.numou.pos.application.PosOutboxDispatcher;

/** Managed polling trigger; database leases coordinate claims across replicas. */
@ApplicationScoped
@IfBuildProperty(name = "numou.pos.outbox.enabled", stringValue = "true")
public class PosOutboxDispatchJob {
    private static final Logger LOG = Logger.getLogger(PosOutboxDispatchJob.class);
    private final PosOutboxDispatcher dispatcher;
    private final PosOutboxMetrics metrics;

    @Inject
    public PosOutboxDispatchJob(PosOutboxDispatcher dispatcher, PosOutboxMetrics metrics) {
        this.dispatcher = dispatcher;
        this.metrics = metrics;
    }

    @Scheduled(every = "${numou.pos.outbox.dispatch-interval:1s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void dispatch() {
        try {
            int published = dispatcher.dispatchOnce();
            metrics.passSucceeded(published);
            if (published > 0) LOG.debugf("Published %d POS outbox event(s)", published);
        } catch (RuntimeException failure) {
            metrics.passFailed();
            LOG.error("POS outbox dispatch pass failed; it will be retried on the next tick", failure);
        }
    }
}

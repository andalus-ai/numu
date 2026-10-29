package tech.kayys.numou.pos.adapter.outbox;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.numou.pos.application.PosOutboxDispatcher;

/** Managed polling trigger; database leases coordinate claims across replicas. */
@ApplicationScoped
public class PosOutboxDispatchJob {
    private static final Logger LOG = Logger.getLogger(PosOutboxDispatchJob.class);
    private final PosOutboxDispatcher dispatcher;

    @Inject
    public PosOutboxDispatchJob(PosOutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(every = "${numou.pos.outbox.dispatch-interval:1s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void dispatch() {
        try {
            int published = dispatcher.dispatchOnce();
            if (published > 0) LOG.debugf("Published %d POS outbox event(s)", published);
        } catch (RuntimeException failure) {
            LOG.error("POS outbox dispatch pass failed; it will be retried on the next tick", failure);
        }
    }
}

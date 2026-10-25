package tech.kayys.numou.courier.adapter.postgres;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runtime worker for the durable POD-to-tracking projection.
 *
 * The projector owns row locking, retry scheduling, and transactional acknowledgement;
 * this bean only provides bounded periodic execution and operational logging.
 */
@ApplicationScoped
public class ProofOfDeliveryProjectionWorker {
    private static final Logger LOG = Logger.getLogger(ProofOfDeliveryProjectionWorker.class.getName());

    private final JdbcProofOfDeliveryTrackingProjector projector;
    private final int batchSize;

    @Inject
    public ProofOfDeliveryProjectionWorker(
            JdbcProofOfDeliveryTrackingProjector projector,
            @ConfigProperty(name = "numou.courier.pod-projector.batch-size", defaultValue = "100")
            int batchSize) {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("numou.courier.pod-projector.batch-size must be between 1 and 500");
        }
        this.projector = projector;
        this.batchSize = batchSize;
    }

    @Scheduled(
            every = "${numou.courier.pod-projector.interval:5s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void projectPendingProofs() {
        try {
            int projected = projector.projectBatch(batchSize).toCompletableFuture().join();
            if (projected > 0) {
                LOG.info(() -> "Projected " + projected + " proof-of-delivery tracking event(s)");
            }
        } catch (RuntimeException failure) {
            LOG.log(Level.SEVERE, "Proof-of-delivery tracking projection failed; eligible rows remain retryable",
                    failure);
        }
    }
}

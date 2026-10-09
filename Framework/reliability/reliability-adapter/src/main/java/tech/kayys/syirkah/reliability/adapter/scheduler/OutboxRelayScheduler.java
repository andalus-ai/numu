package tech.kayys.syirkah.reliability.adapter.scheduler;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.syirkah.reliability.spi.port.OutboxRelayPort;

/**
 * Drives the relay. {@code SKIP} prevents overlapping runs in one JVM; across replicas the
 * {@code FOR UPDATE SKIP LOCKED} lease in the claim query keeps them from double-sending.
 * Keep draining while batches come back full so a backlog clears quickly. NOT COMPILED in sandbox.
 */
@ApplicationScoped
public class OutboxRelayScheduler {

    private static final Logger LOG = Logger.getLogger(OutboxRelayScheduler.class);
    private static final int MAX_BATCHES_PER_TICK = 20;

    @Inject
    OutboxRelayPort relay;

    @Scheduled(every = "{syirkah.outbox.relay-interval:1s}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
            identity = "outbox-relay")
    Uni<Void> tick() {
        return drain(0).onFailure().invoke(t -> LOG.errorf(t, "outbox relay tick failed")).onFailure().recoverWithNull().replaceWithVoid();
    }

    private Uni<Integer> drain(int batches) {
        return relay.runOnce().flatMap(n -> n > 0 && batches < MAX_BATCHES_PER_TICK ? drain(batches + 1) : Uni.createFrom().item(n));
    }
}

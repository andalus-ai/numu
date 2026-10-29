package tech.kayys.numou.pos.adapter.outbox;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.pos.application.PosOutboxDispatcher;
import tech.kayys.numou.pos.application.port.out.PosEventPublisher;
import tech.kayys.numou.pos.application.port.out.PosOutboxDeliveryStore;

import java.time.Clock;
import java.time.Duration;

/** Composes the application dispatcher from configured runtime adapters. */
@ApplicationScoped
public class PosOutboxDispatcherProducer {
    @Inject PosOutboxDeliveryStore deliveryStore;
    @Inject PosEventPublisher eventPublisher;

    @ConfigProperty(name = "numou.pos.outbox.lease-duration", defaultValue = "PT30S")
    Duration leaseDuration;
    @ConfigProperty(name = "numou.pos.outbox.initial-backoff", defaultValue = "PT1S")
    Duration initialBackoff;
    @ConfigProperty(name = "numou.pos.outbox.max-backoff", defaultValue = "PT5M")
    Duration maxBackoff;
    @ConfigProperty(name = "numou.pos.outbox.batch-size", defaultValue = "100")
    int batchSize;
    @ConfigProperty(name = "numou.pos.outbox.max-attempts", defaultValue = "12")
    int maxAttempts;

    @Produces
    @ApplicationScoped
    PosOutboxDispatcher dispatcher() {
        return new PosOutboxDispatcher(deliveryStore, eventPublisher, Clock.systemUTC(),
                leaseDuration, initialBackoff, maxBackoff, batchSize, maxAttempts);
    }
}

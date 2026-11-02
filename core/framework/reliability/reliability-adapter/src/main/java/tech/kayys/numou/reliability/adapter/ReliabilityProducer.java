package tech.kayys.numou.reliability.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.event.spi.port.EventPublisherPort;
import tech.kayys.numou.reliability.adapter.json.EventTypeRegistry;
import tech.kayys.numou.reliability.adapter.json.JsonDomainEvent;
import tech.kayys.numou.reliability.adapter.json.JsonEventSerializer;
import tech.kayys.numou.reliability.application.idempotency.IdempotentExecutor;
import tech.kayys.numou.reliability.application.inbox.InboxProcessor;
import tech.kayys.numou.reliability.application.outbox.OutboxRelayService;
import tech.kayys.numou.reliability.retry.RetryPolicy;
import tech.kayys.numou.reliability.spi.port.IdempotencyStorePort;
import tech.kayys.numou.reliability.spi.port.InboxStorePort;
import tech.kayys.numou.reliability.spi.port.OutboxRelayPort;
import tech.kayys.numou.reliability.spi.port.OutboxStorePort;
import tech.kayys.numou.event.spi.port.EventSerializerPort;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/** Wires the pure reliability services into CDI. */
@ApplicationScoped
public class ReliabilityProducer {

    @Inject
    ObjectMapper mapper;

    @ApplicationScoped
    @Produces
    EventTypeRegistry registry() {
        // Every bounded context can publish the stable generic JSON event contract.
        // Contexts may additionally register strongly typed payloads at startup.
        return new EventTypeRegistry().registerFallback(JsonDomainEvent.class);
    }

    @ApplicationScoped
    @Produces
    JsonEventSerializer jsonSerializer(EventTypeRegistry registry) {
        return new JsonEventSerializer(mapper, registry);
    }

    @ApplicationScoped
    @Produces
    OutboxRelayPort relay(OutboxStorePort store, EventPublisherPort publisher,
                          @ConfigProperty(name = "numou.outbox.batch-size", defaultValue = "50") int batchSize) {
        return new OutboxRelayService(store, publisher, RetryPolicy.DEFAULT, batchSize, Instant::now,
                () -> ThreadLocalRandom.current().nextDouble(), OutboxRelayService.defaultClassifier());
    }

    @ApplicationScoped
    @Produces
    IdempotentExecutor idempotentExecutor(IdempotencyStorePort store) {
        return new IdempotentExecutor(store);
    }

    @ApplicationScoped
    @Produces
    InboxProcessor inboxProcessor(InboxStorePort store) {
        return new InboxProcessor(store);
    }
}

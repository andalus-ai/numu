package tech.kayys.syirkah.document.adapter.outbound.messaging;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.syirkah.document.adapter.outbound.postgres.DocumentOutboxRepository;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;

import java.time.Duration;

@ApplicationScoped
public class DocumentOutboxWorker {
    private static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration LEASE_DURATION = Duration.ofMinutes(30);

    private final DocumentOutboxRepository repository;
    private final DocumentOutboxPublisher publisher;
    private final DomainClock clock;
    private final int batchSize;
    private final int maximumAttempts;

    @Inject
    public DocumentOutboxWorker(
            DocumentOutboxRepository repository,
            DocumentOutboxPublisher publisher,
            DomainClock clock,
            @ConfigProperty(name = "syirkah.document.outbox.batch-size", defaultValue = "50") int batchSize,
            @ConfigProperty(name = "syirkah.document.outbox.max-attempts", defaultValue = "12") int maximumAttempts
    ) {
        if (batchSize < 1 || batchSize > 500 || maximumAttempts < 1) {
            throw new IllegalArgumentException("Invalid document outbox worker configuration");
        }
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maximumAttempts = maximumAttempts;
    }

    @Scheduled(every = "${syirkah.document.outbox.poll-interval:5s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    Uni<Void> poll() {
        var now = clock.now();
        return repository.claimPending(now, now.plus(LEASE_DURATION), batchSize)
                .chain(messages -> Multi.createFrom().iterable(messages)
                        .onItem().transformToUni(this::publishOne)
                        .merge(10)
                        .collect().last()
                        .replaceWithVoid());
    }

    private Uni<Void> publishOne(
            tech.kayys.syirkah.document.adapter.outbound.postgres.DocumentOutboxMessage message
    ) {
        return publisher.publish(message)
                .ifNoItem().after(PUBLISH_TIMEOUT).fail()
                .chain(() -> repository.markPublished(message.id(), clock.now()))
                .onFailure().recoverWithUni(failure -> repository.recordFailure(
                        message.id(),
                        failure.getMessage(),
                        clock.now().plus(retryDelay(message.attemptCount() + 1)),
                        maximumAttempts
                ));
    }

    static Duration retryDelay(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        long seconds = 5;
        for (var count = 1; count < attempt && seconds < 300; count++) {
            seconds = Math.min(300, seconds * 3);
        }
        return Duration.ofSeconds(seconds);
    }
}

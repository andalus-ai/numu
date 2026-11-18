package tech.kayys.numou.feedback.runtime.quarkus;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.feedback.adapter.postgres.PostgresPublicInvitationRateLimitRepository;

import java.time.Clock;
import java.time.Duration;

/** Bounds the shared limiter table by pruning fixed windows older than 48 hours. */
@ApplicationScoped
public class PublicInvitationRateLimitCleanup {
    @Inject
    PostgresPublicInvitationRateLimitRepository repository;

    @Inject
    Clock clock;

    @Scheduled(every = "1h", delayed = "1h")
    void purgeExpiredWindows() {
        repository.deleteExpiredBefore(clock.instant().minus(Duration.ofHours(48)));
    }
}
package tech.kayys.numou.applicationplatform.application.installation;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationOutboxStore.ClaimedEvent;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallation;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallationId;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallationTransition;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationInstallationOutboxManagedWorkerTest {
    @Test
    void startsPollsAndStopsGracefully() throws Exception {
        var polls = new AtomicInteger();
        ApplicationInstallationOutboxStore outbox = emptyOutbox(polls);
        var dispatcher = dispatcher(outbox);
        var worker = new ApplicationInstallationOutboxManagedWorker(TenantId.generate(), "worker-1",
                dispatcher, Duration.ofMillis(10), Duration.ofSeconds(2));

        worker.start().await().indefinitely();
        Thread.sleep(80);
        worker.stop().await().indefinitely();

        assertThat(worker.isRunning()).isFalse();
        assertThat(polls.get()).isGreaterThan(0);
        assertThat(worker.lastCompletedAt()).isNotNull();
        assertThat(worker.lastFailureCode()).isNull();
    }

    @Test
    void validatesWorkerIdentityAndIntervals() {
        var dispatcher = dispatcher(emptyOutbox(new AtomicInteger()));
        assertThatThrownBy(() -> new ApplicationInstallationOutboxManagedWorker(
                TenantId.generate(), " ", dispatcher, Duration.ofSeconds(1), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationInstallationOutboxManagedWorker(
                TenantId.generate(), "worker", dispatcher, Duration.ZERO, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ApplicationInstallationOutboxDispatcher dispatcher(ApplicationInstallationOutboxStore outbox) {
        ApplicationInstallationTransitionStore installations = new ApplicationInstallationTransitionStore() {
            @Override public java.util.concurrent.CompletionStage<Optional<ApplicationInstallation>> find(
                    TenantId tenantId, ApplicationInstallationId id) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            @Override public java.util.concurrent.CompletionStage<Boolean> appendTransition(
                    ApplicationInstallation installation, ApplicationInstallationTransition transition,
                    long expectedRevision) {
                return CompletableFuture.completedFuture(false);
            }
        };
        return new ApplicationInstallationOutboxDispatcher(outbox, installations,
                (app, version) -> CompletableFuture.completedFuture(Optional.empty()),
                (tenant, pkg) -> CompletableFuture.completedFuture(null),
                command -> CompletableFuture.completedFuture(null),
                java.time.Clock.systemUTC(), Duration.ofSeconds(20), Duration.ofSeconds(1),
                Duration.ofSeconds(10), 10, 3);
    }

    private static ApplicationInstallationOutboxStore emptyOutbox(AtomicInteger polls) {
        return new ApplicationInstallationOutboxStore() {
            @Override public java.util.concurrent.CompletionStage<List<ClaimedEvent>> claimDue(
                    TenantId tenantId, String workerId, Instant now, Duration lease, int limit) {
                polls.incrementAndGet();
                return CompletableFuture.completedFuture(List.of());
            }
            @Override public java.util.concurrent.CompletionStage<Boolean> markDelivered(
                    TenantId tenantId, UUID eventId, String workerId, UUID leaseToken, Instant now) {
                return CompletableFuture.completedFuture(false);
            }
            @Override public java.util.concurrent.CompletionStage<RetryOutcome> retry(TenantId tenantId,
                    UUID eventId, String workerId, UUID leaseToken, String failureCode,
                    Instant retryAt, Instant now, int maxAttempts) {
                return CompletableFuture.completedFuture(RetryOutcome.LEASE_LOST);
            }
        };
    }
}

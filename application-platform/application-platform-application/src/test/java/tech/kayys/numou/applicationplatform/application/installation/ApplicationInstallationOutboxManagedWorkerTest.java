package tech.kayys.numou.applicationplatform.application.installation;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationOutboxStore.ClaimedEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationInstallationOutboxManagedWorkerTest {
    @Test
    void startsPollsAndStopsGracefully() throws Exception {
        var polls = new AtomicInteger();
        ApplicationInstallationOutboxStore emptyOutbox = new ApplicationInstallationOutboxStore() {
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
        var dispatcher = new ApplicationInstallationOutboxDispatcher(emptyOutbox,
                (tenantId, id) -> CompletableFuture.completedFuture(java.util.Optional.empty()),
                (app, version) -> CompletableFuture.completedFuture(java.util.Optional.empty()),
                (tenant, pkg) -> CompletableFuture.completedFuture(null),
                command -> CompletableFuture.completedFuture(null),
                java.time.Clock.systemUTC(), Duration.ofSeconds(20), Duration.ofSeconds(1),
                Duration.ofSeconds(10), 10, 3);
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
        var dispatcher = new ApplicationInstallationOutboxDispatcher(
                (tenant, worker, now, lease, limit) -> CompletableFuture.completedFuture(List.of()),
                (tenant, id) -> CompletableFuture.completedFuture(java.util.Optional.empty()),
                (app, version) -> CompletableFuture.completedFuture(java.util.Optional.empty()),
                (tenant, pkg) -> CompletableFuture.completedFuture(null),
                command -> CompletableFuture.completedFuture(null),
                java.time.Clock.systemUTC(), Duration.ofSeconds(20), Duration.ofSeconds(1),
                Duration.ofSeconds(10), 10, 3);
        assertThatThrownBy(() -> new ApplicationInstallationOutboxManagedWorker(
                TenantId.generate(), " ", dispatcher, Duration.ofSeconds(1), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationInstallationOutboxManagedWorker(
                TenantId.generate(), "worker", dispatcher, Duration.ZERO, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

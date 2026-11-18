package tech.kayys.numou.developer.apicatalog.application.cleanup;

import static org.assertj.core.api.Assertions.*;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactCleanupRepository;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactCleanupRepository.CleanupLease;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactObjectDeletionPort;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

class ApiArtifactCleanupDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
    private static final CleanupLease LEASE = new CleanupLease("00000000-0000-0000-0000-000000000001",
            "1.0.0", "s3://api-catalog/orders/1.0.0/" + "a".repeat(64) + ".openapi",
            "a".repeat(64), 0, NOW.plusSeconds(90));

    @Test
    void deletesOnlyAfterLeaseRecheckAndMarksTheArtifactDeleted() {
        FakeRepository repository = new FakeRepository();
        AtomicBoolean deleted = new AtomicBoolean();
        ApiArtifactObjectDeletionPort storage = (tenant, reference, sha) -> {
            deleted.set(true);
            return Uni.createFrom().voidItem();
        };
        ApiArtifactCleanupDispatcher dispatcher = new ApiArtifactCleanupDispatcher(repository, storage);

        int count = dispatcher.dispatchOnce(TenantId.generate(), "worker", NOW,
                Duration.ofSeconds(90), 10, 5).await().indefinitely();

        assertThat(count).isEqualTo(1);
        assertThat(deleted).isTrue();
        assertThat(repository.markedDeleted.get()).isTrue();
        assertThat(repository.claimOwner.get()).startsWith("worker:");
    }

    @Test
    void doesNotDeleteWhenLeaseWasLostBeforeTheStorageCall() {
        FakeRepository repository = new FakeRepository();
        repository.leaseCurrent.set(false);
        AtomicBoolean deleted = new AtomicBoolean();
        ApiArtifactObjectDeletionPort storage = (tenant, reference, sha) -> {
            deleted.set(true);
            return Uni.createFrom().voidItem();
        };

        int count = new ApiArtifactCleanupDispatcher(repository, storage)
                .dispatchOnce(TenantId.generate(), "worker", NOW, Duration.ofSeconds(90), 10, 5)
                .await().indefinitely();

        assertThat(count).isZero();
        assertThat(deleted).isFalse();
        assertThat(repository.markedDeleted.get()).isFalse();
    }

    @Test
    void recordsFailureAndDoesNotCountFailedDeletionAsComplete() {
        FakeRepository repository = new FakeRepository();
        ApiArtifactObjectDeletionPort storage = (tenant, reference, sha) ->
                Uni.createFrom().failure(new IllegalStateException("storage unavailable"));

        Instant beforeFailure = Instant.now();
        int count = new ApiArtifactCleanupDispatcher(repository, storage)
                .dispatchOnce(TenantId.generate(), "worker", NOW, Duration.ofSeconds(90), 10, 5)
                .await().indefinitely();

        assertThat(count).isZero();
        assertThat(repository.failureCode.get()).isEqualTo("IllegalStateException");
        assertThat(repository.retryAt.get()).isAfter(beforeFailure.plusSeconds(4))
                .isBefore(Instant.now().plusSeconds(6));
    }

    @Test
    void countsExpiredFailureLeaseAsLeaseLostRatherThanFailed() {
        FakeRepository repository = new FakeRepository();
        repository.failureRecorded.set(false);
        ApiArtifactObjectDeletionPort storage = (tenant, reference, sha) ->
                Uni.createFrom().failure(new IllegalStateException("late storage failure"));

        ApiArtifactCleanupDispatcher.DispatchResult result = new ApiArtifactCleanupDispatcher(repository, storage)
                .dispatchDetailedOnce(TenantId.generate(), "worker", NOW,
                        Duration.ofSeconds(90), 10, 5).await().indefinitely();

        assertThat(result.failed()).isZero();
        assertThat(result.leaseLost()).isEqualTo(1);
    }

    @Test
    void detailedDispatchReportsClaimsAndDeletedItems() {
        FakeRepository repository = new FakeRepository();
        ApiArtifactCleanupDispatcher.DispatchResult result = new ApiArtifactCleanupDispatcher(
                repository, (tenant, reference, sha) -> Uni.createFrom().voidItem())
                .dispatchDetailedOnce(TenantId.generate(), "worker", NOW,
                        Duration.ofSeconds(90), 10, 5).await().indefinitely();

        assertThat(result.claimed()).isEqualTo(1);
        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.leaseLost()).isZero();
    }

    @Test
    void exposesTenantScopedOperationalSnapshot() {
        FakeRepository repository = new FakeRepository();
        var snapshot = new ApiArtifactCleanupDispatcher(repository,
                (tenant, reference, sha) -> Uni.createFrom().voidItem())
                .snapshot(TenantId.generate(), NOW, 5).await().indefinitely();

        assertThat(snapshot.eligible()).isEqualTo(2);
        assertThat(snapshot.leased()).isEqualTo(1);
        assertThat(snapshot.terminalFailures()).isZero();
        assertThat(snapshot.tombstones()).isEqualTo(3);
        assertThat(snapshot.oldestDueAgeSeconds()).isEqualTo(15);
    }

    @Test
    void appliesBoundedExponentialRetryDelay() {
        assertThat(ApiArtifactCleanupDispatcher.retryDelay(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(ApiArtifactCleanupDispatcher.retryDelay(2)).isEqualTo(Duration.ofSeconds(15));
        assertThat(ApiArtifactCleanupDispatcher.retryDelay(3)).isEqualTo(Duration.ofSeconds(45));
        assertThat(ApiArtifactCleanupDispatcher.retryDelay(20)).isEqualTo(Duration.ofSeconds(3600));
    }

    private static final class FakeRepository implements ApiArtifactCleanupRepository {
        final AtomicReference<String> claimOwner = new AtomicReference<>();
        final AtomicBoolean leaseCurrent = new AtomicBoolean(true);
        final AtomicBoolean markedDeleted = new AtomicBoolean();
        final AtomicReference<String> failureCode = new AtomicReference<>();
        final AtomicReference<Instant> retryAt = new AtomicReference<>();
        final AtomicBoolean failureRecorded = new AtomicBoolean(true);

        @Override public Uni<List<CleanupLease>> claimDue(TenantId tenant, String workerId, Instant now,
                                                          Instant leaseUntil, int limit, int maximumAttempts) {
            claimOwner.set(workerId);
            return Uni.createFrom().item(List.of(LEASE));
        }
        @Override public Uni<Boolean> isLeaseCurrent(TenantId tenant, CleanupLease lease, String workerId,
                                                     Instant now, int maximumAttempts) {
            return Uni.createFrom().item(leaseCurrent.get());
        }
        @Override public Uni<Void> markDeleted(TenantId tenant, CleanupLease lease, String workerId, Instant at) {
            markedDeleted.set(true);
            return Uni.createFrom().voidItem();
        }
        @Override public Uni<Boolean> recordFailure(TenantId tenant, CleanupLease lease, String workerId,
                                                 String code, Instant failedAt, Instant retry, int maximumAttempts) {
            failureCode.set(code);
            retryAt.set(retry);
            return Uni.createFrom().item(failureRecorded.get());
        }

        @Override public Uni<ApiArtifactCleanupRepository.CleanupOperationalSnapshot> snapshot(
                TenantId tenant, Instant now, int maximumAttempts) {
            return Uni.createFrom().item(new ApiArtifactCleanupRepository.CleanupOperationalSnapshot(
                    2, 1, 0, 3, 15));
        }
    }
}

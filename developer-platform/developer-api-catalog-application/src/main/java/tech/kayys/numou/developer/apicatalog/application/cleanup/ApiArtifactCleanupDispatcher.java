package tech.kayys.numou.developer.apicatalog.application.cleanup;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactCleanupRepository;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactCleanupRepository.CleanupLease;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactCleanupRepository.CleanupOperationalSnapshot;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactObjectDeletionPort;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One-tenant cleanup dispatch. Hosts should create one worker per authorized tenant;
 * PostgreSQL SKIP LOCKED leases coordinate replicas for that tenant.
 */
public final class ApiArtifactCleanupDispatcher {
    private static final Duration DELETE_TIMEOUT = Duration.ofSeconds(30);
    private final ApiArtifactCleanupRepository repository;
    private final ApiArtifactObjectDeletionPort deletion;

    public ApiArtifactCleanupDispatcher(ApiArtifactCleanupRepository repository,
                                         ApiArtifactObjectDeletionPort deletion) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.deletion = Objects.requireNonNull(deletion, "deletion cannot be null");
    }

    /** Backwards-compatible count-only dispatch contract. */
    public Uni<Integer> dispatchOnce(TenantId tenant, String workerId, Instant now,
                                     Duration leaseDuration, int batchSize, int maximumAttempts) {
        return dispatchDetailedOnce(tenant, workerId, now, leaseDuration, batchSize, maximumAttempts)
                .map(DispatchResult::deleted);
    }

    public Uni<DispatchResult> dispatchDetailedOnce(TenantId tenant, String workerId, Instant now,
                                                     Duration leaseDuration, int batchSize, int maximumAttempts) {
        Objects.requireNonNull(tenant, "tenant cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        if (workerId == null || workerId.isBlank() || workerId.length() > 120
                || leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()
                || batchSize < 1 || batchSize > 200 || maximumAttempts < 1) {
            return Uni.createFrom().failure(new IllegalArgumentException("Invalid API artifact cleanup worker configuration"));
        }
        String leaseOwner = workerId.trim() + ":" + UUID.randomUUID();
        return repository.claimDue(tenant, leaseOwner, now, now.plus(leaseDuration), batchSize, maximumAttempts)
                .chain(leases -> process(tenant, leaseOwner, now, maximumAttempts, leases, 0, 0, 0)
                        .map(counts -> new DispatchResult(leases.size(), counts.deleted(),
                                counts.failed(), counts.leaseLost())));
    }

    public Uni<CleanupOperationalSnapshot> snapshot(TenantId tenant, Instant now, int maximumAttempts) {
        Objects.requireNonNull(tenant, "tenant cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        if (maximumAttempts < 1) return Uni.createFrom().failure(
                new IllegalArgumentException("maximumAttempts must be positive"));
        return repository.snapshot(tenant, now, maximumAttempts);
    }

    private Uni<Counts> process(TenantId tenant, String workerId, Instant now, int maximumAttempts,
                                List<CleanupLease> leases, int index, int deleted, int failed) {
        if (index >= leases.size()) {
            return Uni.createFrom().item(new Counts(deleted, failed, 0));
        }
        CleanupLease lease = leases.get(index);
        return processOne(tenant, workerId, now, maximumAttempts, lease)
                .chain(outcome -> process(tenant, workerId, now, maximumAttempts, leases, index + 1,
                        deleted + (outcome == Outcome.DELETED ? 1 : 0),
                        failed + (outcome == Outcome.FAILED ? 1 : 0))
                        .map(rest -> new Counts(rest.deleted(), rest.failed(),
                                rest.leaseLost() + (outcome == Outcome.LEASE_LOST ? 1 : 0))));
    }

    private Uni<Outcome> processOne(TenantId tenant, String workerId, Instant now,
                                    int maximumAttempts, CleanupLease lease) {
        return repository.isLeaseCurrent(tenant, lease, workerId, now, maximumAttempts)
                .flatMap(current -> {
                    if (!current) return Uni.createFrom().item(Outcome.LEASE_LOST);
                    return deletion.delete(tenant, lease.artifactReference(), lease.sha256())
                            .ifNoItem().after(DELETE_TIMEOUT).fail()
                            .chain(() -> repository.markDeleted(tenant, lease, workerId, Instant.now()))
                            .replaceWith(Outcome.DELETED);
                })
                .onFailure().recoverWithUni(failure ->
                        repository.recordFailure(tenant, lease, workerId, failureCode(failure),
                                now.plus(retryDelay(lease.attempts() + 1)), maximumAttempts)
                                .replaceWith(Outcome.FAILED));
    }

    static Duration retryDelay(int attempt) {
        if (attempt < 1) throw new IllegalArgumentException("attempt must be positive");
        long seconds = 5;
        for (int i = 1; i < attempt && seconds < 3600; i++) seconds = Math.min(3600, seconds * 3);
        return Duration.ofSeconds(seconds);
    }

    private static String failureCode(Throwable failure) {
        String type = failure.getClass().getSimpleName();
        return type.length() <= 120 ? type : type.substring(0, 120);
    }

    public record DispatchResult(int claimed, int deleted, int failed, int leaseLost) { }
    private record Counts(int deleted, int failed, int leaseLost) { }
    private enum Outcome { DELETED, FAILED, LEASE_LOST }
}

package tech.kayys.numou.developer.apicatalog.application.cleanup;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactCleanupRepository;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactCleanupRepository.CleanupLease;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactObjectDeletionPort;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

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

    public Uni<Integer> dispatchOnce(TenantId tenant, String workerId, Instant now,
                                     Duration leaseDuration, int batchSize, int maximumAttempts) {
        Objects.requireNonNull(tenant, "tenant cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        if (workerId == null || workerId.isBlank() || workerId.length() > 160
                || leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()
                || batchSize < 1 || batchSize > 200 || maximumAttempts < 1) {
            return Uni.createFrom().failure(new IllegalArgumentException("Invalid API artifact cleanup worker configuration"));
        }
        return repository.claimDue(tenant, workerId, now, now.plus(leaseDuration), batchSize, maximumAttempts)
                .chain(leases -> process(tenant, workerId, now, maximumAttempts, leases, 0));
    }

    private Uni<Integer> process(TenantId tenant, String workerId, Instant now, int maximumAttempts,
                                 List<CleanupLease> leases, int completed) {
        if (leases.isEmpty()) return Uni.createFrom().item(completed);
        CleanupLease lease = leases.get(0);
        List<CleanupLease> remaining = leases.subList(1, leases.size());
        return processOne(tenant, workerId, now, maximumAttempts, lease)
                .chain(deleted -> process(tenant, workerId, now, maximumAttempts, remaining,
                        completed + (deleted ? 1 : 0)));
    }

    private Uni<Boolean> processOne(TenantId tenant, String workerId, Instant now,
                                    int maximumAttempts, CleanupLease lease) {
        return repository.isLeaseCurrent(tenant, lease, workerId, now, maximumAttempts)
                .flatMap(current -> {
                    if (!current) return Uni.createFrom().item(false);
                    return deletion.delete(tenant, lease.artifactReference(), lease.sha256())
                            .ifNoItem().after(DELETE_TIMEOUT).fail()
                            .chain(() -> repository.markDeleted(tenant, lease, workerId, now))
                            .replaceWith(true);
                })
                .onFailure().recoverWithUni(failure ->
                        repository.recordFailure(tenant, lease, workerId, failureCode(failure),
                                now.plus(retryDelay(lease.attempts() + 1)), maximumAttempts)
                                .replaceWith(false));
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
}

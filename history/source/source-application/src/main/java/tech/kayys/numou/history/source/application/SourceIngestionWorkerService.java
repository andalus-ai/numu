package tech.kayys.numou.history.source.application;

import tech.kayys.numou.history.source.domain.SourceIngestionLease;
import tech.kayys.numou.history.source.spi.SourceIngestionDispatchRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Authorized application boundary for claiming and advancing durable ingestion work. */
public final class SourceIngestionWorkerService {
    private static final Duration MIN_LEASE = Duration.ofSeconds(1);
    private static final Duration MAX_LEASE = Duration.ofMinutes(30);

    private final SourceIngestionDispatchRepository dispatch;
    private final HistoricalSourceTenantContext tenantContext;
    private final SourceIngestionWorkerAuthorizationPort authorization;
    private final Clock clock;

    public SourceIngestionWorkerService(
            SourceIngestionDispatchRepository dispatch,
            HistoricalSourceTenantContext tenantContext,
            SourceIngestionWorkerAuthorizationPort authorization,
            Clock clock) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<SourceIngestionLease> claimNext(String workerRef, Duration leaseDuration) {
        UUID tenant = tenant();
        String worker = required(workerRef);
        Objects.requireNonNull(leaseDuration, "leaseDuration");
        if (leaseDuration.compareTo(MIN_LEASE) < 0 || leaseDuration.compareTo(MAX_LEASE) > 0) {
            throw new IllegalArgumentException("leaseDuration must be between 1 second and 30 minutes");
        }
        authorization.requireCanClaim(tenant, worker);
        var now = clock.instant();
        return dispatch.claimNext(tenant, worker, now, now.plus(leaseDuration));
    }

    public boolean complete(UUID ingestionId, String workerRef, UUID leaseToken) {
        UUID tenant = tenant();
        String worker = authorizeTransition(tenant, ingestionId, workerRef);
        return dispatch.complete(tenant, ingestionId, worker, Objects.requireNonNull(leaseToken), clock.instant());
    }

    public boolean scheduleRetry(UUID ingestionId, String workerRef, UUID leaseToken,
                                 java.time.Instant availableAt, String error) {
        UUID tenant = tenant();
        String worker = authorizeTransition(tenant, ingestionId, workerRef);
        var now = clock.instant();
        return dispatch.scheduleRetry(tenant, ingestionId, worker, Objects.requireNonNull(leaseToken),
                Objects.requireNonNull(availableAt), now, error);
    }

    public boolean deadLetter(UUID ingestionId, String workerRef, UUID leaseToken, String error) {
        UUID tenant = tenant();
        String worker = authorizeTransition(tenant, ingestionId, workerRef);
        return dispatch.deadLetter(tenant, ingestionId, worker, Objects.requireNonNull(leaseToken),
                clock.instant(), error);
    }

    private String authorizeTransition(UUID tenant, UUID ingestionId, String workerRef) {
        Objects.requireNonNull(ingestionId, "ingestionId");
        String worker = required(workerRef);
        authorization.requireCanTransition(tenant, ingestionId, worker);
        return worker;
    }

    private UUID tenant() {
        return Objects.requireNonNull(tenantContext.requireTenantId(), "tenant context returned null");
    }

    private static String required(String workerRef) {
        if (workerRef == null || workerRef.isBlank()) throw new IllegalArgumentException("workerRef is required");
        String normalized = workerRef.trim();
        if (normalized.length() > 256) throw new IllegalArgumentException("workerRef exceeds 256 characters");
        return normalized;
    }
}

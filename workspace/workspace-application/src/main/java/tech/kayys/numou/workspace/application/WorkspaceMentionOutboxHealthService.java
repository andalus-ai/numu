package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxAdminPolicy;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxHealthStore;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/** Authorized tenant-scoped operational health query for mention delivery. */
public final class WorkspaceMentionOutboxHealthService {
    private final WorkspaceMentionOutboxHealthStore store;
    private final WorkspaceMentionOutboxAdminPolicy policy;
    private final Clock clock;

    public WorkspaceMentionOutboxHealthService(WorkspaceMentionOutboxHealthStore store,
            WorkspaceMentionOutboxAdminPolicy policy, Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.policy = Objects.requireNonNull(policy);
        this.clock = Objects.requireNonNull(clock);
    }

    public HealthSnapshot snapshot(UUID tenantId, UUID actorId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorId, "actorId");
        policy.requireManage(tenantId, actorId);
        var now = clock.instant();
        var snapshot = store.snapshot(tenantId, now);
        Duration oldestPendingAge = snapshot.oldestPendingAt() == null
                ? Duration.ZERO : nonNegative(Duration.between(snapshot.oldestPendingAt(), now));
        return new HealthSnapshot(snapshot.pending(), snapshot.due(), snapshot.leased(),
                snapshot.expiredLeases(), snapshot.dead(), oldestPendingAge, now);
    }

    private static Duration nonNegative(Duration value) {
        return value.isNegative() ? Duration.ZERO : value;
    }

    public record HealthSnapshot(long pending, long due, long leased, long expiredLeases,
                                 long dead, Duration oldestPendingAge, java.time.Instant capturedAt) { }
}

package tech.kayys.numou.queuemanagement.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/**
 * A queue-owned lifecycle around a source reference. It deliberately does not
 * mutate the referenced source record; application handlers coordinate that via ports.
 */
public final class QueueEntry {
    public static final int MIN_PRIORITY = 0;
    public static final int MAX_PRIORITY = 100;

    private final QueueEntryId id;
    private final TenantId tenantId;
    private final QueueId queueId;
    private final QueueSourceReference source;
    private final Instant enteredAt;
    private final int priority;
    private QueueEntryStatus status;
    private Instant updatedAt;
    private Instant calledAt;
    private Instant servingStartedAt;
    private Instant completedAt;
    private int callCount;

    private QueueEntry(QueueEntryId id, TenantId tenantId, QueueId queueId,
                       QueueSourceReference source, int priority, Instant enteredAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.queueId = Objects.requireNonNull(queueId, "queueId must not be null");
        this.source = Objects.requireNonNull(source, "source must not be null");
        if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) {
            throw new IllegalArgumentException("priority must be between 0 and 100");
        }
        this.priority = priority;
        this.enteredAt = Objects.requireNonNull(enteredAt, "enteredAt must not be null");
        this.updatedAt = enteredAt;
        this.status = QueueEntryStatus.WAITING;
    }

    public static QueueEntry join(QueueEntryId id, TenantId tenantId, QueueId queueId,
                                  QueueSourceReference source, int priority, Instant enteredAt) {
        return new QueueEntry(id, tenantId, queueId, source, priority, enteredAt);
    }

    public void call(Instant now) {
        requireStatus(QueueEntryStatus.WAITING);
        Instant at = monotonic(now);
        calledAt = at;
        updatedAt = at;
        callCount++;
        status = QueueEntryStatus.CALLED;
    }

    public void startServing(Instant now) {
        requireStatus(QueueEntryStatus.CALLED);
        Instant at = monotonic(now);
        servingStartedAt = at;
        updatedAt = at;
        status = QueueEntryStatus.SERVING;
    }

    public void complete(Instant now) {
        requireStatus(QueueEntryStatus.SERVING);
        Instant at = monotonic(now);
        completedAt = at;
        updatedAt = at;
        status = QueueEntryStatus.COMPLETED;
    }

    public void markNoShow(Instant now) {
        requireStatus(QueueEntryStatus.CALLED);
        updatedAt = monotonic(now);
        status = QueueEntryStatus.NO_SHOW;
    }

    public void abandon(Instant now) {
        if (status != QueueEntryStatus.WAITING && status != QueueEntryStatus.CALLED) {
            throw new IllegalStateException("Only waiting or called entries can be abandoned");
        }
        updatedAt = monotonic(now);
        status = QueueEntryStatus.ABANDONED;
    }

    public void transfer(Instant now) {
        if (status != QueueEntryStatus.WAITING && status != QueueEntryStatus.CALLED) {
            throw new IllegalStateException("Only waiting or called entries can be transferred");
        }
        updatedAt = monotonic(now);
        status = QueueEntryStatus.TRANSFERRED;
    }

    public void requeue(Instant now) {
        if (status != QueueEntryStatus.CALLED && status != QueueEntryStatus.NO_SHOW) {
            throw new IllegalStateException("Only called or no-show entries can be requeued");
        }
        updatedAt = monotonic(now);
        calledAt = null;
        servingStartedAt = null;
        completedAt = null;
        status = QueueEntryStatus.WAITING;
    }

    private void requireStatus(QueueEntryStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected status " + expected + " but was " + status);
        }
    }

    private Instant monotonic(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("transition time cannot be before the previous update");
        }
        return now;
    }

    public QueueEntryId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public QueueId queueId() { return queueId; }
    public QueueSourceReference source() { return source; }
    public Instant enteredAt() { return enteredAt; }
    public int priority() { return priority; }
    public QueueEntryStatus status() { return status; }
    public Instant updatedAt() { return updatedAt; }
    public Instant calledAt() { return calledAt; }
    public Instant servingStartedAt() { return servingStartedAt; }
    public Instant completedAt() { return completedAt; }
    public int callCount() { return callCount; }
    public boolean isTerminal() {
        return status == QueueEntryStatus.COMPLETED || status == QueueEntryStatus.NO_SHOW
                || status == QueueEntryStatus.ABANDONED || status == QueueEntryStatus.TRANSFERRED;
    }
}

package tech.kayys.numou.queuemanagement.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/**
 * Queue-owned lifecycle around a source reference. Source records and their
 * business outcomes remain owned by their originating capabilities.
 */
public final class QueueEntry {
    public static final int MIN_PRIORITY = 0;
    public static final int MAX_PRIORITY = 100;

    private final QueueEntryId id;
    private final TenantId tenantId;
    private final QueueId queueId;
    private final QueueProfile profile;
    private final QueueSourceReference source;
    private final Instant enteredAt;
    private final int priority;
    private QueueEntryStatus status;
    private Instant updatedAt;
    private Instant calledAt;
    private Instant servingStartedAt;
    private Instant completedAt;
    private int callCount;

    private QueueEntry(QueueEntryId id, QueueDefinition queue, QueueSourceReference source,
                       int priority, Instant enteredAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(queue, "queue must not be null");
        if (!queue.acceptsEntries()) {
            throw new IllegalStateException("Only active queues accept new entries");
        }
        this.tenantId = queue.tenantId();
        this.queueId = queue.id();
        this.profile = queue.profile();
        this.source = Objects.requireNonNull(source, "source must not be null");
        if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) {
            throw new IllegalArgumentException("priority must be between 0 and 100");
        }
        this.priority = priority;
        this.enteredAt = Objects.requireNonNull(enteredAt, "enteredAt must not be null");
        this.updatedAt = enteredAt;
        this.status = QueueEntryStatus.WAITING;
    }

    public static QueueEntry join(QueueEntryId id, QueueDefinition queue,
                                  QueueSourceReference source, int priority, Instant enteredAt) {
        return new QueueEntry(id, queue, source, priority, enteredAt);
    }

    public void call(Instant now) {
        requireProfile(QueueProfile.WAITING_SERVICE);
        requireStatus(QueueEntryStatus.WAITING);
        Instant at = monotonic(now);
        calledAt = at;
        updatedAt = at;
        callCount++;
        status = QueueEntryStatus.CALLED;
    }

    public void startServing(Instant now) {
        requireProfile(QueueProfile.WAITING_SERVICE);
        requireStatus(QueueEntryStatus.CALLED);
        Instant at = monotonic(now);
        servingStartedAt = at;
        updatedAt = at;
        status = QueueEntryStatus.SERVING;
    }

    public void complete(Instant now) {
        requireProfile(QueueProfile.WAITING_SERVICE);
        requireStatus(QueueEntryStatus.SERVING);
        finish(now);
    }

    /** Offer a dispatch item to an assignee; assignment identity belongs in the application layer. */
    public void offer(Instant now) {
        requireProfile(QueueProfile.DISPATCH);
        requireStatus(QueueEntryStatus.WAITING);
        updatedAt = monotonic(now);
        status = QueueEntryStatus.OFFERED;
    }

    public void acceptOffer(Instant now) {
        requireProfile(QueueProfile.DISPATCH);
        requireStatus(QueueEntryStatus.OFFERED);
        updatedAt = monotonic(now);
        status = QueueEntryStatus.ACCEPTED;
    }

    public void rejectOffer(Instant now) {
        requireProfile(QueueProfile.DISPATCH);
        requireStatus(QueueEntryStatus.OFFERED);
        updatedAt = monotonic(now);
        status = QueueEntryStatus.REJECTED;
    }

    public void completeDispatch(Instant now) {
        requireProfile(QueueProfile.DISPATCH);
        requireStatus(QueueEntryStatus.ACCEPTED);
        finish(now);
    }

    public void markNoShow(Instant now) {
        requireProfile(QueueProfile.WAITING_SERVICE);
        requireStatus(QueueEntryStatus.CALLED);
        updatedAt = monotonic(now);
        status = QueueEntryStatus.NO_SHOW;
    }

    public void abandon(Instant now) {
        if (status != QueueEntryStatus.WAITING && status != QueueEntryStatus.CALLED
                && status != QueueEntryStatus.OFFERED && status != QueueEntryStatus.REJECTED) {
            throw new IllegalStateException("Only unserved or unaccepted entries can be abandoned");
        }
        updatedAt = monotonic(now);
        status = QueueEntryStatus.ABANDONED;
    }

    public void transfer(Instant now) {
        if (status != QueueEntryStatus.WAITING && status != QueueEntryStatus.CALLED
                && status != QueueEntryStatus.OFFERED && status != QueueEntryStatus.REJECTED) {
            throw new IllegalStateException("Only unserved or unaccepted entries can be transferred");
        }
        updatedAt = monotonic(now);
        status = QueueEntryStatus.TRANSFERRED;
    }

    public void requeue(Instant now) {
        if (status != QueueEntryStatus.CALLED && status != QueueEntryStatus.NO_SHOW
                && status != QueueEntryStatus.OFFERED && status != QueueEntryStatus.REJECTED) {
            throw new IllegalStateException("Only called, no-show, offered, or rejected entries can be requeued");
        }
        updatedAt = monotonic(now);
        calledAt = null;
        servingStartedAt = null;
        completedAt = null;
        status = QueueEntryStatus.WAITING;
    }

    private void finish(Instant now) {
        Instant at = monotonic(now);
        completedAt = at;
        updatedAt = at;
        status = QueueEntryStatus.COMPLETED;
    }

    private void requireProfile(QueueProfile expected) {
        if (profile != expected) {
            throw new IllegalStateException("Operation requires queue profile " + expected + " but was " + profile);
        }
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
    public QueueProfile profile() { return profile; }
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
        return status == QueueEntryStatus.COMPLETED || status == QueueEntryStatus.ABANDONED
                || status == QueueEntryStatus.TRANSFERRED;
    }
}

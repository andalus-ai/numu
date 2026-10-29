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

    /**
     * Reconstitutes persisted state without replaying lifecycle transitions.
     *
     * Persistence adapters must still scope the row lookup by tenant and queue. This
     * factory validates intrinsic state invariants but does not authorize access to it.
     */
    public static QueueEntry reconstitute(QueueEntryId id, TenantId tenantId, QueueId queueId,
                                          QueueProfile profile, QueueSourceReference source,
                                          int priority, Instant enteredAt, Instant updatedAt,
                                          QueueEntryStatus status, Instant calledAt,
                                          Instant servingStartedAt, Instant completedAt,
                                          int callCount) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(queueId, "queueId must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(enteredAt, "enteredAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) {
            throw new IllegalArgumentException("priority must be between 0 and 100");
        }
        if (updatedAt.isBefore(enteredAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before enteredAt");
        }
        if (callCount < 0) {
            throw new IllegalArgumentException("callCount cannot be negative");
        }
        requireTimestampNotAfter(calledAt, updatedAt, "calledAt");
        requireTimestampNotAfter(servingStartedAt, updatedAt, "servingStartedAt");
        requireTimestampNotAfter(completedAt, updatedAt, "completedAt");
        if (calledAt != null && calledAt.isBefore(enteredAt)
                || servingStartedAt != null && servingStartedAt.isBefore(enteredAt)
                || completedAt != null && completedAt.isBefore(enteredAt)) {
            throw new IllegalArgumentException("lifecycle timestamps cannot be before enteredAt");
        }
        if ((status == QueueEntryStatus.CALLED || status == QueueEntryStatus.SERVING
                || status == QueueEntryStatus.NO_SHOW) && profile != QueueProfile.WAITING_SERVICE) {
            throw new IllegalArgumentException("status " + status + " is only valid for WAITING_SERVICE");
        }
        if ((status == QueueEntryStatus.OFFERED || status == QueueEntryStatus.ACCEPTED
                || status == QueueEntryStatus.REJECTED) && profile != QueueProfile.DISPATCH) {
            throw new IllegalArgumentException("status " + status + " is only valid for DISPATCH");
        }
        if ((status == QueueEntryStatus.CALLED || status == QueueEntryStatus.SERVING
                || status == QueueEntryStatus.NO_SHOW) && calledAt == null) {
            throw new IllegalArgumentException("status " + status + " requires calledAt");
        }
        if (status == QueueEntryStatus.SERVING && servingStartedAt == null) {
            throw new IllegalArgumentException("SERVING requires servingStartedAt");
        }
        if (status == QueueEntryStatus.COMPLETED && completedAt == null) {
            throw new IllegalArgumentException("COMPLETED requires completedAt");
        }
        if (status == QueueEntryStatus.WAITING && (servingStartedAt != null || completedAt != null)) {
            throw new IllegalArgumentException("WAITING cannot retain serving/completion timestamps");
        }
        QueueEntry entry = new QueueEntry(id, tenantId, queueId, profile, source, priority,
                enteredAt, updatedAt, status, calledAt, servingStartedAt, completedAt, callCount);
        return entry;
    }

    private QueueEntry(QueueEntryId id, TenantId tenantId, QueueId queueId, QueueProfile profile,
                       QueueSourceReference source, int priority, Instant enteredAt, Instant updatedAt,
                       QueueEntryStatus status, Instant calledAt, Instant servingStartedAt,
                       Instant completedAt, int callCount) {
        this.id = id;
        this.tenantId = tenantId;
        this.queueId = queueId;
        this.profile = profile;
        this.source = source;
        this.priority = priority;
        this.enteredAt = enteredAt;
        this.updatedAt = updatedAt;
        this.status = status;
        this.calledAt = calledAt;
        this.servingStartedAt = servingStartedAt;
        this.completedAt = completedAt;
        this.callCount = callCount;
    }

    private static void requireTimestampNotAfter(Instant timestamp, Instant upperBound, String field) {
        if (timestamp != null && timestamp.isAfter(upperBound)) {
            throw new IllegalArgumentException(field + " cannot be after updatedAt");
        }
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

    /** Mark queue coordination complete after the source capability reports completion. */
    public void resolveFromSource(Instant now) {
        requireProfile(QueueProfile.OPERATIONAL_WORK);
        requireStatus(QueueEntryStatus.WAITING);
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

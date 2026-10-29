package tech.kayys.numou.queuemanagement.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Locale;
import java.util.Objects;

/** Tenant-owned queue configuration; no persistence or transport dependencies. */
public final class QueueDefinition {
    private final QueueId id;
    private final TenantId tenantId;
    private final String code;
    private String name;
    private final QueueProfile profile;
    private QueueOrderingPolicy orderingPolicy;
    private QueueStatus status;

    private QueueDefinition(QueueId id, TenantId tenantId, String code, String name,
                            QueueProfile profile, QueueOrderingPolicy orderingPolicy) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.code = normalizeCode(code);
        this.name = requiredName(name);
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
        this.orderingPolicy = Objects.requireNonNull(orderingPolicy, "orderingPolicy must not be null");
        this.status = QueueStatus.ACTIVE;
    }

    public static QueueDefinition create(QueueId id, TenantId tenantId, String code, String name,
                                         QueueProfile profile, QueueOrderingPolicy orderingPolicy) {
        return new QueueDefinition(id, tenantId, code, name, profile, orderingPolicy);
    }

    /**
     * Reconstitutes persisted configuration, including its lifecycle status.
     * Tenant authorization and row scoping remain responsibilities of the adapter.
     */
    public static QueueDefinition reconstitute(QueueId id, TenantId tenantId, String code, String name,
                                               QueueProfile profile, QueueOrderingPolicy orderingPolicy,
                                               QueueStatus status) {
        QueueDefinition queue = new QueueDefinition(id, tenantId, code, name, profile, orderingPolicy);
        queue.status = Objects.requireNonNull(status, "status must not be null");
        return queue;
    }

    public void rename(String name) {
        ensureNotClosed();
        this.name = requiredName(name);
    }

    public void changeOrderingPolicy(QueueOrderingPolicy policy) {
        ensureNotClosed();
        this.orderingPolicy = Objects.requireNonNull(policy, "policy must not be null");
    }

    public void pause() {
        if (status != QueueStatus.ACTIVE) {
            throw new IllegalStateException("Only an active queue can be paused");
        }
        status = QueueStatus.PAUSED;
    }

    public void resume() {
        if (status != QueueStatus.PAUSED) {
            throw new IllegalStateException("Only a paused queue can be resumed");
        }
        status = QueueStatus.ACTIVE;
    }

    public void close() {
        if (status == QueueStatus.CLOSED) {
            throw new IllegalStateException("Queue is already closed");
        }
        status = QueueStatus.CLOSED;
    }

    public QueueId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public String code() { return code; }
    public String name() { return name; }
    public QueueProfile profile() { return profile; }
    public QueueOrderingPolicy orderingPolicy() { return orderingPolicy; }
    public QueueStatus status() { return status; }
    public boolean acceptsEntries() { return status == QueueStatus.ACTIVE; }

    private void ensureNotClosed() {
        if (status == QueueStatus.CLOSED) {
            throw new IllegalStateException("Closed queue cannot be changed");
        }
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9][A-Z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("code must be 1-64 characters using letters, digits, dot, underscore, or hyphen");
        }
        return normalized;
    }

    private static String requiredName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        String normalized = name.trim();
        if (normalized.length() > 120) {
            throw new IllegalArgumentException("name exceeds 120 characters");
        }
        return normalized;
    }
}

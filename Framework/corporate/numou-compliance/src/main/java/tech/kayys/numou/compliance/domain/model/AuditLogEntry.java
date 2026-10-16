package tech.kayys.syirkah.compliance.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.compliance.domain.identifier.AuditLogId;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.compliance.domain.valueobject.AuditAction;

import java.time.Instant;

/**
 * Audit log entry aggregate root.
 * Records all auditable actions in the system.
 */
public final class AuditLogEntry extends AbstractAggregateRoot<AuditLogId> {
    
    private static final long serialVersionUID = 1L;
    
    private TenantId tenantId;
    private String userId;
    private String username;
    private String userRole;
    private String sessionId;
    private String clientIp;
    private String userAgent;
    private AuditAction action;
    private String entityType;
    private String entityId;
    private String entityName;
    private String oldValue;
    private String newValue;
    private String additionalData;
    private String notes;
    private boolean successful;
    private String failureReason;
    private Instant timestamp;
    private boolean immutable;

    private AuditLogEntry(AuditLogId id) {
        super(id);
        this.timestamp = Instant.now();
        this.immutable = true;
        this.successful = true;
    }

    private AuditLogEntry() {
        super();
    }

    /**
     * Factory method to create a new audit log entry.
     */
    public static AuditLogEntry create(
            AuditLogId id,
            TenantId tenantId,
            String userId,
            String username,
            AuditAction action,
            String entityType,
            String entityId) {
        AuditLogEntry entry = new AuditLogEntry(id);
        entry.tenantId = tenantId;
        entry.userId = userId;
        entry.username = username;
        entry.action = action;
        entry.entityType = entityType;
        entry.entityId = entityId;
        return entry;
    }

    /**
     * Sets the session details.
     */
    public void setSessionDetails(String sessionId, String clientIp, String userAgent) {
        if (immutable) {
            throw new IllegalStateException("Audit log entry is immutable");
        }
        this.sessionId = sessionId;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets the entity name.
     */
    public void setEntityName(String entityName) {
        if (immutable) {
            throw new IllegalStateException("Audit log entry is immutable");
        }
        this.entityName = entityName;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets the user role.
     */
    public void setUserRole(String userRole) {
        if (immutable) {
            throw new IllegalStateException("Audit log entry is immutable");
        }
        this.userRole = userRole;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets the old and new values.
     */
    public void setValues(String oldValue, String newValue) {
        if (immutable) {
            throw new IllegalStateException("Audit log entry is immutable");
        }
        this.oldValue = oldValue;
        this.newValue = newValue;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets additional data.
     */
    public void setAdditionalData(String additionalData) {
        if (immutable) {
            throw new IllegalStateException("Audit log entry is immutable");
        }
        this.additionalData = additionalData;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets the result of the action.
     */
    public void setResult(boolean successful, String failureReason) {
        if (immutable) {
            throw new IllegalStateException("Audit log entry is immutable");
        }
        this.successful = successful;
        this.failureReason = failureReason;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets notes.
     */
    public void setNotes(String notes) {
        if (immutable) {
            throw new IllegalStateException("Audit log entry is immutable");
        }
        this.notes = notes;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Locks the audit entry to prevent further modifications.
     */
    public void lock() {
        this.immutable = true;
    }

    // Getters
    public TenantId getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getUserRole() { return userRole; }
    public String getSessionId() { return sessionId; }
    public String getClientIp() { return clientIp; }
    public String getUserAgent() { return userAgent; }
    public AuditAction getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getEntityName() { return entityName; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public String getAdditionalData() { return additionalData; }
    public String getNotes() { return notes; }
    public boolean isSuccessful() { return successful; }
    public String getFailureReason() { return failureReason; }
    public Instant getTimestamp() { return timestamp; }
    public boolean isImmutable() { return immutable; }

    @Override
    public String toString() {
        return "AuditLogEntry{" +
                "id=" + getId() +
                ", userId='" + userId + '\'' +
                ", action=" + action +
                ", entityType='" + entityType + '\'' +
                ", entityId='" + entityId + '\'' +
                ", timestamp=" + timestamp +
                ", successful=" + successful +
                '}';
    }

    /**
     * Builder for AuditLogEntry.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AuditLogId id;
        private TenantId tenantId;
        private String userId;
        private String username;
        private AuditAction action;
        private String entityType;
        private String entityId;
        private String entityName;
        private String sessionId;
        private String clientIp;
        private String userAgent;
        private String userRole;
        private String oldValue;
        private String newValue;
        private String additionalData;
        private String notes;
        private boolean successful = true;
        private String failureReason;

        public Builder id(AuditLogId id) {
            this.id = id;
            return this;
        }

        public Builder tenantId(TenantId tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder action(AuditAction action) {
            this.action = action;
            return this;
        }

        public Builder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder entityName(String entityName) {
            this.entityName = entityName;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder clientIp(String clientIp) {
            this.clientIp = clientIp;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder userRole(String userRole) {
            this.userRole = userRole;
            return this;
        }

        public Builder oldValue(String oldValue) {
            this.oldValue = oldValue;
            return this;
        }

        public Builder newValue(String newValue) {
            this.newValue = newValue;
            return this;
        }

        public Builder additionalData(String additionalData) {
            this.additionalData = additionalData;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder successful(boolean successful) {
            this.successful = successful;
            return this;
        }

        public Builder failureReason(String failureReason) {
            this.failureReason = failureReason;
            return this;
        }

        public AuditLogEntry build() {
            if (id == null) {
                id = AuditLogId.generate();
            }
            AuditLogEntry entry = create(id, tenantId, userId, username, action, entityType, entityId);
            entry.entityName = entityName;
            entry.sessionId = sessionId;
            entry.clientIp = clientIp;
            entry.userAgent = userAgent;
            entry.userRole = userRole;
            entry.oldValue = oldValue;
            entry.newValue = newValue;
            entry.additionalData = additionalData;
            entry.notes = notes;
            entry.successful = successful;
            entry.failureReason = failureReason;
            return entry;
        }
    }
}

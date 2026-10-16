package tech.kayys.syirkah.security.domain.audit.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.security.domain.audit.identifier.AuditTrailId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Audit trail aggregate root.
 * Centralized audit logging for all modules.
 */
public final class AuditTrail extends AbstractAggregateRoot<AuditTrailId> {
    
    private static final long serialVersionUID = 1L;
    
    private String module;
    private String entityType;
    private String entityId;
    private String action;
    private String userId;
    private String userName;
    private String tenantId;
    private String companyId;
    private String clientIp;
    private String userAgent;
    private String sessionId;
    private String oldValue;
    private String newValue;
    private String changes;
    private String notes;
    private Instant timestamp;
    private boolean immutable;

    private AuditTrail(AuditTrailId id) {
        super(id);
        this.timestamp = Instant.now();
        this.immutable = true;
    }

    private AuditTrail() {
        super();
    }

    /**
     * Factory method to create a new audit trail entry.
     */
    public static AuditTrail create(
            AuditTrailId id,
            String module,
            String entityType,
            String entityId,
            String action,
            String userId,
            String userName) {
        AuditTrail audit = new AuditTrail(id);
        audit.module = module;
        audit.entityType = entityType;
        audit.entityId = entityId;
        audit.action = action;
        audit.userId = userId;
        audit.userName = userName;
        return audit;
    }

    /**
     * Sets the context information.
     */
    public void setContext(String tenantId, String companyId, String clientIp, String userAgent, String sessionId) {
        if (immutable) {
            throw new IllegalStateException("Audit trail entry is immutable");
        }
        this.tenantId = tenantId;
        this.companyId = companyId;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.sessionId = sessionId;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets the value changes.
     */
    public void setChanges(String oldValue, String newValue, String changes) {
        if (immutable) {
            throw new IllegalStateException("Audit trail entry is immutable");
        }
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changes = changes;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets notes.
     */
    public void setNotes(String notes) {
        if (immutable) {
            throw new IllegalStateException("Audit trail entry is immutable");
        }
        this.notes = notes;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    // Getters
    public String getModule() { return module; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getAction() { return action; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getTenantId() { return tenantId; }
    public String getCompanyId() { return companyId; }
    public String getClientIp() { return clientIp; }
    public String getUserAgent() { return userAgent; }
    public String getSessionId() { return sessionId; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public String getChanges() { return changes; }
    public String getNotes() { return notes; }
    public Instant getTimestamp() { return timestamp; }
    public boolean isImmutable() { return immutable; }

    @Override
    public String toString() {
        return "AuditTrail{" +
                "id=" + getId() +
                ", module='" + module + '\'' +
                ", entityType='" + entityType + '\'' +
                ", entityId='" + entityId + '\'' +
                ", action='" + action + '\'' +
                ", userId='" + userId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
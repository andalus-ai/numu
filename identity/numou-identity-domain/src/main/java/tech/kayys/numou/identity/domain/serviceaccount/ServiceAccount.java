package tech.kayys.numou.identity.domain.serviceaccount;

import tech.kayys.numou.foundation.domain.audit.AuditMeta;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Machine identity aggregate root for system-to-system integrations.
 */
public final class ServiceAccount extends AbstractAggregateRoot<ServiceAccountId> {

    /** Actor recorded when there is no owning user (e.g. a platform/system account). */
    static final String SYSTEM_ACTOR = "system";

    private final ServiceAccountId id;
    private final TenantId tenantId;
    private final String name;
    private final String description;
    private final UserId ownerUserId;
    private boolean enabled;
    private final Instant createdAt;
    private AuditMeta auditMeta;

    private ServiceAccount(
            ServiceAccountId id,
            TenantId tenantId,
            String name,
            String description,
            UserId ownerUserId,
            boolean enabled,
            Instant createdAt,
            AuditMeta auditMeta
    ) {
        super(Objects.requireNonNull(id, "id cannot be null"));
        this.id = id;
        this.tenantId = tenantId;
        this.name = requireText(name, "name");
        if (description != null && description.length() > 255) {
            throw new IllegalArgumentException("description cannot exceed 255 characters");
        }
        this.description = description;
        this.ownerUserId = ownerUserId;
        this.enabled = enabled;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.auditMeta = auditMeta != null ? auditMeta : AuditMeta.initial(
                ownerUserId != null ? ownerUserId.value().toString() : SYSTEM_ACTOR,
                createdAt
        );
    }

    public static ServiceAccount create(
            ServiceAccountId id,
            TenantId tenantId,
            String name,
            String description,
            UserId ownerUserId,
            Instant occurredAt
    ) {
        var account = new ServiceAccount(
                id,
                tenantId,
                name,
                description,
                ownerUserId,
                true,
                occurredAt,
                null
        );

        account.raise(new ServiceAccountCreated(
                UUID.randomUUID(),
                occurredAt,
                id,
                name,
                ownerUserId
        ));

        return account;
    }

    public static ServiceAccount reconstitute(
            ServiceAccountId id,
            TenantId tenantId,
            String name,
            String description,
            UserId ownerUserId,
            boolean enabled,
            Instant createdAt
    ) {
        return reconstitute(id, tenantId, name, description, ownerUserId, enabled, createdAt, null);
    }

    /**
     * @param auditMeta persisted audit metadata; {@code null} falls back to values derived
     *                   from the owning user and creation timestamp (pre-migration rows).
     */
    public static ServiceAccount reconstitute(
            ServiceAccountId id,
            TenantId tenantId,
            String name,
            String description,
            UserId ownerUserId,
            boolean enabled,
            Instant createdAt,
            AuditMeta auditMeta
    ) {
        return new ServiceAccount(
                id,
                tenantId,
                name,
                description,
                ownerUserId,
                enabled,
                createdAt,
                auditMeta
        );
    }

    public void disable(Instant occurredAt) {
        disable(auditMeta.updatedBy(), occurredAt);
    }

    /**
     * Disables the account and records who did it in the audit metadata.
     *
     * @param actor     actor identifier (user id or system name); {@code null} keeps the previous actor
     * @param occurredAt transition timestamp
     */
    public void disable(String actor, Instant occurredAt) {
        if (!enabled) {
            return;
        }
        this.enabled = false;
        this.auditMeta = new AuditMeta(
                auditMeta.createdBy(),
                auditMeta.createdAt(),
                actor != null && !actor.isBlank() ? actor : auditMeta.updatedBy(),
                Objects.requireNonNull(occurredAt, "occurredAt cannot be null")
        );
        raise(new ServiceAccountDisabled(
                UUID.randomUUID(),
                occurredAt,
                id
        ));
    }

    @Override
    public ServiceAccountId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public UserId ownerUserId() {
        return ownerUserId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Standard audit metadata ({@code createdBy/createdAt/updatedBy/updatedAt}) for this account.
     */
    public AuditMeta auditMeta() {
        return auditMeta;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }
}

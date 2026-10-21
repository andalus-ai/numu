package tech.kayys.numou.identity.domain.role.assignment;

import tech.kayys.numou.foundation.domain.audit.AuditMeta;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.identity.domain.role.RoleId;
import tech.kayys.numou.identity.domain.user.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Binds a principal (User or ServiceAccount) to a Role at a specific hierarchical scope.
 */
public final class RoleAssignment extends AbstractAggregateRoot<RoleAssignmentId> {

    /** Actor recorded when the granting principal is anonymous (e.g. a system/seed grant). */
    static final String SYSTEM_ACTOR = "system";

    private final RoleAssignmentId id;
    private final String principalType;
    private final UUID principalId;
    private final RoleId roleId;
    private final RoleScopeType scopeType;
    private final String scopePath;
    private final UserId grantedByUserId;
    private final Instant grantedAt;
    private final Instant expiresAt;
    private final AuditMeta auditMeta;

    private RoleAssignment(
            RoleAssignmentId id,
            String principalType,
            UUID principalId,
            RoleId roleId,
            RoleScopeType scopeType,
            String scopePath,
            UserId grantedByUserId,
            Instant grantedAt,
            Instant expiresAt
    ) {
        super(Objects.requireNonNull(id, "id cannot be null"));
        this.id = id;
        this.principalType = Objects.requireNonNull(principalType, "principalType cannot be null");
        this.principalId = Objects.requireNonNull(principalId, "principalId cannot be null");
        this.roleId = Objects.requireNonNull(roleId, "roleId cannot be null");
        this.scopeType = Objects.requireNonNull(scopeType, "scopeType cannot be null");
        this.scopePath = requireText(scopePath, "scopePath");
        this.grantedByUserId = grantedByUserId;
        this.grantedAt = Objects.requireNonNull(grantedAt, "grantedAt cannot be null");
        this.expiresAt = expiresAt;
        // A grant is immutable (revocation deletes the row and is captured by the
        // RoleAssignmentChanged audit event), so updated* mirrors created*. The
        // persisted audit columns are granted_by_user_id / granted_at.
        this.auditMeta = AuditMeta.initial(
                grantedByUserId != null ? grantedByUserId.value().toString() : SYSTEM_ACTOR,
                grantedAt
        );
    }

    public static RoleAssignment grant(
            RoleAssignmentId id,
            String principalType,
            UUID principalId,
            RoleId roleId,
            RoleScopeType scopeType,
            String scopePath,
            UserId grantedByUserId,
            Instant expiresAt,
            Instant occurredAt
    ) {
        var assignment = new RoleAssignment(
                id,
                principalType,
                principalId,
                roleId,
                scopeType,
                scopePath,
                grantedByUserId,
                occurredAt,
                expiresAt
        );

        assignment.raise(new RoleAssignmentChanged(
                UUID.randomUUID(),
                occurredAt,
                id,
                principalType,
                principalId,
                roleId,
                scopePath,
                grantedByUserId,
                true
        ));

        return assignment;
    }

    public static RoleAssignment reconstitute(
            RoleAssignmentId id,
            String principalType,
            UUID principalId,
            RoleId roleId,
            RoleScopeType scopeType,
            String scopePath,
            UserId grantedByUserId,
            Instant grantedAt,
            Instant expiresAt
    ) {
        return new RoleAssignment(
                id,
                principalType,
                principalId,
                roleId,
                scopeType,
                scopePath,
                grantedByUserId,
                grantedAt,
                expiresAt
        );
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }

    @Override
    public RoleAssignmentId id() {
        return id;
    }

    public String principalType() {
        return principalType;
    }

    public UUID principalId() {
        return principalId;
    }

    public RoleId roleId() {
        return roleId;
    }

    public RoleScopeType scopeType() {
        return scopeType;
    }

    public String scopePath() {
        return scopePath;
    }

    public UserId grantedByUserId() {
        return grantedByUserId;
    }

    public Instant grantedAt() {
        return grantedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    /**
     * Standard audit metadata ({@code createdBy/createdAt/updatedBy/updatedAt}) for this grant.
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

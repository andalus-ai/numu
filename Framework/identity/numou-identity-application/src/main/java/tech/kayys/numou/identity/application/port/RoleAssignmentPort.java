package tech.kayys.syirkah.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.identity.domain.role.RoleId;
import tech.kayys.syirkah.identity.domain.user.UserId;

import java.time.Instant;
import java.util.Set;

public interface RoleAssignmentPort {
    Uni<Boolean> isAssigned(TenantId tenantId, UserId userId, RoleId roleId);

    Uni<Set<RoleId>> rolesFor(TenantId tenantId, UserId userId);

    Uni<Void> assign(
            TenantId tenantId,
            UserId userId,
            RoleId roleId,
            UserId assignedBy,
            Instant assignedAt
    );

    /**
     * Must enforce last-owner protection atomically in the database transaction.
     */
    Uni<Void> remove(TenantId tenantId, UserId userId, RoleId roleId, UserId removedBy, Instant removedAt);
}

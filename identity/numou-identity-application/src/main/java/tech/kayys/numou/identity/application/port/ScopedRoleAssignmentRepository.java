package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.identity.domain.role.RoleId;
import tech.kayys.numou.identity.domain.role.assignment.RoleAssignment;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ScopedRoleAssignmentRepository {

    /**
     * Resolves role IDs applicable to the principal at the given requested path or any ancestor path.
     */
    Uni<List<RoleId>> findApplicableRoleIds(String principalType, UUID principalId, String requestedPath);

    Uni<List<RoleAssignment>> findByPrincipal(String principalType, UUID principalId);

    /**
     * Reverse lookup for SCIM Group membership: every principal holding the role at the scope.
     *
     * @param roleId    role id
     * @param scopePath exact scope path (no ancestor expansion - membership is assignment, not grant)
     */
    Uni<List<RoleAssignment>> findHolders(String principalType, RoleId roleId, String scopePath);

    Uni<Void> save(RoleAssignment assignment);

    Uni<Long> deleteByPrincipalAndRole(String principalType, UUID principalId, RoleId roleId, String scopePath);

    /**
     * Revokes every scoped role assignment held by a principal (used when a SCIM user is
     * deactivated / deprovisioned). Returns the number of rows deleted.
     */
    Uni<Long> deleteByPrincipal(String principalType, UUID principalId);

    /**
     * Finds permission codes associated with the given roles.
     */
    Uni<Set<String>> findPermissionCodesForRoles(List<RoleId> roleIds);
}

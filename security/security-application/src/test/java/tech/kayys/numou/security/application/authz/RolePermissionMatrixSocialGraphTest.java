package tech.kayys.numou.security.application.authz;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.security.domain.authorization.Permission;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolePermissionMatrixSocialGraphTest {
    @Test
    void socialGraphMemberReceivesOnlyTheExplicitSocialGraphCommandGrants() {
        var permissions = RolePermissionMatrix.socialGraphDefaults()
                .permissionsFor(Set.of("social-graph-member"));

        assertTrue(permissions.contains(Permission.of("social-graph", "create")));
        assertTrue(permissions.contains(Permission.of("social-graph", "perform")));
        assertEquals(2, permissions.size());
    }

    @Test
    void unknownRoleReceivesNoSocialGraphGrants() {
        assertTrue(RolePermissionMatrix.socialGraphDefaults()
                .permissionsFor(Set.of("unrecognized-role")).isEmpty());
    }
}

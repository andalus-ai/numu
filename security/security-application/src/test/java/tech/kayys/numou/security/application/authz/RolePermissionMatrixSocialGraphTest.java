package tech.kayys.numou.security.application.authz;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.security.domain.authorization.Permission;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionMatrixSocialGraphTest {
    @Test
    void socialGraphMemberReceivesOnlyTheExplicitSocialGraphCommandGrants() {
        var permissions = RolePermissionMatrix.socialGraphDefaults()
                .permissionsFor(Set.of("social-graph-member"));

        assertThat(permissions).contains(
                Permission.of("social-graph", "create"),
                Permission.of("social-graph", "perform"));
        assertThat(permissions).hasSize(2);
    }

    @Test
    void unknownRoleReceivesNoSocialGraphGrants() {
        assertThat(RolePermissionMatrix.socialGraphDefaults()
                .permissionsFor(Set.of("unrecognized-role"))).isEmpty();
    }
}

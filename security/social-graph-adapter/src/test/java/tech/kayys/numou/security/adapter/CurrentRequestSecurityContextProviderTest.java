package tech.kayys.numou.security.adapter;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.security.application.authz.RolePermissionMatrix;
import tech.kayys.numou.security.application.context.SecurityContext;
import tech.kayys.numou.security.domain.authorization.Permission;
import tech.kayys.numou.security.domain.authorization.Principal;
import tech.kayys.numou.security.domain.identifier.PrincipalId;
import tech.kayys.numou.security.socialgraph.CurrentRequestSecurityContextProvider;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentRequestSecurityContextProviderTest {
    @Test
    void mapsVerifiedRequestTenantAndRoleGrantsIntoCanonicalContext() {
        TenantId tenant = TenantId.generate();
        CurrentSecurity current = new CurrentSecurity();
        current.set(new SecurityContext(
                Principal.of(PrincipalId.generate(), "member", tenant),
                tenant.value(),
                Set.of("social-graph-member"),
                "127.0.0.1"));

        var matrix = RolePermissionMatrix.restaurantDefaults().merge(RolePermissionMatrix.socialGraphDefaults());
        var provider = new CurrentRequestSecurityContextProvider(current, matrix);

        var context = provider.current().orElseThrow();
        assertThat(context.tenantId()).isEqualTo(tenant);
        assertThat(context.tenantMember()).isTrue();
        assertThat(context.authenticated()).isTrue();
        assertThat(context.hasPermission(Permission.of("social-graph", "create"))).isTrue();
        assertThat(context.hasPermission(Permission.of("social-graph", "perform"))).isTrue();
    }

    @Test
    void returnsEmptyWhenRequestSecurityFilterHasNotEstablishedContext() {
        var provider = new CurrentRequestSecurityContextProvider(
                new CurrentSecurity(), RolePermissionMatrix.socialGraphDefaults());
        assertThat(provider.current()).isEmpty();
    }
}

package tech.kayys.syirkah.identity.adapter.outbound.security;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.identity.application.port.MembershipManagementPort;
import tech.kayys.syirkah.identity.domain.user.UserId;
import tech.kayys.syirkah.security.domain.authorization.Principal;
import tech.kayys.syirkah.security.domain.identifier.PrincipalId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdentityTenantMembershipAdapterTest {

    @Test
    void shouldReturnTrueWhenMembershipIsActive() {
        var userUuid = UUID.randomUUID();
        var tenantId = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(userUuid), "alice", tenantId);

        MembershipManagementPort membershipPort = (tenant, user) -> {
            if (tenant.equals(tenantId) && user.equals(UserId.of(userUuid))) {
                return Uni.createFrom().item(true);
            }
            return Uni.createFrom().item(false);
        };

        var adapter = new IdentityTenantMembershipAdapter(membershipPort);

        var isMember = adapter.isMember(principal, tenantId).await().indefinitely();
        assertTrue(isMember);
    }

    @Test
    void shouldReturnFalseWhenMembershipIsRevokedOrInactive() {
        var userUuid = UUID.randomUUID();
        var tenantA = TenantId.of(UUID.randomUUID());
        var tenantB = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(userUuid), "alice", tenantA);

        MembershipManagementPort membershipPort = (tenant, user) -> Uni.createFrom().item(false);
        var adapter = new IdentityTenantMembershipAdapter(membershipPort);

        var isMember = adapter.isMember(principal, tenantB).await().indefinitely();
        assertFalse(isMember);
    }

    @Test
    void shouldReturnFalseForNullPrincipalOrTenant() {
        var tenantId = TenantId.of(UUID.randomUUID());
        MembershipManagementPort membershipPort = (t, u) -> Uni.createFrom().item(true);
        var adapter = new IdentityTenantMembershipAdapter(membershipPort);

        assertFalse(adapter.isMember(null, tenantId).await().indefinitely());
        assertFalse(adapter.isMember(Principal.of(PrincipalId.of(UUID.randomUUID()), "bob", tenantId), null).await().indefinitely());
    }
}

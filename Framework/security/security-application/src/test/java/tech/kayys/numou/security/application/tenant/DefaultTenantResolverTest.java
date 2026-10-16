package tech.kayys.syirkah.security.application.tenant;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.security.domain.authentication.TenantResolutionException;
import tech.kayys.syirkah.security.domain.authorization.Principal;
import tech.kayys.syirkah.security.domain.identifier.PrincipalId;
import tech.kayys.syirkah.security.spi.port.TenantMembershipResolver;
import tech.kayys.syirkah.security.spi.port.TenantResolutionRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DefaultTenantResolverTest {

    @Test
    void shouldResolveWhenPrincipalIsMember() {
        var tenantId = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(UUID.randomUUID()), "bob", tenantId);
        var request = new TenantResolutionRequest(principal, tenantId.value().toString());

        var resolver = new DefaultTenantResolver();
        var resolved = resolver.resolve(request).await().indefinitely();

        assertEquals(tenantId, resolved);
    }

    @Test
    void shouldFailWhenRequestedTenantIsBlank() {
        var tenantId = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(UUID.randomUUID()), "bob", tenantId);
        var request = new TenantResolutionRequest(principal, "   ");

        var resolver = new DefaultTenantResolver();

        var ex = assertThrows(TenantResolutionException.class, () ->
                resolver.resolve(request).await().indefinitely()
        );
        assertEquals("tenant.required", ex.code());
    }

    @Test
    void shouldFailWhenPrincipalIsNotMember() {
        var memberTenant = TenantId.of(UUID.randomUUID());
        var foreignTenant = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(UUID.randomUUID()), "bob", memberTenant);
        var request = new TenantResolutionRequest(principal, foreignTenant.value().toString());

        var resolver = new DefaultTenantResolver();

        var ex = assertThrows(TenantResolutionException.class, () ->
                resolver.resolve(request).await().indefinitely()
        );
        assertEquals("tenant.access", ex.code());
    }

    @Test
    void shouldDelegateToAuthoritativeMembershipResolver() {
        var tenantId = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(UUID.randomUUID()), "bob", tenantId);
        var request = new TenantResolutionRequest(principal, tenantId.value().toString());

        TenantMembershipResolver memberResolver = (p, t) -> Uni.createFrom().item(true);
        var resolver = new DefaultTenantResolver(memberResolver);

        var resolved = resolver.resolve(request).await().indefinitely();
        assertEquals(tenantId, resolved);

        TenantMembershipResolver revokedResolver = (p, t) -> Uni.createFrom().item(false);
        var resolverRevoked = new DefaultTenantResolver(revokedResolver);

        var ex = assertThrows(TenantResolutionException.class, () ->
                resolverRevoked.resolve(request).await().indefinitely()
        );
        assertEquals("tenant.access", ex.code());
    }
}

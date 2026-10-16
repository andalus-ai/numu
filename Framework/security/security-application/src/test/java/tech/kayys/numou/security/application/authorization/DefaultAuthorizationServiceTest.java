package tech.kayys.syirkah.security.application.authorization;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.security.domain.authorization.AccessDecision;
import tech.kayys.syirkah.security.domain.authorization.AccessRequest;
import tech.kayys.syirkah.security.domain.authorization.Permission;
import tech.kayys.syirkah.security.domain.authorization.Principal;
import tech.kayys.syirkah.security.domain.identifier.PrincipalId;
import tech.kayys.syirkah.security.domain.valueobject.ActionType;
import tech.kayys.syirkah.security.domain.valueobject.Decision;
import tech.kayys.syirkah.security.spi.port.PolicyPort;
import tech.kayys.syirkah.security.spi.port.ResourceAuthorizationPort;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAuthorizationServiceTest {

    @Test
    void shouldAllowWhenPrincipalHasPermission() {
        var tenantId = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(UUID.randomUUID()), "alice", tenantId);
        var request = AccessRequest.of(principal, tenantId, ActionType.READ, "order");

        PolicyPort policyPort = (p, t) -> Uni.createFrom().item(Set.of(Permission.of("order", "read")));
        var authService = new DefaultAuthorizationService(policyPort);

        var decision = authService.authorize(request).await().indefinitely();

        assertEquals(Decision.ALLOW, decision.decision());
    }

    @Test
    void shouldDenyWhenPrincipalLacksPermission() {
        var tenantId = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(UUID.randomUUID()), "alice", tenantId);
        var request = AccessRequest.of(principal, tenantId, ActionType.DELETE, "order");

        PolicyPort policyPort = (p, t) -> Uni.createFrom().item(Set.of(Permission.of("order", "read")));
        var authService = new DefaultAuthorizationService(policyPort);

        var decision = authService.authorize(request).await().indefinitely();

        assertEquals(Decision.DENY, decision.decision());
    }

    @Test
    void shouldDenyWhenPrincipalIsNotMemberOfTenant() {
        var memberTenant = TenantId.of(UUID.randomUUID());
        var foreignTenant = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(UUID.randomUUID()), "alice", memberTenant);
        var request = AccessRequest.of(principal, foreignTenant, ActionType.READ, "order");

        PolicyPort policyPort = (p, t) -> Uni.createFrom().item(Set.of(Permission.of("order", "read")));
        var authService = new DefaultAuthorizationService(policyPort);

        var decision = authService.authorize(request).await().indefinitely();

        assertEquals(Decision.DENY, decision.decision());
    }

    @Test
    void shouldEvaluateAbacWhenRbacAllows() {
        var tenantId = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(UUID.randomUUID()), "alice", tenantId);
        var request = AccessRequest.of(principal, tenantId, ActionType.READ, "order");

        PolicyPort policyPort = (p, t) -> Uni.createFrom().item(Set.of(Permission.of("order", "read")));
        ResourceAuthorizationPort abacPort = req -> Uni.createFrom().item(AccessDecision.deny("Order is locked"));

        var authService = new DefaultAuthorizationService(policyPort, abacPort);

        var decision = authService.authorize(request).await().indefinitely();

        assertEquals(Decision.DENY, decision.decision());
        assertTrue(decision.reason().contains("Order is locked"));
    }

    @Test
    void shouldNotEvaluateAbacWhenRbacDenies() {
        var tenantId = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(UUID.randomUUID()), "alice", tenantId);
        var request = AccessRequest.of(principal, tenantId, ActionType.DELETE, "order");

        PolicyPort policyPort = (p, t) -> Uni.createFrom().item(Set.of(Permission.of("order", "read")));
        var abacCalled = new AtomicBoolean(false);
        ResourceAuthorizationPort abacPort = req -> {
            abacCalled.set(true);
            return Uni.createFrom().item(AccessDecision.allow("allowed"));
        };

        var authService = new DefaultAuthorizationService(policyPort, abacPort);

        var decision = authService.authorize(request).await().indefinitely();

        assertEquals(Decision.DENY, decision.decision());
        assertFalse(abacCalled.get());
    }
}

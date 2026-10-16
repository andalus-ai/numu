package tech.kayys.syirkah.security.domain.authorization;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.security.domain.identifier.PrincipalId;
import tech.kayys.syirkah.security.domain.valueobject.ActionType;
import tech.kayys.syirkah.security.domain.valueobject.Decision;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PermissionEvaluatorTest {

    @Test
    void shouldAllowWhenPrincipalIsMemberAndHasPermission() {
        var tenantId = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(UUID.randomUUID()), "dave", tenantId);

        var evaluator = PermissionEvaluator.with(Set.of(Permission.of(ActionType.READ, "order")));
        var request = AccessRequest.of(principal, tenantId, ActionType.READ, "order");

        var decision = evaluator.decide(request);

        assertEquals(Decision.ALLOW, decision.decision());
    }

    @Test
    void shouldDenyWhenPrincipalIsNotMemberOfTenant() {
        var memberTenant = TenantId.of(UUID.randomUUID());
        var foreignTenant = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(UUID.randomUUID()), "dave", memberTenant);

        var evaluator = PermissionEvaluator.with(Set.of(Permission.of(ActionType.READ, "order")));
        var request = AccessRequest.of(principal, foreignTenant, ActionType.READ, "order");

        var decision = evaluator.decide(request);

        assertEquals(Decision.DENY, decision.decision());
        assertTrue(decision.reason().contains("not a member of tenant"));
    }

    @Test
    void shouldDenyWhenPermissionIsNotGranted() {
        var tenantId = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(UUID.randomUUID()), "dave", tenantId);

        var evaluator = PermissionEvaluator.with(Set.of(Permission.of(ActionType.READ, "order")));
        var request = AccessRequest.of(principal, tenantId, ActionType.DELETE, "order");

        var decision = evaluator.decide(request);

        assertEquals(Decision.DENY, decision.decision());
        assertTrue(decision.reason().contains("not granted"));
    }

    @Test
    void shouldDenyCleanlyWhenGrantsAreEmptyWithoutThrowing() {
        var tenantId = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(UUID.randomUUID()), "dave", tenantId);

        var evaluator = PermissionEvaluator.with(Set.of());
        var request = AccessRequest.of(principal, tenantId, ActionType.READ, "order");

        var decision = evaluator.decide(request);

        assertEquals(Decision.DENY, decision.decision());
        assertTrue(decision.reason().contains("not granted"));

        var nullEvaluator = PermissionEvaluator.with(null);
        var nullDecision = nullEvaluator.decide(request);
        assertEquals(Decision.DENY, nullDecision.decision());
    }

    @Test
    void shouldEnforceScopedResourceIds() {
        var tenantId = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(UUID.randomUUID()), "dave", tenantId);

        var evaluator = new PermissionEvaluator(
                Set.of(Permission.of(ActionType.READ, "order")),
                Set.of("order-100", "order-200")
        );

        var reqAllowed = new AccessRequest(principal, tenantId, ActionType.READ, "order", "order-100", "");
        var reqDenied = new AccessRequest(principal, tenantId, ActionType.READ, "order", "order-300", "");

        assertEquals(Decision.ALLOW, evaluator.decide(reqAllowed).decision());
        assertEquals(Decision.DENY, evaluator.decide(reqDenied).decision());
    }

    @Test
    void shouldSupportCanonicalStringFormat() {
        var perm = Permission.of("Product", "READ");
        assertEquals("product", perm.resource());
        assertEquals("read", perm.action());
        assertEquals("product.read", perm.name());
        assertEquals("product", perm.resourceType());
    }
}

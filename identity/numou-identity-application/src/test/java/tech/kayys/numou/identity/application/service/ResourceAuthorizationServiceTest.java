package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.identity.application.port.AuthorizationPolicyRepository;
import tech.kayys.numou.identity.application.port.AuthorizationPort;
import tech.kayys.numou.identity.application.port.ResourceAttributePort;
import tech.kayys.numou.identity.application.security.AuthorizationRequirement;
import tech.kayys.numou.identity.application.security.Principal;
import tech.kayys.numou.identity.application.security.PrincipalType;
import tech.kayys.numou.identity.application.security.abac.AllOf;
import tech.kayys.numou.identity.application.security.abac.AttributeNamespace;
import tech.kayys.numou.identity.application.security.abac.AttributeReference;
import tech.kayys.numou.identity.application.security.abac.AuthorizationContext;
import tech.kayys.numou.identity.application.security.abac.AuthorizationPolicy;
import tech.kayys.numou.identity.application.security.abac.Comparison;
import tech.kayys.numou.identity.application.security.abac.ComparisonOperator;
import tech.kayys.numou.identity.application.security.abac.PolicyEffect;
import tech.kayys.numou.identity.application.security.abac.PolicyEvaluator;
import tech.kayys.numou.identity.application.service.ResourceAuthorizationService.AbacDefaultEffect;
import tech.kayys.numou.identity.domain.user.UserId;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RBAC-first / ABAC-overlay orchestration in
 * {@link ResourceAuthorizationService}.
 *
 * <p>Stubs are built inline — no framework, no mocking library.
 */
class ResourceAuthorizationServiceTest {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final AuthorizationRequirement REQ =
            AuthorizationRequirement.permission("document", "read");

    private static final Principal AUTHED_USER = new Principal(
            new UserId(UUID.randomUUID()),
            "sub-alice", "alice", "Alice",
            Set.of(), Map.of("department", "engineering"),
            PrincipalType.USER);

    private static final AuthorizationPolicy ALLOW_POLICY = policy(PolicyEffect.ALLOW);
    private static final AuthorizationPolicy DENY_POLICY  = policy(PolicyEffect.DENY);

    // ---- factory helpers ----

    private static AuthorizationPolicy policy(PolicyEffect effect) {
        return new AuthorizationPolicy(
                UUID.randomUUID(), TENANT, REQ.permissionName(),
                effect,
                // trivially true condition: subject.department equals subject.department
                new Comparison(
                        new AttributeReference(AttributeNamespace.SUBJECT, "department"),
                        ComparisonOperator.EQUALS,
                        "engineering"),
                true, 1L);
    }

    private static AuthorizationContext ctx() {
        return new AuthorizationContext(
                TENANT, REQ.permissionName(),
                Map.of("department", "engineering"),
                Map.of(),
                Map.of());
    }

    // ---- stubs ----

    /** Stubbed RBAC port — returns a fixed boolean. */
    private static AuthorizationPort rbac(boolean allows) {
        return (principal, tenantId, requirement) -> Uni.createFrom().item(allows);
    }

    /** Counts how many times it was called (to assert short-circuit). */
    private static class CountingAttributePort implements ResourceAttributePort {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Uni<AuthorizationContext> resolve(
                Principal principal, String tenantId,
                AuthorizationRequirement requirement, String resourceId) {
            calls.incrementAndGet();
            return Uni.createFrom().item(ctx());
        }
    }

    /** Counts how many times it was called (to assert short-circuit). */
    private static class CountingPolicyRepository implements AuthorizationPolicyRepository {
        final AtomicInteger calls = new AtomicInteger();
        final List<AuthorizationPolicy> policies;

        CountingPolicyRepository(List<AuthorizationPolicy> policies) {
            this.policies = policies;
        }

        @Override
        public Uni<List<AuthorizationPolicy>> findActive(String tenantId, String permission) {
            calls.incrementAndGet();
            return Uni.createFrom().item(policies);
        }
    }

    // ---- test groups ----

    @Nested
    @DisplayName("Guard conditions")
    class GuardConditions {

        @Test
        void nullPrincipal_denies() {
            var svc = service(rbac(true), new CountingAttributePort(), List.of());
            assertFalse(svc.isAllowed(null, TENANT, REQ, "res-1").await().indefinitely());
        }

        @Test
        void unauthenticatedPrincipal_denies() {
            // A principal with no userId is authenticated only if it's a SERVICE type;
            // a USER without userId is unauthenticated.
            var anon = new Principal(null, "anon", "anon", "Anon",
                    Set.of(), Map.of(), PrincipalType.SERVICE);
            // SERVICE with blank subject is unauthenticated
            var blank = new Principal(null, " ", "anon", "Anon",
                    Set.of(), Map.of(), PrincipalType.SERVICE);
            var svc = service(rbac(true), new CountingAttributePort(), List.of());
            assertFalse(svc.isAllowed(blank, TENANT, REQ, "res-1").await().indefinitely());
        }

        @Test
        void blankTenantId_denies() {
            var svc = service(rbac(true), new CountingAttributePort(), List.of());
            assertFalse(svc.isAllowed(AUTHED_USER, "  ", REQ, "res-1").await().indefinitely());
        }
    }

    @Nested
    @DisplayName("RBAC short-circuit")
    class RbacShortCircuit {

        @Test
        void rbac_denied_returns_false_without_touching_abac() {
            var attrPort = new CountingAttributePort();
            var policyRepo = new CountingPolicyRepository(List.of(ALLOW_POLICY));
            var svc = service(rbac(false), attrPort, List.of());

            boolean result = svc.isAllowed(AUTHED_USER, TENANT, REQ, "res-1")
                    .await().indefinitely();

            assertFalse(result, "RBAC denial must propagate");
            assertEquals(0, attrPort.calls.get(), "attribute port must NOT be called when RBAC denies");
            assertEquals(0, policyRepo.calls.get(), "policy repo must NOT be called when RBAC denies");
        }
    }

    @Nested
    @DisplayName("ABAC overlay — ALLOW policies")
    class AbacAllow {

        @Test
        void matching_allow_policy_allows() {
            var svc = service(rbac(true), new CountingAttributePort(), List.of(ALLOW_POLICY));
            assertTrue(svc.isAllowed(AUTHED_USER, TENANT, REQ, "res-1").await().indefinitely());
        }

        @Test
        void no_matching_policies_uses_allow_default() {
            var svc = serviceWithDefault(rbac(true), AbacDefaultEffect.ALLOW, List.of());
            assertTrue(svc.isAllowed(AUTHED_USER, TENANT, REQ, "res-1").await().indefinitely(),
                    "Empty policy list + ALLOW default must allow");
        }

        @Test
        void no_matching_policies_uses_deny_default() {
            var svc = serviceWithDefault(rbac(true), AbacDefaultEffect.DENY, List.of());
            assertFalse(svc.isAllowed(AUTHED_USER, TENANT, REQ, "res-1").await().indefinitely(),
                    "Empty policy list + DENY default must deny");
        }
    }

    @Nested
    @DisplayName("ABAC overlay — DENY policies")
    class AbacDeny {

        @Test
        void deny_policy_overrides_allow_policy() {
            var svc = service(rbac(true), new CountingAttributePort(),
                    List.of(ALLOW_POLICY, DENY_POLICY));
            assertFalse(svc.isAllowed(AUTHED_USER, TENANT, REQ, "res-1").await().indefinitely(),
                    "Any matching DENY must override matching ALLOW");
        }

        @Test
        void deny_policy_alone_denies() {
            var svc = service(rbac(true), new CountingAttributePort(), List.of(DENY_POLICY));
            assertFalse(svc.isAllowed(AUTHED_USER, TENANT, REQ, "res-1").await().indefinitely());
        }
    }

    @Nested
    @DisplayName("ABAC context integrity check")
    class ContextIntegrity {

        @Test
        void context_tenant_mismatch_denies() {
            // Attribute port returns a context for a different tenant — must be rejected.
            ResourceAttributePort wrongTenant = (p, t, r, id) ->
                    Uni.createFrom().item(new AuthorizationContext(
                            "other-tenant", r.permissionName(), Map.of(), Map.of(), Map.of()));
            var svc = service(rbac(true), wrongTenant, List.of(ALLOW_POLICY));
            assertFalse(svc.isAllowed(AUTHED_USER, TENANT, REQ, "res-1").await().indefinitely(),
                    "Context with wrong tenantId must be rejected");
        }

        @Test
        void context_permission_mismatch_denies() {
            ResourceAttributePort wrongPermission = (p, t, r, id) ->
                    Uni.createFrom().item(new AuthorizationContext(
                            TENANT, "other.action", Map.of(), Map.of(), Map.of()));
            var svc = service(rbac(true), wrongPermission, List.of(ALLOW_POLICY));
            assertFalse(svc.isAllowed(AUTHED_USER, TENANT, REQ, "res-1").await().indefinitely(),
                    "Context with wrong permission must be rejected");
        }
    }

    @Nested
    @DisplayName("Non-matching ABAC condition")
    class NonMatchingCondition {

        @Test
        void policy_condition_not_satisfied_falls_through_to_default() {
            // Policy requires department = "finance" but the user is in "engineering".
            var strictPolicy = new AuthorizationPolicy(
                    UUID.randomUUID(), TENANT, REQ.permissionName(),
                    PolicyEffect.ALLOW,
                    new Comparison(
                            new AttributeReference(AttributeNamespace.SUBJECT, "department"),
                            ComparisonOperator.EQUALS,
                            "finance"),
                    true, 1L);
            // With ALLOW default: no condition matched → default fires → allow
            var svcAllow = serviceWithDefault(rbac(true), AbacDefaultEffect.ALLOW, List.of(strictPolicy));
            assertFalse(svcAllow.isAllowed(AUTHED_USER, TENANT, REQ, "res-1").await().indefinitely(),
                    "A policy exists but none match — result is false (no ALLOW matched, no DENY)");
        }
    }

    // ---- helpers ----

    private static ResourceAuthorizationService service(
            AuthorizationPort rbac,
            ResourceAttributePort attrPort,
            List<AuthorizationPolicy> activePolicies) {
        return new ResourceAuthorizationService(
                rbac, attrPort,
                (tenantId, permission) -> Uni.createFrom().item(activePolicies),
                new PolicyEvaluator());
    }

    private static ResourceAuthorizationService serviceWithDefault(
            AuthorizationPort rbac,
            AbacDefaultEffect defaultEffect,
            List<AuthorizationPolicy> activePolicies) {
        return new ResourceAuthorizationService(
                rbac,
                (p, t, r, id) -> Uni.createFrom().item(ctx()),
                (tenantId, permission) -> Uni.createFrom().item(activePolicies),
                new PolicyEvaluator(),
                defaultEffect);
    }
}

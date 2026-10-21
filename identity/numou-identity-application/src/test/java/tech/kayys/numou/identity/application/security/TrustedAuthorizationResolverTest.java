package tech.kayys.numou.identity.application.security;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustedAuthorizationResolverTest {
    private final UUID principal = UUID.randomUUID();
    private final UUID tenant = UUID.randomUUID();
    private final UUID workspace = UUID.randomUUID();
    private final String action = "catalog.dataset.read";
    private final String resource = "dataset:orders";

    private boolean check(AuthorizationEvidenceProvider provider) {
        return new TrustedAuthorizationResolver(provider, new AuthorizationDecisionService())
                .isAllowed(principal, action, resource, tenant, workspace);
    }

    @Test
    void allowsExplicitGrantWithVerifiedMembership() {
        assertTrue(check((p, t, w, a, r) -> Optional.of(
                new AuthorizationEvidence(principal, tenant, workspace, Set.of(action), true))));
    }

    @Test
    void rejectsMissingEvidence() {
        assertFalse(check((p, t, w, a, r) -> Optional.empty()));
    }

    @Test
    void rejectsInactivePrincipal() {
        assertFalse(check((p, t, w, a, r) -> Optional.of(
                new AuthorizationEvidence(principal, tenant, workspace, Set.of(action), false))));
    }

    @Test
    void rejectsAnotherPrincipalEvidence() {
        assertFalse(check((p, t, w, a, r) -> Optional.of(
                new AuthorizationEvidence(UUID.randomUUID(), tenant, workspace, Set.of(action), true))));
    }

    @Test
    void rejectsCrossTenantEvidence() {
        assertFalse(check((p, t, w, a, r) -> Optional.of(
                new AuthorizationEvidence(principal, UUID.randomUUID(), workspace, Set.of(action), true))));
    }

    @Test
    void rejectsMissingActionGrant() {
        assertFalse(check((p, t, w, a, r) -> Optional.of(
                new AuthorizationEvidence(principal, tenant, workspace, Set.of(), true))));
    }

    @Test
    void deniesPolicyStoreFailure() {
        assertFalse(check((p, t, w, a, r) -> { throw new IllegalStateException("unavailable"); }));
    }
}

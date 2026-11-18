package tech.kayys.numou.security.vault.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VaultPolicyEvaluatorTest {
    private final VaultPolicyEvaluator evaluator = new VaultPolicyEvaluator();
    private final Instant now = Instant.parse("2026-11-03T00:00:00Z");

    @Test
    void defaultsToDenyWhenNoRuleMatches() {
        var decision = evaluator.evaluate(UUID.randomUUID(), UUID.randomUUID(), Set.of(),
                null, VaultId.generate(), SecretId.generate(), VaultOperation.READ, List.of(), now);

        assertEquals(VaultPolicyEffect.DENY, decision.effect());
        assertEquals("NO_MATCH_DEFAULT_DENY", decision.reasonCode());
        assertTrue(decision.matchedRuleId().isEmpty());
    }

    @Test
    void tenantVaultSecretAndOperationAreAllBoundaries() {
        UUID tenant = UUID.randomUUID();
        UUID principal = UUID.randomUUID();
        VaultId vault = VaultId.generate();
        SecretId secret = SecretId.generate();
        VaultPolicyRule allow = rule(tenant, principal, vault, secret, VaultOperation.READ,
                VaultPolicyEffect.ALLOW, 3L, now.minusSeconds(10), null);

        assertEquals(VaultPolicyEffect.ALLOW, evaluate(tenant, principal, vault, secret,
                VaultOperation.READ, List.of(allow)).effect());
        assertEquals(VaultPolicyEffect.DENY, evaluate(UUID.randomUUID(), principal, vault, secret,
                VaultOperation.READ, List.of(allow)).effect());
        assertEquals(VaultPolicyEffect.DENY, evaluate(tenant, principal, VaultId.generate(), secret,
                VaultOperation.READ, List.of(allow)).effect());
        assertEquals(VaultPolicyEffect.DENY, evaluate(tenant, principal, vault, SecretId.generate(),
                VaultOperation.READ, List.of(allow)).effect());
        assertEquals(VaultPolicyEffect.DENY, evaluate(tenant, principal, vault, secret,
                VaultOperation.ROTATE, List.of(allow)).effect());
    }

    @Test
    void explicitDenyWinsWithinOnePublishedSnapshot() {
        UUID tenant = UUID.randomUUID();
        UUID principal = UUID.randomUUID();
        VaultId vault = VaultId.generate();
        SecretId secret = SecretId.generate();
        VaultPolicyRule allow = rule(tenant, principal, vault, secret, VaultOperation.READ,
                VaultPolicyEffect.ALLOW, 7L, now.minusSeconds(10), null);
        VaultPolicyRule deny = rule(tenant, principal, vault, secret, VaultOperation.READ,
                VaultPolicyEffect.DENY, 7L, now.minusSeconds(10), null);

        var decision = evaluate(tenant, principal, vault, secret, VaultOperation.READ,
                List.of(allow, deny));

        assertEquals(VaultPolicyEffect.DENY, decision.effect());
        assertEquals("EXPLICIT_DENY", decision.reasonCode());
        assertEquals(7L, decision.policyVersion());
        assertTrue(decision.matchedRuleId().isPresent());
    }

    @Test
    void evaluationIsPinnedToThePublishedSnapshotRevision() {
        UUID tenant = UUID.randomUUID();
        UUID principal = UUID.randomUUID();
        VaultId vault = VaultId.generate();
        SecretId secret = SecretId.generate();
        VaultPolicyRule oldRevision = rule(tenant, principal, vault, secret, VaultOperation.READ,
                VaultPolicyEffect.ALLOW, 1L, now.minusSeconds(10), null);

        var stale = evaluator.evaluate(tenant, principal, Set.of(), null, vault, secret,
                VaultOperation.READ, List.of(oldRevision), 2L, now);
        var absent = evaluator.evaluate(tenant, principal, Set.of(), null, vault, secret,
                VaultOperation.READ, List.of(), 0L, now);

        assertEquals(VaultPolicyEffect.DENY, stale.effect());
        assertEquals(2L, stale.policyVersion());
        assertEquals(VaultPolicyEffect.DENY, absent.effect());
        assertEquals(0L, absent.policyVersion());
    }

    @Test
    void supportsExactRoleAndVerifiedWorkloadSubjectMatching() {
        UUID tenant = UUID.randomUUID();
        UUID principal = UUID.randomUUID();
        VaultId vault = VaultId.generate();
        SecretId secret = SecretId.generate();
        VaultPolicyRule role = new VaultPolicyRule(UUID.randomUUID(), tenant, 1L,
                VaultPolicySubjectType.ROLE, "vault-reader", null, null, vault, null, VaultOperation.READ,
                VaultPolicyEffect.ALLOW, now.minusSeconds(1), null);
        VaultPolicyRule workload = new VaultPolicyRule(UUID.randomUUID(), tenant, 1L,
                VaultPolicySubjectType.WORKLOAD_SUBJECT, "spiffe://prod/payments", "spiffe://prod", "numou-vault",
                vault, null, VaultOperation.READ, VaultPolicyEffect.ALLOW, now.minusSeconds(1), null);
        VaultWorkloadIdentity identity = new VaultWorkloadIdentity(tenant, "spiffe://prod",
                "spiffe://prod/payments", Set.of("numou-vault"), Set.of("secret:read"), now.plusSeconds(30));

        assertEquals(VaultPolicyEffect.ALLOW, evaluate(tenant, principal, vault, secret,
                VaultOperation.READ, List.of(role), Set.of("vault-reader"), null).effect());
        assertEquals(VaultPolicyEffect.ALLOW, evaluate(tenant, principal, vault, secret,
                VaultOperation.READ, List.of(workload), Set.of(), identity).effect());
        assertEquals(VaultPolicyEffect.DENY, evaluate(tenant, principal, vault, secret,
                VaultOperation.READ, List.of(workload), Set.of(), null).effect());

        VaultPolicyRule wrongIssuer = new VaultPolicyRule(UUID.randomUUID(), tenant, 1L,
                VaultPolicySubjectType.WORKLOAD_SUBJECT, "spiffe://prod/payments", "spiffe://untrusted",
                "numou-vault", vault, null, VaultOperation.READ, VaultPolicyEffect.ALLOW,
                now.minusSeconds(1), null);
        assertEquals(VaultPolicyEffect.DENY, evaluate(tenant, principal, vault, secret,
                VaultOperation.READ, List.of(wrongIssuer), Set.of(), identity).effect());

        VaultWorkloadIdentity wrongScope = new VaultWorkloadIdentity(tenant, "spiffe://prod",
                "spiffe://prod/payments", Set.of("numou-vault"), Set.of("secret:rotate"), now.plusSeconds(30));
        assertEquals(VaultPolicyEffect.DENY, evaluate(tenant, principal, vault, secret,
                VaultOperation.READ, List.of(workload), Set.of(), wrongScope).effect());
    }

    @Test
    void policyValidityUsesAnInclusiveStartAndExclusiveEnd() {
        UUID tenant = UUID.randomUUID();
        UUID principal = UUID.randomUUID();
        VaultPolicyRule rule = rule(tenant, principal, null, null, VaultOperation.READ,
                VaultPolicyEffect.ALLOW, 1L, now, now.plusSeconds(10));

        assertEquals(VaultPolicyEffect.ALLOW, evaluate(tenant, principal, VaultId.generate(),
                SecretId.generate(), VaultOperation.READ, List.of(rule), now).effect());
        assertEquals(VaultPolicyEffect.DENY, evaluate(tenant, principal, VaultId.generate(),
                SecretId.generate(), VaultOperation.READ, List.of(rule), now.plusSeconds(10)).effect());
    }

    private VaultPolicyDecision evaluate(UUID tenant, UUID principal, VaultId vault, SecretId secret,
            VaultOperation operation, List<VaultPolicyRule> rules) {
        return evaluate(tenant, principal, vault, secret, operation, rules, Set.of(), null);
    }

    private VaultPolicyDecision evaluate(UUID tenant, UUID principal, VaultId vault, SecretId secret,
            VaultOperation operation, List<VaultPolicyRule> rules, Set<String> roles,
            VaultWorkloadIdentity identity) {
        return evaluator.evaluate(tenant, principal, roles, identity, vault, secret, operation, rules, now);
    }

    private VaultPolicyRule rule(UUID tenant, UUID principal, VaultId vault, SecretId secret,
            VaultOperation operation, VaultPolicyEffect effect, long version,
            Instant from, Instant until) {
        return new VaultPolicyRule(UUID.randomUUID(), tenant, version,
                VaultPolicySubjectType.PRINCIPAL_ID, principal.toString(), null, null, vault, secret,
                operation, effect, from, until);
    }
}

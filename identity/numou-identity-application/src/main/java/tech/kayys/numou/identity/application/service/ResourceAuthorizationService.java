package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.identity.application.port.AuthorizationPolicyRepository;
import tech.kayys.numou.identity.application.port.AuthorizationPort;
import tech.kayys.numou.identity.application.port.ResourceAttributePort;
import tech.kayys.numou.identity.application.security.AuthorizationRequirement;
import tech.kayys.numou.identity.application.security.Principal;
import tech.kayys.numou.identity.application.security.abac.PolicyEffect;
import tech.kayys.numou.identity.application.security.abac.PolicyEvaluator;

import java.util.Objects;

/**
 * Orchestrates RBAC-first authorization with an ABAC policy overlay.
 *
 * <h3>Decision flow</h3>
 * <ol>
 *   <li>RBAC gate — if the principal has no role that covers the required permission the
 *       request is denied immediately (no ABAC evaluation).</li>
 *   <li>Attribute resolution — {@link ResourceAttributePort} collects subject/resource/
 *       environment attributes into an {@link tech.kayys.numou.identity.application.security.abac.AuthorizationContext}.</li>
 *   <li>ABAC overlay — active policies for {@code (tenantId, permission)} are evaluated:
 *       <ul>
 *         <li>Any DENY match → deny.</li>
 *         <li>At least one ALLOW match → allow.</li>
 *         <li><b>No matching policies</b> → {@link #abacDefaultEffect} applies
 *             ({@code ALLOW} by default for backwards compatibility, configurable to
 *             {@code DENY} for strict tenants).</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h3>ABAC default effect</h3>
 * The fail-open default ({@code ALLOW} when no policies exist) is intentional: RBAC is
 * the primary access control layer, and a fresh tenant that has not yet defined ABAC
 * policies should not be locked out.  Deployments that want strict ABAC enforcement
 * should set {@code abacDefaultEffect = DENY} so that every access requires an explicit
 * ALLOW policy.
 */
public final class ResourceAuthorizationService {

    /**
     * Effect applied when no ABAC policies match for a {@code (tenant, permission)} pair.
     *
     * <ul>
     *   <li>{@code ALLOW} (default) — RBAC is sufficient; ABAC is an opt-in refinement.</li>
     *   <li>{@code DENY} — strict mode; every access requires at least one active ALLOW policy.</li>
     * </ul>
     */
    public enum AbacDefaultEffect { ALLOW, DENY }

    private final AuthorizationPort rbac;
    private final ResourceAttributePort attributes;
    private final AuthorizationPolicyRepository policies;
    private final PolicyEvaluator evaluator;
    private final AbacDefaultEffect abacDefaultEffect;

    /**
     * Backwards-compatible constructor — uses {@link AbacDefaultEffect#ALLOW}.
     */
    public ResourceAuthorizationService(
            AuthorizationPort rbac,
            ResourceAttributePort attributes,
            AuthorizationPolicyRepository policies,
            PolicyEvaluator evaluator
    ) {
        this(rbac, attributes, policies, evaluator, AbacDefaultEffect.ALLOW);
    }

    /**
     * Full constructor.
     *
     * @param abacDefaultEffect effect when no ABAC policies exist for a permission; must not be null
     */
    public ResourceAuthorizationService(
            AuthorizationPort rbac,
            ResourceAttributePort attributes,
            AuthorizationPolicyRepository policies,
            PolicyEvaluator evaluator,
            AbacDefaultEffect abacDefaultEffect
    ) {
        this.rbac               = Objects.requireNonNull(rbac, "rbac cannot be null");
        this.attributes         = Objects.requireNonNull(attributes, "attributes cannot be null");
        this.policies           = Objects.requireNonNull(policies, "policies cannot be null");
        this.evaluator          = Objects.requireNonNull(evaluator, "evaluator cannot be null");
        this.abacDefaultEffect  = Objects.requireNonNull(abacDefaultEffect, "abacDefaultEffect cannot be null");
    }

    public Uni<Boolean> isAllowed(
            Principal principal,
            String tenantId,
            AuthorizationRequirement requirement,
            String resourceId
    ) {
        if (principal == null || !principal.authenticated() || tenantId == null || tenantId.isBlank()) {
            return Uni.createFrom().item(false);
        }
        return rbac.isAllowed(principal, tenantId, requirement)
                .flatMap(rbacAllowed -> {
                    if (!rbacAllowed) {
                        // Short-circuit: RBAC is the gate; ABAC is never consulted.
                        return Uni.createFrom().item(false);
                    }
                    return attributes.resolve(principal, tenantId, requirement, resourceId)
                            .chain(context -> policies.findActive(tenantId, requirement.permissionName())
                                    .map(matching -> {
                                        if (!context.tenantId().equals(tenantId)
                                                || !context.permission().equals(requirement.permissionName())) {
                                            return false;
                                        }
                                        if (matching.isEmpty()) {
                                            // No ABAC policies configured — apply the configured default.
                                            return abacDefaultEffect == AbacDefaultEffect.ALLOW;
                                        }
                                        var applicable = matching.stream()
                                                .filter(policy -> policy.tenantId().equals(tenantId))
                                                .filter(policy -> policy.permission()
                                                        .equals(requirement.permissionName()))
                                                .filter(policy -> evaluator.evaluate(policy.condition(), context))
                                                .toList();
                                        if (applicable.stream().anyMatch(
                                                policy -> policy.effect() == PolicyEffect.DENY)) {
                                            return false;
                                        }
                                        return applicable.stream().anyMatch(
                                                policy -> policy.effect() == PolicyEffect.ALLOW);
                                    }));
                });
    }
}


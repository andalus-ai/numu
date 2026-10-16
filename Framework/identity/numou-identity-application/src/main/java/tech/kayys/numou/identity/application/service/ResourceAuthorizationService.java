package tech.kayys.syirkah.identity.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.identity.application.port.AuthorizationPolicyRepository;
import tech.kayys.syirkah.identity.application.port.AuthorizationPort;
import tech.kayys.syirkah.identity.application.port.ResourceAttributePort;
import tech.kayys.syirkah.identity.application.security.AuthorizationRequirement;
import tech.kayys.syirkah.identity.application.security.Principal;
import tech.kayys.syirkah.identity.application.security.abac.PolicyEffect;
import tech.kayys.syirkah.identity.application.security.abac.PolicyEvaluator;

import java.util.Objects;

public final class ResourceAuthorizationService {
    private final AuthorizationPort rbac;
    private final ResourceAttributePort attributes;
    private final AuthorizationPolicyRepository policies;
    private final PolicyEvaluator evaluator;

    public ResourceAuthorizationService(
            AuthorizationPort rbac,
            ResourceAttributePort attributes,
            AuthorizationPolicyRepository policies,
            PolicyEvaluator evaluator
    ) {
        this.rbac = Objects.requireNonNull(rbac);
        this.attributes = Objects.requireNonNull(attributes);
        this.policies = Objects.requireNonNull(policies);
        this.evaluator = Objects.requireNonNull(evaluator);
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
                                            return true;
                                        }
                                        var applicable = matching.stream()
                                                .filter(policy -> policy.tenantId().equals(tenantId))
                                                .filter(policy -> policy.permission()
                                                        .equals(requirement.permissionName()))
                                                .filter(policy -> evaluator.evaluate(policy.condition(), context))
                                                .toList();
                                        if (applicable.stream().anyMatch(
                                                policy -> policy.effect() == PolicyEffect.DENY
                                        )) {
                                            return false;
                                        }
                                        return applicable.stream().anyMatch(
                                                policy -> policy.effect() == PolicyEffect.ALLOW
                                        );
                                    }));
                });
    }
}

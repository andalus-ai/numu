package tech.kayys.syirkah.security.application.authorization;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import tech.kayys.syirkah.security.domain.authorization.AccessDecision;
import tech.kayys.syirkah.security.domain.authorization.AccessRequest;
import tech.kayys.syirkah.security.domain.authorization.PermissionEvaluator;
import tech.kayys.syirkah.security.spi.port.PolicyPort;
import tech.kayys.syirkah.security.spi.port.ResourceAuthorizationPort;

import java.util.Objects;

/**
 * Default implementation of {@link AuthorizationService} (security02.md §P3-06).
 *
 * <p>Evaluates role-based permissions via {@link PolicyPort} and {@link PermissionEvaluator}.
 * If RBAC permits and a {@link ResourceAuthorizationPort} is configured, further evaluates
 * fine-grained ABAC/resource policy.
 */
@ApplicationScoped
public class DefaultAuthorizationService implements AuthorizationService {

    private final PolicyPort policyPort;
    private final ResourceAuthorizationPort resourceAuthorizationPort;

    public DefaultAuthorizationService(PolicyPort policyPort) {
        this(policyPort, (ResourceAuthorizationPort) null);
    }

    public DefaultAuthorizationService(
            PolicyPort policyPort,
            ResourceAuthorizationPort resourceAuthorizationPort) {
        this.policyPort = Objects.requireNonNull(policyPort, "policyPort cannot be null");
        this.resourceAuthorizationPort = resourceAuthorizationPort;
    }

    @Inject
    public DefaultAuthorizationService(
            PolicyPort policyPort,
            Instance<ResourceAuthorizationPort> resourceAuthorizationInstances) {
        this.policyPort = Objects.requireNonNull(policyPort, "policyPort cannot be null");
        this.resourceAuthorizationPort = (resourceAuthorizationInstances != null && resourceAuthorizationInstances.isResolvable())
                ? resourceAuthorizationInstances.get()
                : null;
    }

    @Override
    public Uni<AccessDecision> authorize(AccessRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        return policyPort
                .permissionsFor(request.principal(), request.tenantId())
                .chain(permissions -> {
                    var rbacDecision = PermissionEvaluator.with(permissions).decide(request);
                    if (!rbacDecision.allowed()) {
                        return Uni.createFrom().item(rbacDecision);
                    }
                    if (resourceAuthorizationPort != null) {
                        return resourceAuthorizationPort.evaluate(request);
                    }
                    return Uni.createFrom().item(rbacDecision);
                });
    }
}

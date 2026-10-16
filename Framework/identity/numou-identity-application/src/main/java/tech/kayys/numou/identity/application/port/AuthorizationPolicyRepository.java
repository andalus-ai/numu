package tech.kayys.syirkah.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.identity.application.security.abac.AuthorizationPolicy;

import java.util.List;

public interface AuthorizationPolicyRepository {
    Uni<List<AuthorizationPolicy>> findActive(String tenantId, String permission);
}

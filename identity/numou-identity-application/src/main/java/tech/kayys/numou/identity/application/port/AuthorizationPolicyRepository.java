package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.identity.application.security.abac.AuthorizationPolicy;

import java.util.List;

public interface AuthorizationPolicyRepository {
    Uni<List<AuthorizationPolicy>> findActive(String tenantId, String permission);
}

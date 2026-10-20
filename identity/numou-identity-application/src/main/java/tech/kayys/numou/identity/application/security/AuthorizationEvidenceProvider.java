package tech.kayys.numou.identity.application.security;

import java.util.Optional;
import java.util.UUID;

@FunctionalInterface
public interface AuthorizationEvidenceProvider {
    Optional<AuthorizationEvidence> resolve(UUID principalId, UUID tenantId, UUID workspaceId,
                                            String action, String resource);
}

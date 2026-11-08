package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;

import java.util.UUID;

public interface TokenVerifierPort {

    record VerifiedToken(String principalType, UUID principalId, String displayName, UUID tenantId) {
        /** Compatibility constructor for tenant-neutral legacy tokens. */
        public VerifiedToken(String principalType, UUID principalId, String displayName) {
            this(principalType, principalId, displayName, null);
        }
    }

    Uni<VerifiedToken> verifyToken(String rawToken);
}

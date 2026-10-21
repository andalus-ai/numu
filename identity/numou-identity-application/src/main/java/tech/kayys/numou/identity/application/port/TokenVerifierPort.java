package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;

import java.util.UUID;

public interface TokenVerifierPort {

    record VerifiedToken(String principalType, UUID principalId, String displayName) {}

    Uni<VerifiedToken> verifyToken(String rawToken);
}

package tech.kayys.numou.identity.application.port;

import java.util.UUID;

public interface TokenIssuerPort {

    String issueToken(UUID principalId, String principalType, String displayName);
}

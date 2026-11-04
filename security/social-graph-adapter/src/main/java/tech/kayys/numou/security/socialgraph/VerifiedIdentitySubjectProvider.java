package tech.kayys.numou.security.socialgraph;

import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import java.util.Optional;

/**
 * Supplies the original issuer/subject from the host's validated authentication
 * mechanism. Implementations must never read identity from request data.
 */
@FunctionalInterface
public interface VerifiedIdentitySubjectProvider {
    Optional<IdentitySubjectRef> current();
}

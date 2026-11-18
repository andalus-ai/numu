package tech.kayys.numou.security.socialgraph;

import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;

import java.util.Optional;

/**
 * Supplies the issuer/subject from the already-validated authentication
 * mechanism (for example, verified OIDC token claims).
 *
 * <p>Implementations must not read issuer or subject from request parameters,
 * headers, or command payloads. An empty result means the authenticated
 * principal has no usable external identity association.</p>
 */
@FunctionalInterface
public interface VerifiedIdentitySubjectProvider {
    Optional<IdentitySubjectRef> current();
}

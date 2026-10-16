package tech.kayys.syirkah.security.application.authorization;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.security.domain.authorization.AccessDecision;
import tech.kayys.syirkah.security.domain.authorization.AccessRequest;

/**
 * Application port for evaluating access control decisions (security02.md §P3-05).
 */
public interface AuthorizationService {

    /**
     * Authorizes an access request.
     *
     * @param request the access request
     * @return reactive Uni producing the access decision
     */
    Uni<AccessDecision> authorize(AccessRequest request);
}

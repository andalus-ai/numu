package tech.kayys.syirkah.security.spi.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.security.domain.authorization.AccessDecision;
import tech.kayys.syirkah.security.domain.authorization.AccessRequest;

/**
 * Port for evaluating authorization access requests (security01.md §3.4).
 */
public interface AuthorizationServicePort {

    /**
     * Authorizes an access request.
     *
     * @param request the access request
     * @return reactive Uni emitting the access decision
     */
    Uni<AccessDecision> authorize(AccessRequest request);
}

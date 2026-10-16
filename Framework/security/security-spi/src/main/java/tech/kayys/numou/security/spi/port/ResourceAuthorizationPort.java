package tech.kayys.syirkah.security.spi.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.security.domain.authorization.AccessDecision;
import tech.kayys.syirkah.security.domain.authorization.AccessRequest;

/**
 * Port for ABAC/resource-level authorization evaluation.
 */
public interface ResourceAuthorizationPort {

    /**
     * Evaluates fine-grained resource policy / attributes for an access request.
     *
     * @param request the access request
     * @return reactive Uni emitting the access decision
     */
    Uni<AccessDecision> evaluate(AccessRequest request);
}

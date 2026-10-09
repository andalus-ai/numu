package tech.kayys.syirkah.security.application.authz;

import tech.kayys.syirkah.security.domain.authorization.AccessDecision;

/** Carries an internal denial reason; the REST layer maps it to 403 without echoing the reason. */
public final class AccessDeniedException extends RuntimeException {

    private final transient AccessDecision decision;

    public AccessDeniedException(AccessDecision decision) {
        super("Access denied: " + decision.reason());
        this.decision = decision;
    }

    public AccessDecision decision() {
        return decision;
    }
}

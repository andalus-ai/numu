package tech.kayys.syirkah.security.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

/**
 * Outcome of an authorization check.
 *
 * <p>A denial carries the reason so callers can surface it (and audit can
 * record it) without string-matching an enum.
 */
public enum Decision implements ValueObject {

    /** The action is permitted. */
    ALLOW,

    /** The action is refused. */
    DENY
}

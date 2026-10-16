package tech.kayys.syirkah.security.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

/**
 * The verb of an authorization decision - deliberately small and generic
 * so every bounded context shares one vocabulary.
 */
public enum ActionType implements ValueObject {

    CREATE,
    READ,
    UPDATE,
    DELETE,

    /** Domain-specific transitions: dispatch, approve, cancel, ... */
    PERFORM
}

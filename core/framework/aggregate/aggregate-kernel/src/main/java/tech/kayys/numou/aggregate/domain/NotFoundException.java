package tech.kayys.numou.aggregate.domain;

import tech.kayys.numou.foundation.domain.exception.DomainException;

/** The aggregate does not exist <em>for this tenant</em> (HTTP 404; never reveals other tenants' data). */
public final class NotFoundException extends DomainException {

    public NotFoundException(String what, Object id) {
        super(what + " not found: " + id);
    }
}

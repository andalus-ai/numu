package tech.kayys.syirkah.restaurant.kernel.domain;

import tech.kayys.syirkah.foundation.domain.exception.DomainException;

/** The aggregate does not exist <em>for this tenant</em> (HTTP 404; never reveals other tenants' data). */
public final class NotFoundException extends DomainException {

    public NotFoundException(String what, Object id) {
        super(what + " not found: " + id);
    }
}

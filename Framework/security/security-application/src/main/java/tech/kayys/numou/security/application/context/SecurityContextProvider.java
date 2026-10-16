package tech.kayys.numou.security.application.context;

import tech.kayys.numou.security.domain.authorization.SecurityContext;

import java.util.Optional;

/**
 * Contract for accessing the current {@link SecurityContext} in reactive execution.
 */
public interface SecurityContextProvider {

    /**
     * Returns the current security context if present.
     */
    Optional<SecurityContext> current();

    /**
     * Returns the current security context or throws if not present.
     */
    SecurityContext require();
}

package tech.kayys.syirkah.security.spi.port;

import tech.kayys.syirkah.security.domain.authorization.Principal;

import java.util.Objects;

/**
 * Request to resolve an authenticated principal against a requested tenant selector.
 */
public record TenantResolutionRequest(
        Principal principal,
        String requestedTenant) {

    public TenantResolutionRequest {
        Objects.requireNonNull(principal, "principal cannot be null");
    }
}

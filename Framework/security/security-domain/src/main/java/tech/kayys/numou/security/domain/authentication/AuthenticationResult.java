package tech.kayys.syirkah.security.domain.authentication;

import tech.kayys.syirkah.security.domain.authorization.Principal;

import java.util.Objects;

public record AuthenticationResult(
        Principal principal,
        AuthenticationInfo authentication) {

    public AuthenticationResult {
        Objects.requireNonNull(principal, "principal cannot be null");
        Objects.requireNonNull(authentication, "authentication cannot be null");

        if (!authentication.authenticated()) {
            throw new IllegalArgumentException(
                    "Authenticated result requires authenticated info");
        }
    }
}

package tech.kayys.syirkah.security.domain.authentication;

import java.time.Instant;
import java.util.Objects;

public record AuthenticationInfo(
        AuthenticationStatus status,
        AuthenticationMethod method,
        Instant authenticatedAt) {

    public AuthenticationInfo {
        Objects.requireNonNull(status, "status cannot be null");

        if (status == AuthenticationStatus.AUTHENTICATED) {
            Objects.requireNonNull(method, "method cannot be null for authenticated status");
            Objects.requireNonNull(authenticatedAt, "authenticatedAt cannot be null for authenticated status");
        }
    }

    public boolean authenticated() {
        return status == AuthenticationStatus.AUTHENTICATED;
    }

    public static AuthenticationInfo authenticated(
            AuthenticationMethod method,
            Instant at) {

        return new AuthenticationInfo(
                AuthenticationStatus.AUTHENTICATED,
                method,
                at);
    }

    public static AuthenticationInfo anonymous() {
        return new AuthenticationInfo(
                AuthenticationStatus.ANONYMOUS,
                null,
                null);
    }
}

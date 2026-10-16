package tech.kayys.syirkah.security.spi.port;

import java.util.Objects;

public record AuthenticationRequest(
        CredentialType type,
        String credential) {

    public AuthenticationRequest {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(credential, "credential cannot be null");

        if (credential.isBlank()) {
            throw new IllegalArgumentException("credential cannot be blank");
        }
    }
}

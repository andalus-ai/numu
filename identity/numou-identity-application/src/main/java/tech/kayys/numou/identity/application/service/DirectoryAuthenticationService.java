package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.identity.application.dto.FederatedLoginResponse;
import tech.kayys.numou.identity.application.port.DirectoryAuthPort;
import tech.kayys.numou.identity.domain.idp.IdentityProviderType;

import java.util.Objects;
import java.util.UUID;

/**
 * Enterprise LDAP / Active Directory login: verifies credentials with a directory bind,
 * then runs the exact same JIT provisioning and group-to-role synchronization as OIDC.
 */
public class DirectoryAuthenticationService {

    private final FederatedAuthenticationService federation;
    private final DirectoryAuthPort directory;

    public DirectoryAuthenticationService(
            FederatedAuthenticationService federation,
            DirectoryAuthPort directory
    ) {
        this.federation = Objects.requireNonNull(federation);
        this.directory = Objects.requireNonNull(directory);
    }

    /**
     * Authenticates one directory user and returns an internal Numou access token.
     *
     * @param providerId LDAP/AD provider config id
     * @param username   directory login (survives only inside this call - never persisted)
     * @param password   raw password (handled in-memory only, never persisted or logged)
     */
    public Uni<FederatedLoginResponse> login(UUID providerId, String username, String password) {
        Objects.requireNonNull(providerId, "providerId cannot be null");
        if (username == null || username.isBlank()) {
            return Uni.createFrom().failure(tech.kayys.numou.foundation.application.result.ApplicationError.of(
                    "LDAP_USERNAME_REQUIRED", "Directory username is required").toException());
        }
        if (password == null || password.isEmpty()) {
            return Uni.createFrom().failure(tech.kayys.numou.foundation.application.result.ApplicationError.of(
                    "LDAP_PASSWORD_REQUIRED", "Directory password is required").toException());
        }

        return federation.requireProvider(providerId, IdentityProviderType.LDAP, "LDAP bind")
                .chain(config -> directory.authenticate(config, username, password)
                        .chain(identity -> federation.authenticate(config, identity)));
    }
}

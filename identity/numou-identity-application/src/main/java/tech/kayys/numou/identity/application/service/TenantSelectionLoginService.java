package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.identity.application.dto.AccessTokenResponse;
import tech.kayys.numou.identity.application.dto.LoginRequest;
import tech.kayys.numou.identity.application.port.PasswordHasher;
import tech.kayys.numou.identity.application.port.TenantTokenIssuerPort;
import tech.kayys.numou.identity.application.port.UserRepository;
import tech.kayys.numou.identity.domain.valueobject.EmailAddress;

import java.util.Objects;

/** Versioned local login path that returns only a short-lived tenant-selection credential. */
public class TenantSelectionLoginService {
    private static final long SELECTION_TOKEN_LIFETIME_SECONDS = 300;

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final TenantTokenIssuerPort tokenIssuer;

    public TenantSelectionLoginService(UserRepository users, PasswordHasher passwordHasher,
                                       TenantTokenIssuerPort tokenIssuer) {
        this.users = Objects.requireNonNull(users, "users");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer, "tokenIssuer");
    }

    public Uni<AccessTokenResponse> login(LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            return failure("INVALID_CREDENTIALS", "Username and password are required");
        }
        if (request.rememberMe()) {
            return failure("REMEMBER_ME_UNSUPPORTED",
                    "Long-lived credentials are not issued by tenant-selection login");
        }

        final EmailAddress email;
        try {
            email = EmailAddress.of(request.username());
        } catch (RuntimeException invalidEmail) {
            return failure("BAD_CREDENTIALS", "Invalid username or password");
        }

        return users.findByEmail(email)
                .chain(optional -> {
                    if (optional.isEmpty()) {
                        return failure("BAD_CREDENTIALS", "Invalid username or password");
                    }
                    var user = optional.get();
                    if (!user.isActive() || !passwordHasher.matches(request.password(), user.passwordHash())) {
                        return failure("BAD_CREDENTIALS", "Invalid username or password");
                    }
                    Uni<Void> upgrade = Uni.createFrom().voidItem();
                    if (passwordHasher.needsRehash(user.passwordHash())) {
                        upgrade = users.updatePasswordHash(user.id(),
                                passwordHasher.hash(request.password()));
                    }
                    return upgrade.map(ignored -> {
                        String token = tokenIssuer.issueTenantSelectionToken(
                                user.id().value(), user.displayName().value());
                        return AccessTokenResponse.bearer(token, SELECTION_TOKEN_LIFETIME_SECONDS);
                    });
                });
    }

    private static <T> Uni<T> failure(String code, String message) {
        return Uni.createFrom().failure(ApplicationError.of(code, message).toException());
    }
}

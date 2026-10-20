package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.identity.application.dto.AccessTokenResponse;
import tech.kayys.numou.identity.application.dto.LoginRequest;
import tech.kayys.numou.identity.application.port.PasswordHasher;
import tech.kayys.numou.identity.application.port.TokenIssuerPort;
import tech.kayys.numou.identity.application.port.TokenVerifierPort;
import tech.kayys.numou.identity.application.port.UserRepository;
import tech.kayys.numou.identity.domain.valueobject.EmailAddress;

import java.util.Objects;
import java.util.UUID;

public class TokenService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuerPort tokenIssuer;
    private final TokenVerifierPort tokenVerifier;
    private final long lifespanSeconds;

    public TokenService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            TokenIssuerPort tokenIssuer,
            TokenVerifierPort tokenVerifier,
            long lifespanSeconds
    ) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer);
        this.tokenVerifier = Objects.requireNonNull(tokenVerifier);
        this.lifespanSeconds = lifespanSeconds > 0 ? lifespanSeconds : 900;
    }

    public Uni<AccessTokenResponse> loginLocal(LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            return Uni.createFrom().failure(ApplicationError.of(
                    "INVALID_CREDENTIALS", "Username and password are required"
            ).toException());
        }

        try {
            var email = EmailAddress.of(request.username());
            return userRepository.findByEmail(email)
                    .chain(optional -> {
                        if (optional.isEmpty()) {
                            return Uni.createFrom().failure(ApplicationError.of(
                                    "BAD_CREDENTIALS", "Invalid username or password"
                            ).toException());
                        }
                        var user = optional.get();
                        boolean valid = user.isActive()
                                && passwordHasher.matches(request.password(), user.passwordHash());
                        if (!valid) {
                            return Uni.createFrom().failure(ApplicationError.of(
                                    "BAD_CREDENTIALS", "Invalid username or password"
                            ).toException());
                        }
                        String token = tokenIssuer.issueToken(
                                user.id().value(),
                                "USER",
                                user.displayName().value()
                        );
                        return Uni.createFrom().item(AccessTokenResponse.bearer(token, lifespanSeconds));
                    });
        } catch (IllegalArgumentException e) {
            return Uni.createFrom().failure(ApplicationError.of(
                    "BAD_CREDENTIALS", "Invalid username or password format"
            ).toException());
        }
    }

    public AccessTokenResponse issueFor(UUID principalId, String principalType, String displayName) {
        String token = tokenIssuer.issueToken(principalId, principalType, displayName);
        return AccessTokenResponse.bearer(token, lifespanSeconds);
    }

    public Uni<TokenVerifierPort.VerifiedToken> verify(String rawToken) {
        return tokenVerifier.verifyToken(rawToken);
    }
}

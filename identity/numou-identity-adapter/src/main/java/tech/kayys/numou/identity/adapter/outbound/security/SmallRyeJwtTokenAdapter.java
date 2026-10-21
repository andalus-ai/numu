package tech.kayys.numou.identity.adapter.outbound.security;

import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.identity.application.port.TokenIssuerPort;
import tech.kayys.numou.identity.application.port.TokenVerifierPort;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class SmallRyeJwtTokenAdapter implements TokenIssuerPort, TokenVerifierPort {

    @Inject
    JWTParser jwtParser;

    @ConfigProperty(name = "smallrye.jwt.new-token.issuer", defaultValue = "https://identity.numou.internal")
    String issuer;

    @ConfigProperty(name = "smallrye.jwt.new-token.lifespan", defaultValue = "900")
    long lifespanSeconds;

    @Override
    public String issueToken(UUID principalId, String principalType, String displayName) {
        return Jwt.issuer(issuer)
                .subject(principalId.toString())
                .upn(displayName)
                .claim("principalType", principalType)
                .groups(Set.of())
                .expiresIn(Duration.ofSeconds(lifespanSeconds))
                .sign();
    }

    @Override
    public Uni<VerifiedToken> verifyToken(String rawToken) {
        return Uni.createFrom().item(() -> {
            try {
                JsonWebToken jwt = jwtParser.parse(rawToken);
                String principalType = jwt.getClaim("principalType");
                if (principalType == null || principalType.isBlank()) {
                    principalType = "USER";
                }
                UUID principalId = UUID.fromString(jwt.getSubject());
                String displayName = jwt.getClaim("upn");
                return new VerifiedToken(principalType, principalId, displayName != null ? displayName : "");
            } catch (Exception e) {
                throw ApplicationError.of("INVALID_TOKEN", "Token could not be verified: " + e.getMessage()).toException();
            }
        });
    }
}

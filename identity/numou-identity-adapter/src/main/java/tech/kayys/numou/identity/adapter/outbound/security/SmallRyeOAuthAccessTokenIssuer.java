package tech.kayys.numou.identity.adapter.outbound.security;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.identity.application.port.OAuthAccessTokenIssuerPort;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Signs machine-client tokens using the externally mounted Identity signing key. */
@ApplicationScoped
public class SmallRyeOAuthAccessTokenIssuer implements OAuthAccessTokenIssuerPort {
    @ConfigProperty(name = "smallrye.jwt.new-token.issuer") String issuer;
    @ConfigProperty(name = "numou.oauth.access-token.audience") String audience;
    @ConfigProperty(name = "numou.oauth.access-token.lifespan-seconds", defaultValue = "300") long lifespanSeconds;

    @Override
    public String issueClientAccessToken(String clientId, UUID tenantId, UUID applicationId,
                                         Set<String> scopes, long credentialVersion) {
        if (lifespanSeconds < 30 || lifespanSeconds > 900)
            throw new IllegalStateException("OAuth client token lifetime must be between 30 and 900 seconds");
        String scope = scopes.stream().sorted().collect(Collectors.joining(" "));
        return Jwt.issuer(issuer).subject(clientId).audience(audience)
                .claim("client_id", clientId).claim("tenant_id", tenantId.toString())
                .claim("application_id", applicationId.toString()).claim("scope", scope)
                // Quarkus @RolesAllowed consumes the standard groups claim. Each
                // group is derived only from scopes authorized on this credential.
                .claim("groups", Set.copyOf(scopes))
                .claim("credential_version", credentialVersion).claim("token_use", "access")
                .claim("jti", UUID.randomUUID().toString())
                .expiresIn(Duration.ofSeconds(lifespanSeconds)).sign();
    }
}

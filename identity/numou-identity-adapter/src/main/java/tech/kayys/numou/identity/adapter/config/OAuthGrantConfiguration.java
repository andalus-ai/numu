package tech.kayys.numou.identity.adapter.config;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.Config;
import tech.kayys.numou.identity.application.port.OAuthAccessTokenIssuerPort;
import tech.kayys.numou.identity.application.port.OAuthClientCredentialRepository;
import tech.kayys.numou.identity.application.security.ClientSecretGenerator;
import tech.kayys.numou.identity.application.service.OAuthClientCredentialsGrantService;
import java.time.Clock;

@ApplicationScoped
public class OAuthGrantConfiguration {
    @Produces
    ClientSecretGenerator clientSecretGenerator() { return new ClientSecretGenerator(); }

    @Produces @ApplicationScoped
    OAuthClientCredentialsGrantService grantService(OAuthClientCredentialRepository credentials,
            ClientSecretGenerator secrets, OAuthAccessTokenIssuerPort issuer, Config config) {
        long lifetime = config.getOptionalValue("numou.oauth.access-token.lifespan-seconds", Long.class).orElse(300L);
        return new OAuthClientCredentialsGrantService(credentials, secrets, issuer, Clock.systemUTC(), lifetime);
    }
}

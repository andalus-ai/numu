package tech.kayys.numou.identity.adapter.config;

import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.identity.application.port.OAuthClientCredentialRepository;
import tech.kayys.numou.identity.application.service.OAuthClientAccessTokenStateValidator;

import java.time.Clock;

/** CDI composition for live OAuth credential-state validation. */
public class OAuthIntrospectionConfiguration {
    @Produces
    OAuthClientAccessTokenStateValidator stateValidator(OAuthClientCredentialRepository credentials) {
        return new OAuthClientAccessTokenStateValidator(credentials, Clock.systemUTC());
    }
}

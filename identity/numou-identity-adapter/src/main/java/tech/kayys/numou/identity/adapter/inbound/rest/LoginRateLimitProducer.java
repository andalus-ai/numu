package tech.kayys.numou.identity.adapter.inbound.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.security.application.ratelimit.TokenBucketRateLimiter;

/** CDI wiring for the unauthenticated login endpoint's bounded token bucket. */
@ApplicationScoped
public class LoginRateLimitProducer {

    @ConfigProperty(name = "numou.identity.login-rate-limit.capacity", defaultValue = "10")
    long capacity;

    @ConfigProperty(name = "numou.identity.login-rate-limit.refill-per-second", defaultValue = "0.1")
    double refillPerSecond;

    @ConfigProperty(name = "numou.identity.login-rate-limit.max-keys", defaultValue = "100000")
    int maxKeys;

    @Produces
    @ApplicationScoped
    TokenBucketRateLimiter loginRateLimiter() {
        return new TokenBucketRateLimiter(capacity, refillPerSecond, maxKeys);
    }
}

package tech.kayys.syirkah.identity.application.support;

import tech.kayys.syirkah.identity.application.port.PasswordHasher;
import tech.kayys.syirkah.identity.domain.valueobject.PasswordHash;

/**
 * NOT for production use - just tags the raw value so tests can
 * assert on hashing/matching behavior without pulling in a real
 * hashing library into the application module's test scope.
 */
public final class InMemoryPasswordHasher implements PasswordHasher {

    @Override
    public PasswordHash hash(String rawPassword) {
        return PasswordHash.of("hashed:" + rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash hash) {
        return hash.value().equals("hashed:" + rawPassword);
    }

}

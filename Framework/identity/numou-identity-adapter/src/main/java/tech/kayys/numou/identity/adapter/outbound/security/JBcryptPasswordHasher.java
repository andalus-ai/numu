package tech.kayys.syirkah.identity.adapter.outbound.security;

import jakarta.enterprise.context.ApplicationScoped;
import org.mindrot.jbcrypt.BCrypt;
import tech.kayys.syirkah.identity.application.port.PasswordHasher;
import tech.kayys.syirkah.identity.domain.valueobject.PasswordHash;

@ApplicationScoped
public class JBcryptPasswordHasher implements PasswordHasher {

    private static final int WORK_FACTOR = 12;

    @Override
    public PasswordHash hash(String rawPassword) {
        return PasswordHash.of(BCrypt.hashpw(rawPassword, BCrypt.gensalt(WORK_FACTOR)));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash hash) {
        return BCrypt.checkpw(rawPassword, hash.value());
    }

}

package tech.kayys.numou.identity.application.port;

import tech.kayys.numou.identity.domain.valueobject.PasswordHash;

/**
 * Password hashing is infrastructure work behind the Identity application boundary.
 * Implementations must use a versioned encoded representation and verify legacy
 * formats during a controlled migration.
 */
public interface PasswordHasher {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash hash);

    /**
     * True when a successfully authenticated credential should be re-encoded
     * using the implementation's current algorithm and parameters.
     */
    default boolean needsRehash(PasswordHash hash) {
        return false;
    }
}

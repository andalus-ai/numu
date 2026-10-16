package tech.kayys.syirkah.identity.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.util.Objects;

/**
 * Wraps an already-hashed password. The domain never hashes or
 * verifies passwords itself - hashing is CPU-bound infrastructure
 * work, not a domain rule (see the PasswordHasher application port).
 * This value object exists purely so a raw password string can never
 * accidentally end up stored on the aggregate.
 */
public record PasswordHash(String value) implements ValueObject {

    public PasswordHash {
        Objects.requireNonNull(value, "Password hash cannot be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be blank");
        }
    }

    public static PasswordHash of(String value) {
        return new PasswordHash(value);
    }

    @Override
    public String toString() {
        // Never let a hash leak into logs via a naive toString() call.
        return "PasswordHash[REDACTED]";
    }

}

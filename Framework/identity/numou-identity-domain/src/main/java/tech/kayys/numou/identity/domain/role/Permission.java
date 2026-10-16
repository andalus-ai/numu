package tech.kayys.syirkah.identity.domain.role;

import java.util.Objects;

public record Permission(String value) {
    public Permission {
        Objects.requireNonNull(value, "value cannot be null");
        if (!value.matches("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+")) {
            throw new IllegalArgumentException("Permission must use a qualified lowercase name");
        }
    }

    public static Permission of(String value) {
        return new Permission(value);
    }
}

package tech.kayys.syirkah.company.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Company identifier.
 */
public record CompanyId(UUID value) implements DomainId<UUID>, Serializable {

    public CompanyId {
        Objects.requireNonNull(value, "CompanyId value cannot be null");
    }

    public static CompanyId of(UUID value) {
        return new CompanyId(value);
    }

    public static CompanyId generate() {
        return new CompanyId(UUID.randomUUID());
    }

    public static CompanyId fromString(String value) {
        return new CompanyId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "CompanyId{" + value + "}";
    }
}

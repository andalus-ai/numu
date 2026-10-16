package tech.kayys.syirkah.communication.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Communication template identifier.
 */
public record TemplateId(UUID value) implements DomainId<UUID>, Serializable {

    public TemplateId {
        Objects.requireNonNull(value, "TemplateId value cannot be null");
    }

    public static TemplateId of(UUID value) {
        return new TemplateId(value);
    }

    public static TemplateId generate() {
        return new TemplateId(UUID.randomUUID());
    }

    public static TemplateId fromString(String value) {
        return new TemplateId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "TemplateId{" + value + "}";
    }
}

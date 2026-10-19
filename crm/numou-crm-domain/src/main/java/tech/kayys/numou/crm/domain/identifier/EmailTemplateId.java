package tech.kayys.numou.crm.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record EmailTemplateId(UUID value) implements DomainId<UUID>, Serializable {

    public EmailTemplateId {
        Objects.requireNonNull(value, "EmailTemplateId value cannot be null");
    }

    public static EmailTemplateId of(UUID value) {
        return new EmailTemplateId(value);
    }

    public static EmailTemplateId generate() {
        return new EmailTemplateId(UUID.randomUUID());
    }

    public static EmailTemplateId fromString(String value) {
        return new EmailTemplateId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "EmailTemplateId{" + value + "}";
    }
}

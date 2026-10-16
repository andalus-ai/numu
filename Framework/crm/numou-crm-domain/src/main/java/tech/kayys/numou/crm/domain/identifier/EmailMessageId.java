package tech.kayys.numou.crm.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record EmailMessageId(UUID value) implements DomainId<UUID>, Serializable {

    public EmailMessageId {
        Objects.requireNonNull(value, "EmailMessageId value cannot be null");
    }

    public static EmailMessageId generate() {
        return new EmailMessageId(UUID.randomUUID());
    }

    public static EmailMessageId of(UUID value) {
        return new EmailMessageId(value);
    }

    public static EmailMessageId of(String value) {
        return new EmailMessageId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}

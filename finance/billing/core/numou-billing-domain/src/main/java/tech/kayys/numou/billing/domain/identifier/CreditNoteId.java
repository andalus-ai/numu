package tech.kayys.numou.billing.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record CreditNoteId(UUID value) implements DomainId<UUID>, Serializable {

    public CreditNoteId {
        Objects.requireNonNull(value, "CreditNoteId value cannot be null");
    }

    public static CreditNoteId of(UUID value) {
        return new CreditNoteId(value);
    }

    public static CreditNoteId generate() {
        return new CreditNoteId(UUID.randomUUID());
    }

    public static CreditNoteId fromString(String value) {
        return new CreditNoteId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "CreditNoteId{" + value + "}";
    }
}
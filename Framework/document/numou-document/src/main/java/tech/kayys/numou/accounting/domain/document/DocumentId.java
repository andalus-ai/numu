package tech.kayys.syirkah.accounting.domain.document;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a {@link Document} aggregate. */
public record DocumentId(String value) implements DomainId<String> {
    public DocumentId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("DocumentId must not be blank");
    }
    public static DocumentId generate() { return new DocumentId(UUID.randomUUID().toString()); }
    public static DocumentId of(String v) { return new DocumentId(v); }
}

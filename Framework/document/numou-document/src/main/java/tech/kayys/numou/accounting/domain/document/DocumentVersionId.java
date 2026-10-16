package tech.kayys.syirkah.accounting.domain.document;

import java.util.Objects;
import java.util.UUID;

/** Identity of a single immutable {@link DocumentVersion}. */
public record DocumentVersionId(String value) {
    public DocumentVersionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("DocumentVersionId must not be blank");
    }
    public static DocumentVersionId generate() { return new DocumentVersionId(UUID.randomUUID().toString()); }
    public static DocumentVersionId of(String v) { return new DocumentVersionId(v); }
}

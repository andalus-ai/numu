package tech.kayys.syirkah.accounting.domain.document;

import java.util.Objects;
import java.util.UUID;

/** Identity of a link binding a document to an audit entity. */
public record DocumentLinkId(String value) {
    public DocumentLinkId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("DocumentLinkId must not be blank");
    }
    public static DocumentLinkId generate() { return new DocumentLinkId(UUID.randomUUID().toString()); }
    public static DocumentLinkId of(String v) { return new DocumentLinkId(v); }
}

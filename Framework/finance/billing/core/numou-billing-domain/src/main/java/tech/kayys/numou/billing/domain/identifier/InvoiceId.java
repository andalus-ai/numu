package tech.kayys.numou.billing.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record InvoiceId(String value) implements DomainId<String>, Serializable {

    public InvoiceId {
        Objects.requireNonNull(value, "InvoiceId value cannot be null");
    }

    public static InvoiceId generate() {
        return new InvoiceId("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    public static InvoiceId of(String value) {
        return new InvoiceId(value);
    }

    @Override
    public String toString() {
        return "InvoiceId{" + value + "}";
    }
}

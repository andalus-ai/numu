package tech.kayys.numou.billing.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Invoice batch identifier for batch billing.
 */
public record InvoiceBatchId(UUID value) implements DomainId<UUID>, Serializable {

    public InvoiceBatchId {
        Objects.requireNonNull(value, "InvoiceBatchId value cannot be null");
    }

    public static InvoiceBatchId of(UUID value) {
        return new InvoiceBatchId(value);
    }

    public static InvoiceBatchId generate() {
        return new InvoiceBatchId(UUID.randomUUID());
    }

    public static InvoiceBatchId fromString(String value) {
        return new InvoiceBatchId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "InvoiceBatchId{" + value + "}";
    }
}
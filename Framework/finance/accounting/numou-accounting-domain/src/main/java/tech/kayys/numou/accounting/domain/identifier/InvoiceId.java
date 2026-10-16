package tech.kayys.syirkah.accounting.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Invoice identifier in Accounting context.
 */
public record InvoiceId(UUID value) implements DomainId<UUID> {

    public InvoiceId {
        Objects.requireNonNull(value, "InvoiceId value cannot be null");
    }

    public UUID getValue() {
        return value;
    }

    public static InvoiceId generate() {
        return new InvoiceId(UUID.randomUUID());
    }

    public static InvoiceId of(UUID value) {
        return new InvoiceId(value);
    }

    public static InvoiceId of(String value) {
        return new InvoiceId(UUID.fromString(value));
    }

    public static InvoiceId fromString(String value) {
        return new InvoiceId(UUID.fromString(value));
    }
}

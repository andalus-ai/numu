package tech.kayys.numou.education.school.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record FeeInvoiceId(UUID value) implements DomainId<UUID> {
    public static FeeInvoiceId of(UUID v) { return new FeeInvoiceId(v); }
    public static FeeInvoiceId generate() { return new FeeInvoiceId(UUID.randomUUID()); }
}

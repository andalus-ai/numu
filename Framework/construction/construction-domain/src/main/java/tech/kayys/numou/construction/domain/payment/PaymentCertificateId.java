package tech.kayys.syirkah.construction.domain.payment;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record PaymentCertificateId(UUID value) implements DomainId<UUID> {
    public PaymentCertificateId { Objects.requireNonNull(value, "Payment certificate id cannot be null"); }
    public static PaymentCertificateId generate() { return new PaymentCertificateId(UUID.randomUUID()); }
    public static PaymentCertificateId of(UUID value) { return new PaymentCertificateId(value); }
}

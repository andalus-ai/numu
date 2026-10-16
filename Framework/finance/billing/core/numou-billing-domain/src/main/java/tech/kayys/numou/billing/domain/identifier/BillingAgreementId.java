package tech.kayys.numou.billing.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record BillingAgreementId(UUID value) implements DomainId<UUID>, Serializable {

    public BillingAgreementId {
        Objects.requireNonNull(value, "BillingAgreementId value cannot be null");
    }

    public static BillingAgreementId of(UUID value) {
        return new BillingAgreementId(value);
    }

    public static BillingAgreementId generate() {
        return new BillingAgreementId(UUID.randomUUID());
    }

    public static BillingAgreementId fromString(String value) {
        return new BillingAgreementId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "BillingAgreementId{" + value + "}";
    }
}
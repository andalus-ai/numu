package tech.kayys.numou.billing.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Billing schedule identifier.
 */
public record BillingScheduleId(UUID value) implements DomainId<UUID>, Serializable {

    public BillingScheduleId {
        Objects.requireNonNull(value, "BillingScheduleId value cannot be null");
    }

    public static BillingScheduleId of(UUID value) {
        return new BillingScheduleId(value);
    }

    public static BillingScheduleId generate() {
        return new BillingScheduleId(UUID.randomUUID());
    }

    public static BillingScheduleId fromString(String value) {
        return new BillingScheduleId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "BillingScheduleId{" + value + "}";
    }
}
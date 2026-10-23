package tech.kayys.numou.fulfillment.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identity for a fulfillment request, independent of any sales or project module. */
public record FulfillmentOrderId(String value) {
    public FulfillmentOrderId {
        Objects.requireNonNull(value, "value is required");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Fulfillment order id must not be blank");
    }

    public static FulfillmentOrderId generate() {
        return new FulfillmentOrderId(UUID.randomUUID().toString());
    }

    public static FulfillmentOrderId of(String value) {
        return new FulfillmentOrderId(value);
    }
}

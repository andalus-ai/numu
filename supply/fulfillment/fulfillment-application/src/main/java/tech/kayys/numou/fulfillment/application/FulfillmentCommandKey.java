package tech.kayys.numou.fulfillment.application;

import java.util.Objects;

/** Caller-generated stable key reused for retries of the same logical command. */
public record FulfillmentCommandKey(String value) {
    public FulfillmentCommandKey {
        Objects.requireNonNull(value, "Command key is required");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Command key must not be blank");
        if (value.length() > 200) throw new IllegalArgumentException("Command key must not exceed 200 characters");
    }
}

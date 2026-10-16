package tech.kayys.syirkah.integration.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Identifies a webhook subscription belonging to an external system. */
public record WebhookSubscriptionId(UUID value) implements DomainId<UUID>, Serializable {

    public WebhookSubscriptionId {
        Objects.requireNonNull(value, "WebhookSubscriptionId value cannot be null");
    }

    public static WebhookSubscriptionId of(UUID value) {
        return new WebhookSubscriptionId(value);
    }

    public static WebhookSubscriptionId generate() {
        return new WebhookSubscriptionId(UUID.randomUUID());
    }

    public static WebhookSubscriptionId fromString(String value) {
        return new WebhookSubscriptionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "WebhookSubscriptionId{" + value + "}";
    }
}

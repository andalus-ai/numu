package tech.kayys.syirkah.integration.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.integration.domain.identifier.ExternalSystemId;
import tech.kayys.syirkah.integration.domain.identifier.WebhookSubscriptionId;

import java.time.Instant;
import java.util.Objects;

/**
 * A push subscription: "when this event type happens, call this endpoint".
 *
 * <p>Enables the subscription relationship in base01.md §P1-16: Numou can
 * push to a partner, or accept the partner pushing to it, without either
 * side polling.
 */
public final class WebhookSubscription extends AbstractAggregateRoot<WebhookSubscriptionId> {

    private static final long serialVersionUID = 1L;

    private ExternalSystemId externalSystemId;
    private String eventType;
    private String targetUrl;
    private boolean active;
    private String secretHandle;

    private WebhookSubscription() {
        super();
    }

    private WebhookSubscription(WebhookSubscriptionId id) {
        super(id);
        this.active = true;
    }

    /**
     * Subscribes an external system to one event type.
     *
     * @param secretHandle reference to the signing secret, resolved by the
     *                     credential resolver port - never the secret itself
     */
    public static WebhookSubscription subscribe(
            WebhookSubscriptionId id,
            ExternalSystemId externalSystemId,
            String eventType,
            String targetUrl,
            String secretHandle) {

        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(externalSystemId, "externalSystemId cannot be null");

        if (eventType == null || eventType.isBlank()) {
            throw new BusinessRuleViolation("Event type is required");
        }
        if (targetUrl == null || targetUrl.isBlank() || !targetUrl.startsWith("https://")) {
            throw new BusinessRuleViolation("Webhook target must be an https endpoint");
        }
        if (secretHandle == null || secretHandle.isBlank()) {
            throw new BusinessRuleViolation("A webhook signing secret is required");
        }

        final var subscription = new WebhookSubscription(id);
        subscription.externalSystemId = externalSystemId;
        subscription.eventType = eventType.trim();
        subscription.targetUrl = targetUrl.trim();
        subscription.secretHandle = secretHandle.trim();
        return subscription;
    }

    /** Rehydrates from persistence. */
    public static WebhookSubscription rehydrate(
            WebhookSubscriptionId id,
            ExternalSystemId externalSystemId,
            String eventType,
            String targetUrl,
            boolean active,
            String secretHandle) {

        final var subscription = new WebhookSubscription(id);
        subscription.externalSystemId = externalSystemId;
        subscription.eventType = eventType;
        subscription.targetUrl = targetUrl;
        subscription.active = active;
        subscription.secretHandle = secretHandle;
        return subscription;
    }

    /** Stops delivery while keeping the subscription record. */
    public void pause() {
        if (!active) {
            throw new InvalidStateException("Webhook subscription is already paused");
        }
        this.active = false;
        touch();
    }

    /** Resumes delivery. */
    public void resume() {
        if (active) {
            throw new InvalidStateException("Webhook subscription is already active");
        }
        this.active = true;
        touch();
    }

    /** Rotates the signing secret handle after a compromise or expiry. */
    public void rotateSecret(String newSecretHandle) {
        if (newSecretHandle == null || newSecretHandle.isBlank()) {
            throw new BusinessRuleViolation("A webhook signing secret is required");
        }
        this.secretHandle = newSecretHandle.trim();
        touch();
    }

    /** True when an event of the given type should be delivered. */
    public boolean matches(String candidateEventType) {
        return active && eventType.equals(candidateEventType);
    }

    public ExternalSystemId getExternalSystemId() {
        return externalSystemId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public boolean isActive() {
        return active;
    }

    public String getSecretHandle() {
        return secretHandle;
    }

    private void touch() {
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "WebhookSubscription{id=" + getId()
                + ", externalSystemId=" + externalSystemId
                + ", eventType='" + eventType + '\''
                + ", active=" + active
                + '}';
    }
}

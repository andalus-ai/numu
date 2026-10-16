package tech.kayys.syirkah.commerce.pricing.domain;

import tech.kayys.syirkah.commerce.configuration.domain.ProductConfiguration;
import tech.kayys.syirkah.commerce.offering.domain.ChannelId;
import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.foundation.domain.valueobject.Quantity;

import java.time.Instant;
import java.util.Objects;

/**
 * Everything a pricing rule needs to resolve a price — and nothing more.
 *
 * Mirrors the blueprint's {@code PricingContext(offeringId, quantity,
 * customer, channel, configuration, at)}. Customer is an opaque
 * reference (UUID string) on purpose: pricing must not depend on the
 * CRM aggregate, only on the identity the caller passes in. Channel
 * reuses the offering capability's {@link ChannelId}; the customer
 * selection arrives as a validated {@link ProductConfiguration}.
 */
public record PricingContext(
        ProductOfferingId offeringId,
        Quantity quantity,
        String customerRef,
        ChannelId channel,
        ProductConfiguration configuration,
        Instant at
) {

    public PricingContext {
        Objects.requireNonNull(offeringId, "offeringId cannot be null");
        Objects.requireNonNull(quantity, "quantity cannot be null");
        Objects.requireNonNull(channel, "channel cannot be null");
        Objects.requireNonNull(configuration, "configuration cannot be null");
        Objects.requireNonNull(at, "at cannot be null");
        if (customerRef != null && customerRef.isBlank()) {
            throw new IllegalArgumentException("customerRef cannot be blank");
        }
    }
}

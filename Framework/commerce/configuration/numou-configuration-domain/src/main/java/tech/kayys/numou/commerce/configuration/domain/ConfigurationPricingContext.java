package tech.kayys.syirkah.commerce.configuration.domain;

import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.foundation.domain.valueobject.Quantity;

import java.time.Instant;
import java.util.Objects;

/**
 * Bridge value object handed to pricing: which offering is being
 * configured, with which validated selections, in which quantity,
 * at which point in time.
 */
public record ConfigurationPricingContext(
        ProductOfferingId offeringId,
        ProductConfiguration configuration,
        Quantity quantity,
        Instant at
) {

    public ConfigurationPricingContext {
        Objects.requireNonNull(offeringId, "offeringId cannot be null");
        Objects.requireNonNull(configuration, "configuration cannot be null");
        Objects.requireNonNull(quantity, "quantity cannot be null");
        Objects.requireNonNull(at, "at cannot be null");
    }
}

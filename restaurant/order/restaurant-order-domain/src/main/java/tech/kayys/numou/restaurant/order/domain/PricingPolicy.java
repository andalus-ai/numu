package tech.kayys.numou.restaurant.order.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Tax rules frozen onto an order when it is opened, so changing the venue's rates mid-service does not reprice
 * checks already on the table.
 *
 * @param fractionDigits minor-unit digits used for rounding (IDR: 0, USD: 2)
 */
public record PricingPolicy(String currency, int fractionDigits, BigDecimal taxRate, BigDecimal serviceChargeRate, boolean taxOnServiceCharge) {

    public PricingPolicy {
        Objects.requireNonNull(currency, "currency cannot be null");
        Objects.requireNonNull(taxRate, "taxRate cannot be null");
        Objects.requireNonNull(serviceChargeRate, "serviceChargeRate cannot be null");
        if (fractionDigits < 0 || fractionDigits > 4) {
            throw new IllegalArgumentException("fractionDigits must be 0-4");
        }
    }
}

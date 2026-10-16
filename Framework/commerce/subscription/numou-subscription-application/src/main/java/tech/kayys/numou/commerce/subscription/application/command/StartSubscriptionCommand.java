package tech.kayys.syirkah.commerce.subscription.application.command;

import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.subscription.domain.BillingCycle;
import tech.kayys.syirkah.commerce.subscription.domain.RenewalPolicy;
import tech.kayys.syirkah.foundation.application.command.Command;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Start a subscription for a customer on an offering (blueprint §9).
 */
public record StartSubscriptionCommand(
        String customerRef,
        ProductOfferingId offeringId,
        long quantity,
        BillingCycle billingCycle,
        RenewalPolicy renewalPolicy,
        LocalDate startDate
) implements Command {

    public StartSubscriptionCommand {
        Objects.requireNonNull(customerRef, "customerRef cannot be null");
        Objects.requireNonNull(offeringId, "offeringId cannot be null");
        Objects.requireNonNull(billingCycle, "billingCycle cannot be null");
        Objects.requireNonNull(renewalPolicy, "renewalPolicy cannot be null");
        Objects.requireNonNull(startDate, "startDate cannot be null");
        if (customerRef.isBlank()) {
            throw new IllegalArgumentException("customerRef cannot be blank");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
    }
}

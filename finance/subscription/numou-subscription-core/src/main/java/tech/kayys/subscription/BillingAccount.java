package tech.kayys.billing.subscription;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Billing account a subscription hangs off. Kept deliberately thin:
 * the subscription flow only needs the account's billing cycle to
 * default a new subscription's cycle.
 */
@Entity
class BillingAccount extends PanacheEntity {

    @Enumerated(EnumType.STRING)
    public BillingCycle billingCycle;

    public enum BillingCycle {
        WEEKLY, MONTHLY, QUARTERLY, ANNUAL
    }
}

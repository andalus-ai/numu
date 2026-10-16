package tech.kayys.syirkah.commerce.subscription.domain;

import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.subscription.domain.event.SubscriptionCancelled;
import tech.kayys.syirkah.commerce.subscription.domain.event.SubscriptionRenewed;
import tech.kayys.syirkah.commerce.subscription.domain.event.SubscriptionStarted;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Subscription aggregate root (Commerce Phase G, blueprint §9).
 *
 * <pre>
 * Product -&gt; Offering -&gt; Subscription -&gt; Billing -&gt; Entitlement
 * </pre>
 *
 * Contains exactly the blueprint's shape: customer, offering, start
 * date, renewal policy, billing cycle, quantity, status — plus the
 * current entitlement period derived from the cycle. Customer is an
 * opaque reference (no CRM dependency); the offering is referenced
 * by id only. Billing itself belongs to the finance capability: the
 * billing chain consumes this aggregate through events, it does not
 * live here.
 */
public final class Subscription extends AbstractAggregateRoot<SubscriptionId> {

    private final String customerRef;
    private final ProductOfferingId offeringId;
    private final long quantity;
    private final BillingCycle billingCycle;
    private final RenewalPolicy renewalPolicy;
    private final LocalDate startDate;

    private SubscriptionStatus status;
    private DateRange currentPeriod;

    private Subscription(
            SubscriptionId id,
            String customerRef,
            ProductOfferingId offeringId,
            long quantity,
            BillingCycle billingCycle,
            RenewalPolicy renewalPolicy,
            LocalDate startDate
    ) {
        super(id);
        this.customerRef = requireText(customerRef, "customerRef");
        this.offeringId = Objects.requireNonNull(offeringId, "offeringId cannot be null");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        this.quantity = quantity;
        this.billingCycle = Objects.requireNonNull(billingCycle, "billingCycle cannot be null");
        this.renewalPolicy = Objects.requireNonNull(renewalPolicy, "renewalPolicy cannot be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate cannot be null");
        this.status = SubscriptionStatus.ACTIVE;
        this.currentPeriod = firstPeriod(startDate, billingCycle);
    }

    public static Subscription start(
            SubscriptionId id,
            String customerRef,
            ProductOfferingId offeringId,
            long quantity,
            BillingCycle billingCycle,
            RenewalPolicy renewalPolicy,
            LocalDate startDate
    ) {
        var subscription = new Subscription(
                id, customerRef, offeringId, quantity,
                billingCycle, renewalPolicy, startDate);
        subscription.raise(new SubscriptionStarted(
                UUID.randomUUID(), Instant.now(), id, offeringId));
        return subscription;
    }

    /** True only while entitled: an in-force status AND a live period. */
    public boolean isActiveOn(LocalDate date) {
        Objects.requireNonNull(date, "date cannot be null");
        var inForce = status == SubscriptionStatus.ACTIVE
                || status == SubscriptionStatus.PAST_DUE;
        return inForce && currentPeriod.contains(date);
    }

    /**
     * Rolls the entitlement period forward by one billing cycle and
     * heals a PAST_DUE status. Callable by the auto-renew job
     * (AUTO_RENEW) or by an explicit command (MANUAL) — the policy
     * only decides who calls this, not the math.
     */
    public void renew() {
        if (status != SubscriptionStatus.ACTIVE && status != SubscriptionStatus.PAST_DUE) {
            throw new InvalidStateException(
                    "Cannot renew a " + status + " subscription");
        }
        var nextStart = currentPeriod.end().plusDays(1);
        this.currentPeriod = new DateRange(
                nextStart, billingCycle.nextFrom(nextStart).minusDays(1));
        this.status = SubscriptionStatus.ACTIVE;
        this.updatedAt = Instant.now();
        raise(new SubscriptionRenewed(
                UUID.randomUUID(), Instant.now(), id(), offeringId));
    }

    public void markPastDue() {
        if (status == SubscriptionStatus.PAST_DUE) {
            return;
        }
        if (status != SubscriptionStatus.ACTIVE) {
            throw new InvalidStateException(
                    "Only an active subscription can become past due");
        }
        this.status = SubscriptionStatus.PAST_DUE;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (status != SubscriptionStatus.ACTIVE && status != SubscriptionStatus.PAST_DUE) {
            throw new InvalidStateException(
                    "Cannot cancel a " + status + " subscription");
        }
        this.status = SubscriptionStatus.CANCELLED;
        this.updatedAt = Instant.now();
        raise(new SubscriptionCancelled(
                UUID.randomUUID(), Instant.now(), id(), offeringId));
    }

    public void expire() {
        if (status == SubscriptionStatus.EXPIRED) {
            throw new InvalidStateException("Subscription is already expired");
        }
        this.status = SubscriptionStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    private static DateRange firstPeriod(LocalDate start, BillingCycle cycle) {
        return new DateRange(start, cycle.nextFrom(start).minusDays(1));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }

    public String customerRef() {
        return customerRef;
    }

    public ProductOfferingId offeringId() {
        return offeringId;
    }

    public long quantity() {
        return quantity;
    }

    public BillingCycle billingCycle() {
        return billingCycle;
    }

    public RenewalPolicy renewalPolicy() {
        return renewalPolicy;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public SubscriptionStatus status() {
        return status;
    }

    public DateRange currentPeriod() {
        return currentPeriod;
    }
}

package tech.kayys.syirkah.commerce.subscription.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.subscription.domain.event.SubscriptionCancelled;
import tech.kayys.syirkah.commerce.subscription.domain.event.SubscriptionRenewed;
import tech.kayys.syirkah.commerce.subscription.domain.event.SubscriptionStarted;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Subscription aggregate (blueprint §9)")
class SubscriptionTest {

    private static final ProductOfferingId OFFERING = ProductOfferingId.generate();
    private static final LocalDate START = LocalDate.of(2026, 6, 15);

    private static Subscription monthly() {
        return Subscription.start(
                SubscriptionId.generate(), "customer-42", OFFERING,
                5, BillingCycle.MONTHLY, RenewalPolicy.AUTO_RENEW, START);
    }

    @Test
    void startComputesFirstPeriodAndRaisesEvent() {
        var subscription = monthly();

        assertEquals(SubscriptionStatus.ACTIVE, subscription.status());
        assertEquals(LocalDate.of(2026, 7, 14), subscription.currentPeriod().end());
        assertTrue(subscription.isActiveOn(START));
        assertTrue(subscription.isActiveOn(LocalDate.of(2026, 7, 14)));
        assertFalse(subscription.isActiveOn(LocalDate.of(2026, 7, 15)));
        assertInstanceOf(SubscriptionStarted.class,
                subscription.pullDomainEvents().getFirst());
    }

    @Test
    void renewRollsPeriodAndHealsPastDue() {
        var subscription = monthly();
        subscription.markPastDue();
        assertEquals(SubscriptionStatus.PAST_DUE, subscription.status());

        subscription.renew();

        assertEquals(SubscriptionStatus.ACTIVE, subscription.status());
        assertEquals(LocalDate.of(2026, 7, 15), subscription.currentPeriod().start());
        assertEquals(LocalDate.of(2026, 8, 14), subscription.currentPeriod().end());
        assertTrue(subscription.pullDomainEvents().stream()
                .anyMatch(SubscriptionRenewed.class::isInstance));
    }

    @Test
    void annualCycleRollsByAYear() {
        var subscription = Subscription.start(
                SubscriptionId.generate(), "customer-42", OFFERING,
                1, BillingCycle.ANNUAL, RenewalPolicy.MANUAL, START);

        assertEquals(LocalDate.of(2027, 6, 14), subscription.currentPeriod().end());
    }

    @Test
    void lifecycleGuardsAreEnforced() {
        var subscription = monthly();

        subscription.cancel();
        assertEquals(SubscriptionStatus.CANCELLED, subscription.status());
        assertFalse(subscription.isActiveOn(START));
        assertTrue(subscription.pullDomainEvents().stream()
                .anyMatch(SubscriptionCancelled.class::isInstance));

        assertThrows(InvalidStateException.class, subscription::cancel);
        assertThrows(InvalidStateException.class, subscription::renew);

        subscription.expire();
        assertEquals(SubscriptionStatus.EXPIRED, subscription.status());
        assertThrows(InvalidStateException.class, subscription::expire);
    }

    @Test
    void pastDueStillGrantsAccessWithinPeriod() {
        var subscription = monthly();
        subscription.markPastDue();

        assertTrue(subscription.isActiveOn(LocalDate.of(2026, 6, 20)));
        assertFalse(subscription.isActiveOn(LocalDate.of(2026, 8, 1)));
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> Subscription.start(
                SubscriptionId.generate(), "customer-42", OFFERING,
                0, BillingCycle.MONTHLY, RenewalPolicy.AUTO_RENEW, START));
        assertThrows(IllegalArgumentException.class, () -> Subscription.start(
                SubscriptionId.generate(), "  ", OFFERING,
                1, BillingCycle.MONTHLY, RenewalPolicy.AUTO_RENEW, START));
    }
}

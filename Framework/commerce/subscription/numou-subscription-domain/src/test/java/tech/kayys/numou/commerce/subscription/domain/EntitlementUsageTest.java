package tech.kayys.syirkah.commerce.subscription.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Entitlement + usage metering (blueprint §10)")
class EntitlementUsageTest {

    private static final ProductOfferingId OFFERING = ProductOfferingId.generate();
    private static final LocalDate START = LocalDate.of(2026, 6, 15);
    private static final DateRange PERIOD = new DateRange(
            START, LocalDate.of(2026, 7, 14));
    private static final Entitlement STORAGE = Entitlement.limited("STORAGE", 100);
    private static final Entitlement API_ACCESS = Entitlement.flag("API_ACCESS");

    private static UsageEntry entry(String code, long units, LocalDate date) {
        return new UsageEntry(
                code, BigDecimal.valueOf(units),
                date.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private static Subscription subscription() {
        return Subscription.start(
                SubscriptionId.generate(), "customer-42", OFFERING,
                1, BillingCycle.MONTHLY, RenewalPolicy.AUTO_RENEW, START);
    }

    @Test
    void meterAccumulatesUpToQuotaAndRejectsOverflow() {
        var meter = new UsageMeter(SubscriptionId.generate(), PERIOD);

        assertEquals(0, BigDecimal.valueOf(60).compareTo(
                meter.record(entry("STORAGE", 60, LocalDate.of(2026, 6, 16)), STORAGE)));
        assertEquals(0, BigDecimal.valueOf(100).compareTo(
                meter.record(entry("STORAGE", 40, LocalDate.of(2026, 6, 17)), STORAGE)));
        assertThrows(IllegalArgumentException.class,
                () -> meter.record(entry("STORAGE", 1, LocalDate.of(2026, 6, 18)), STORAGE));

        assertEquals(0, BigDecimal.ZERO.compareTo(meter.consumedOf("STORAGE_MISSING")));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                meter.remaining(STORAGE).orElseThrow()));
    }

    @Test
    void unmeteredEntitlementNeverRunsOut() {
        var meter = new UsageMeter(SubscriptionId.generate(), PERIOD);
        meter.record(entry("API_ACCESS", 999, LocalDate.of(2026, 6, 16)), API_ACCESS);

        assertTrue(meter.remaining(API_ACCESS).isEmpty());
    }

    @Test
    void meterRejectsForeignCodeAndOutOfPeriodEntries() {
        var meter = new UsageMeter(SubscriptionId.generate(), PERIOD);

        assertThrows(IllegalArgumentException.class,
                () -> meter.record(entry("API_ACCESS", 1, LocalDate.of(2026, 6, 16)), STORAGE));
        assertThrows(IllegalArgumentException.class,
                () -> meter.record(entry("STORAGE", 1, LocalDate.of(2026, 8, 1)), STORAGE));
        assertThrows(IllegalArgumentException.class,
                () -> meter.record(entry("STORAGE", 0, LocalDate.of(2026, 6, 16)), STORAGE));
    }

    @Test
    void accessChecksGrantAndDenyWithStableReasons() {
        var access = new EntitlementAccess();
        var subscription = subscription();
        var plan = List.of(STORAGE, API_ACCESS);

        assertEquals(AccessDecision.ALLOWED, access.check(
                        subscription, plan, "API_ACCESS",
                        new UsageMeter(subscription.id(), PERIOD),
                        LocalDate.of(2026, 6, 20)).reason());

        assertEquals(AccessDecision.ENTITLEMENT_NOT_FOUND, access.check(
                        subscription, plan, "ADVANCED_REPORTING",
                        new UsageMeter(subscription.id(), PERIOD),
                        LocalDate.of(2026, 6, 20)).reason());

        assertEquals(AccessDecision.SUBSCRIPTION_NOT_IN_FORCE, access.check(
                        subscription, plan, "API_ACCESS",
                        new UsageMeter(subscription.id(), PERIOD),
                        LocalDate.of(2026, 8, 1)).reason());
    }

    @Test
    void exactlyExhaustedQuotaIsDenied() {
        var access = new EntitlementAccess();
        var subscription = subscription();
        var meter = new UsageMeter(subscription.id(), PERIOD);
        meter.record(entry("STORAGE", 100, LocalDate.of(2026, 6, 20)), STORAGE);

        var decision = access.check(
                subscription, List.of(STORAGE), "STORAGE",
                meter, LocalDate.of(2026, 6, 21));

        assertFalse(decision.allowed());
        assertEquals(AccessDecision.QUOTA_EXHAUSTED, decision.reason());
    }

    @Test
    void cancelledSubscriptionLosesAccessEvenInsidePeriod() {
        var access = new EntitlementAccess();
        var subscription = subscription();
        subscription.cancel();

        var decision = access.check(
                subscription, List.of(API_ACCESS), "API_ACCESS",
                new UsageMeter(subscription.id(), PERIOD),
                LocalDate.of(2026, 6, 20));

        assertFalse(decision.allowed());
        assertEquals(AccessDecision.SUBSCRIPTION_NOT_IN_FORCE, decision.reason());
    }
}

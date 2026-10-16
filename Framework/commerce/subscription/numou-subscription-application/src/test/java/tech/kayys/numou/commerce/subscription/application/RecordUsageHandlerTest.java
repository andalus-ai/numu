package tech.kayys.syirkah.commerce.subscription.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.subscription.application.command.RecordUsageCommand;
import tech.kayys.syirkah.commerce.subscription.application.command.StartSubscriptionCommand;
import tech.kayys.syirkah.commerce.subscription.application.handler.RecordUsageHandler;
import tech.kayys.syirkah.commerce.subscription.application.handler.StartSubscriptionHandler;
import tech.kayys.syirkah.commerce.subscription.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.commerce.subscription.application.support.StubOfferingRepository;
import tech.kayys.syirkah.commerce.subscription.application.support.StubPlanEntitlements;
import tech.kayys.syirkah.commerce.subscription.application.support.StubSubscriptionRepository;
import tech.kayys.syirkah.commerce.subscription.application.support.StubUsageMeters;
import tech.kayys.syirkah.commerce.subscription.domain.BillingCycle;
import tech.kayys.syirkah.commerce.subscription.domain.Entitlement;
import tech.kayys.syirkah.commerce.subscription.domain.RenewalPolicy;
import tech.kayys.syirkah.commerce.subscription.domain.SubscriptionId;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RecordUsage handler (blueprint §9 Usage)")
class RecordUsageHandlerTest {

    private final StubOfferingRepository offerings = new StubOfferingRepository();
    private final StubSubscriptionRepository subscriptions = new StubSubscriptionRepository();
    private final StubPlanEntitlements entitlements = new StubPlanEntitlements();
    private final StubUsageMeters meters = new StubUsageMeters();
    private final RecordingEventPublisher events = new RecordingEventPublisher();

    private SubscriptionId subscriptionId;

    @BeforeEach
    void setUp() {
        var offering = offerings.activeOffering(ProductId.generate());
        entitlements.grant(offering.id(),
                Entitlement.flag("API_ACCESS"),
                Entitlement.limited("STORAGE", 100));
        subscriptionId = new StartSubscriptionHandler(offerings, subscriptions, events)
                .handle(new StartSubscriptionCommand(
                        "customer-42", offering.id(), 1,
                        BillingCycle.MONTHLY, RenewalPolicy.AUTO_RENEW,
                        LocalDate.of(2026, 6, 15)))
                .await().indefinitely().orElseThrow();
    }

    private tech.kayys.syirkah.foundation.application.result.Result<UsageReceipt> record(
            String code, String units, Instant at) {
        return new RecordUsageHandler(subscriptions, entitlements, meters)
                .handle(new RecordUsageCommand(
                        subscriptionId, code, new BigDecimal(units), at))
                .await().indefinitely();
    }

    @Test
    void accumulatesUsageAndReportsRemainingQuota() {
        var result = record("STORAGE", "60", Instant.parse("2026-06-20T10:00:00Z"));

        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("60").compareTo(result.orElseThrow().consumed()));
        assertEquals(0, new BigDecimal("40").compareTo(
                result.orElseThrow().remaining().orElseThrow()));
    }

    @Test
    void rejectsOverflowUnentitledAndOutOfPeriodUsage() {
        assertTrue(record("STORAGE", "60",
                Instant.parse("2026-06-20T10:00:00Z")).isSuccess());
        assertTrue(record("STORAGE", "50",
                Instant.parse("2026-06-21T10:00:00Z")).isFailure());
        assertTrue(record("ADVANCED_REPORTING", "1",
                Instant.parse("2026-06-21T10:00:00Z")).isFailure());
        assertTrue(record("STORAGE", "1",
                Instant.parse("2026-08-01T10:00:00Z")).isFailure());
        assertFalse(record("STORAGE", "1",
                Instant.parse("2026-08-01T10:00:00Z")).isSuccess());
    }

    @Test
    void rejectsUsageForUnknownSubscription() {
        var result = new RecordUsageHandler(subscriptions, entitlements, meters)
                .handle(new RecordUsageCommand(
                        SubscriptionId.generate(), "STORAGE", BigDecimal.ONE,
                        Instant.parse("2026-06-20T10:00:00Z")))
                .await().indefinitely();

        assertTrue(result.isFailure());
    }
}

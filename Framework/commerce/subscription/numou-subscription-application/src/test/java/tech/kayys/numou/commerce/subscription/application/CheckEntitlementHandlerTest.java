package tech.kayys.syirkah.commerce.subscription.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.subscription.application.command.RecordUsageCommand;
import tech.kayys.syirkah.commerce.subscription.application.command.StartSubscriptionCommand;
import tech.kayys.syirkah.commerce.subscription.application.handler.CheckEntitlementHandler;
import tech.kayys.syirkah.commerce.subscription.application.handler.RecordUsageHandler;
import tech.kayys.syirkah.commerce.subscription.application.handler.StartSubscriptionHandler;
import tech.kayys.syirkah.commerce.subscription.application.query.CheckEntitlementQuery;
import tech.kayys.syirkah.commerce.subscription.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.commerce.subscription.application.support.StubOfferingRepository;
import tech.kayys.syirkah.commerce.subscription.application.support.StubPlanEntitlements;
import tech.kayys.syirkah.commerce.subscription.application.support.StubSubscriptionRepository;
import tech.kayys.syirkah.commerce.subscription.application.support.StubUsageMeters;
import tech.kayys.syirkah.commerce.subscription.domain.AccessDecision;
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

@DisplayName("CheckEntitlement query handler (blueprint §10)")
class CheckEntitlementHandlerTest {

    private final StubOfferingRepository offerings = new StubOfferingRepository();
    private final StubSubscriptionRepository subscriptions = new StubSubscriptionRepository();
    private final StubPlanEntitlements entitlements = new StubPlanEntitlements();
    private final StubUsageMeters meters = new StubUsageMeters();
    private final RecordingEventPublisher events = new RecordingEventPublisher();

    private SubscriptionId subscriptionId;
    private CheckEntitlementHandler handler;

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
        handler = new CheckEntitlementHandler(subscriptions, entitlements, meters);
    }

    private AccessDecision check(String code, LocalDate at) {
        var result = handler.handle(new CheckEntitlementQuery(subscriptionId, code, at))
                .await().indefinitely();
        assertTrue(result.isSuccess());
        return result.orElseThrow();
    }

    @Test
    void grantsFlagAndMeteredEntitlementsWhileInForce() {
        var decision = check("API_ACCESS", LocalDate.of(2026, 6, 20));

        assertTrue(decision.allowed());
        assertEquals(AccessDecision.ALLOWED, decision.reason());
        assertTrue(check("STORAGE", LocalDate.of(2026, 6, 20)).allowed());
    }

    @Test
    void deniesUnknownEntitlementAndLapsedDates() {
        assertEquals(AccessDecision.ENTITLEMENT_NOT_FOUND,
                check("ADVANCED_REPORTING", LocalDate.of(2026, 6, 20)).reason());
        assertEquals(AccessDecision.SUBSCRIPTION_NOT_IN_FORCE,
                check("API_ACCESS", LocalDate.of(2026, 8, 1)).reason());
    }

    @Test
    void deniesAfterQuotaConsumedToTheLimit() {
        new RecordUsageHandler(subscriptions, entitlements, meters)
                .handle(new RecordUsageCommand(
                        subscriptionId, "STORAGE", new BigDecimal("100"),
                        Instant.parse("2026-06-20T10:00:00Z")))
                .await().indefinitely();

        var decision = check("STORAGE", LocalDate.of(2026, 6, 21));

        assertFalse(decision.allowed());
        assertEquals(AccessDecision.QUOTA_EXHAUSTED, decision.reason());
    }

    @Test
    void failsForUnknownSubscription() {
        var result = handler.handle(new CheckEntitlementQuery(
                        SubscriptionId.generate(), "API_ACCESS", LocalDate.of(2026, 6, 20)))
                .await().indefinitely();

        assertTrue(result.isFailure());
    }
}

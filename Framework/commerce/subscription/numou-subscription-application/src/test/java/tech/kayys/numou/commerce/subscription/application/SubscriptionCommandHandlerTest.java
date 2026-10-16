package tech.kayys.syirkah.commerce.subscription.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.subscription.application.command.RenewSubscriptionCommand;
import tech.kayys.syirkah.commerce.subscription.application.command.StartSubscriptionCommand;
import tech.kayys.syirkah.commerce.subscription.application.handler.RenewSubscriptionHandler;
import tech.kayys.syirkah.commerce.subscription.application.handler.StartSubscriptionHandler;
import tech.kayys.syirkah.commerce.subscription.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.commerce.subscription.application.support.StubOfferingRepository;
import tech.kayys.syirkah.commerce.subscription.application.support.StubPlanEntitlements;
import tech.kayys.syirkah.commerce.subscription.application.support.StubSubscriptionRepository;
import tech.kayys.syirkah.commerce.subscription.application.support.StubUsageMeters;
import tech.kayys.syirkah.commerce.subscription.domain.BillingCycle;
import tech.kayys.syirkah.commerce.subscription.domain.Entitlement;
import tech.kayys.syirkah.commerce.subscription.domain.RenewalPolicy;
import tech.kayys.syirkah.commerce.subscription.domain.SubscriptionStatus;
import tech.kayys.syirkah.commerce.subscription.domain.event.SubscriptionStarted;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Subscription start + renew handlers (blueprint §9)")
class SubscriptionCommandHandlerTest {

    private final StubOfferingRepository offerings = new StubOfferingRepository();
    private final StubSubscriptionRepository subscriptions = new StubSubscriptionRepository();
    private final StubPlanEntitlements entitlements = new StubPlanEntitlements();
    private final StubUsageMeters meters = new StubUsageMeters();
    private final RecordingEventPublisher events = new RecordingEventPublisher();

    private StartSubscriptionHandler startHandler;
    private RenewSubscriptionHandler renewHandler;

    @BeforeEach
    void setUp() {
        startHandler = new StartSubscriptionHandler(offerings, subscriptions, events);
        renewHandler = new RenewSubscriptionHandler(subscriptions, events);
    }

    private ProductOfferingId activePlan() {
        var offering = offerings.activeOffering(ProductId.generate());
        entitlements.grant(offering.id(),
                Entitlement.flag("API_ACCESS"),
                Entitlement.limited("STORAGE", 100));
        return offering.id();
    }

    private tech.kayys.syirkah.commerce.subscription.domain.SubscriptionId start(
            ProductOfferingId offeringId) {
        return startHandler.handle(new StartSubscriptionCommand(
                        "customer-42", offeringId, 5,
                        BillingCycle.MONTHLY, RenewalPolicy.AUTO_RENEW,
                        LocalDate.of(2026, 6, 15)))
                .await().indefinitely().orElseThrow();
    }

    @Test
    void startsSubscriptionAgainstActiveOffering() {
        var subscriptionId = start(activePlan());

        var saved = subscriptions.get(subscriptionId).orElseThrow();
        assertEquals(SubscriptionStatus.ACTIVE, saved.status());
        assertEquals(5, saved.quantity());
        assertEquals(LocalDate.of(2026, 7, 14), saved.currentPeriod().end());
        assertTrue(events.published().stream()
                .anyMatch(SubscriptionStarted.class::isInstance));
    }

    @Test
    void rejectsUnknownAndDraftOfferings() {
        var missing = startHandler.handle(new StartSubscriptionCommand(
                        "customer-42", ProductOfferingId.generate(), 1,
                        BillingCycle.MONTHLY, RenewalPolicy.AUTO_RENEW,
                        LocalDate.of(2026, 6, 15)))
                .await().indefinitely();
        assertTrue(missing.isFailure());

        var draft = offerings.draftOffering(ProductId.generate());
        var rejected = startHandler.handle(new StartSubscriptionCommand(
                        "customer-42", draft.id(), 1,
                        BillingCycle.MONTHLY, RenewalPolicy.AUTO_RENEW,
                        LocalDate.of(2026, 6, 15)))
                .await().indefinitely();
        assertTrue(rejected.isFailure());
    }

    @Test
    void renewRollsThePeriodForward() {
        var subscriptionId = start(activePlan());

        var result = renewHandler.handle(new RenewSubscriptionCommand(subscriptionId))
                .await().indefinitely();

        assertTrue(result.isSuccess());
        var saved = subscriptions.get(subscriptionId).orElseThrow();
        assertEquals(LocalDate.of(2026, 7, 15), saved.currentPeriod().start());
        assertEquals(LocalDate.of(2026, 8, 14), saved.currentPeriod().end());
    }
}

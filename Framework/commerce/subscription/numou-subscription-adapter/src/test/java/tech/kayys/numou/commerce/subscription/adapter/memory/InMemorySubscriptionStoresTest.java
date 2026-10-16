package tech.kayys.syirkah.commerce.subscription.adapter.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.subscription.domain.BillingCycle;
import tech.kayys.syirkah.commerce.subscription.domain.Entitlement;
import tech.kayys.syirkah.commerce.subscription.domain.RenewalPolicy;
import tech.kayys.syirkah.commerce.subscription.domain.Subscription;
import tech.kayys.syirkah.commerce.subscription.domain.SubscriptionId;
import tech.kayys.syirkah.commerce.subscription.domain.UsageEntry;
import tech.kayys.syirkah.commerce.subscription.domain.UsageMeter;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("In-memory subscription stores")
class InMemorySubscriptionStoresTest {

    @Test
    void subscriptionRepositoryRoundTripsAndDeletes() {
        var repository = new InMemorySubscriptionRepository();
        var subscription = Subscription.start(
                SubscriptionId.generate(), "customer-42",
                ProductOfferingId.generate(), 1,
                BillingCycle.MONTHLY, RenewalPolicy.AUTO_RENEW,
                LocalDate.of(2026, 6, 15));
        repository.save(subscription).toCompletableFuture().join();

        assertTrue(repository.existsById(subscription.id()).toCompletableFuture().join());
        assertEquals(subscription, repository.findById(subscription.id())
                .toCompletableFuture().join().orElseThrow());

        repository.deleteById(subscription.id()).toCompletableFuture().join();
        assertFalse(repository.findById(subscription.id())
                .toCompletableFuture().join().isPresent());
    }

    @Test
    void planStoreReturnsNothingForUnknownOffering() {
        var store = new InMemoryPlanEntitlementStore();
        store.grant(ProductOfferingId.generate(), Entitlement.flag("API_ACCESS"));

        assertTrue(store.findEntitlements(ProductOfferingId.generate())
                .toCompletableFuture().join().isEmpty());
    }

    @Test
    void meterStoreIsPeriodScoped() {
        var store = new InMemoryUsageMeterStore();
        var subscriptionId = SubscriptionId.generate();
        var period = new DateRange(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 7, 14));
        var meter = new UsageMeter(subscriptionId, period);
        meter.record(new UsageEntry("STORAGE", BigDecimal.TEN,
                Instant.parse("2026-06-20T10:00:00Z")), Entitlement.limited("STORAGE", 100));

        store.save(meter).toCompletableFuture().join();

        assertTrue(store.find(subscriptionId, period).toCompletableFuture().join().isPresent());
        assertTrue(store.find(subscriptionId, new DateRange(
                        LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 14)))
                .toCompletableFuture().join().isEmpty());
    }
}

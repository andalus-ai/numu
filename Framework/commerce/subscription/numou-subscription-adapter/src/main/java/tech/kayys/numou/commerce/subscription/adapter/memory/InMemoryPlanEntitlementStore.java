package tech.kayys.syirkah.commerce.subscription.adapter.memory;

import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.subscription.domain.Entitlement;
import tech.kayys.syirkah.commerce.subscription.spi.port.PlanEntitlementPort;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory plan catalog: offering id -&gt; granted entitlements.
 */
public final class InMemoryPlanEntitlementStore implements PlanEntitlementPort {

    private final Map<ProductOfferingId, List<Entitlement>> plans = new ConcurrentHashMap<>();

    public void grant(ProductOfferingId offeringId, Entitlement... entitlements) {
        plans.put(offeringId, List.of(entitlements));
    }

    @Override
    public CompletionStage<List<Entitlement>> findEntitlements(
            ProductOfferingId offeringId) {
        return CompletableFuture.completedFuture(
                plans.getOrDefault(offeringId, List.of()));
    }
}

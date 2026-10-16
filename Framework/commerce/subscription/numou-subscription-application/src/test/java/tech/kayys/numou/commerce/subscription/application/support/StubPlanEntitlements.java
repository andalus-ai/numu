package tech.kayys.syirkah.commerce.subscription.application.support;

import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.subscription.domain.Entitlement;
import tech.kayys.syirkah.commerce.subscription.spi.port.PlanEntitlementPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Test double for the plan catalog.
 */
public final class StubPlanEntitlements implements PlanEntitlementPort {

    private final Map<ProductOfferingId, List<Entitlement>> plans = new LinkedHashMap<>();

    public void grant(ProductOfferingId offeringId, Entitlement... entitlements) {
        plans.put(offeringId, List.of(entitlements));
    }

    @Override
    public CompletionStage<List<Entitlement>> findEntitlements(ProductOfferingId offeringId) {
        return CompletableFuture.completedFuture(
                plans.getOrDefault(offeringId, List.of()));
    }
}


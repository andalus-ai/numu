package tech.kayys.syirkah.commerce.subscription.spi.port;

import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.subscription.domain.Entitlement;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Plan catalog lookup: which entitlements an offering (plan)
 * grants. Entitlements live with the plan, never with the
 * subscriber.
 */
public interface PlanEntitlementPort {

    CompletionStage<List<Entitlement>> findEntitlements(ProductOfferingId offeringId);
}

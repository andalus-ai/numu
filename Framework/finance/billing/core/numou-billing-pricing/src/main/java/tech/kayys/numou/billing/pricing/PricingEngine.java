package tech.kayys.numou.billing.pricing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.numou.billing.domain.plugin.PriceCalculation;
import tech.kayys.numou.billing.domain.valueobject.BillingItem;
import tech.kayys.numou.billing.pricing.strategy.FlatPricingStrategy;
import tech.kayys.numou.billing.pricing.strategy.PricingStrategy;

import java.util.Map;

@ApplicationScoped
public class PricingEngine {

    private static final Logger LOG = Logger.getLogger(PricingEngine.class);

    @Inject
    Instance<PricingStrategy> strategies;

    @Inject
    FlatPricingStrategy defaultStrategy;

    public PriceCalculation calculatePrice(BillingItem item, Map<String, Object> parameters) {
        if (strategies != null) {
            for (PricingStrategy strategy : strategies) {
                if (strategy.supports(item, parameters)) {
                    LOG.debugf("Using pricing strategy %s for item %s", strategy.getType(), item.getName());
                    return strategy.calculate(item, parameters);
                }
            }
        }
        return defaultStrategy.calculate(item, parameters);
    }
}

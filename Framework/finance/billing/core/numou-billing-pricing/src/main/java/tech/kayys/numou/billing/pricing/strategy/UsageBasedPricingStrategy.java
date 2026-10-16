package tech.kayys.numou.billing.pricing.strategy;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.billing.domain.plugin.PriceCalculation;
import tech.kayys.numou.billing.domain.valueobject.BillingItem;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.Map;

@ApplicationScoped
public class UsageBasedPricingStrategy implements PricingStrategy {

    @Override
    public PricingStrategyType getType() {
        return PricingStrategyType.USAGE_BASED;
    }

    @Override
    public boolean supports(BillingItem item, Map<String, Object> parameters) {
        return (parameters != null && "USAGE_BASED".equalsIgnoreCase(String.valueOf(parameters.get("strategy"))))
               || item.getItemType() == tech.kayys.numou.billing.domain.valueobject.ItemType.USAGE;
    }

    @Override
    public PriceCalculation calculate(BillingItem item, Map<String, Object> parameters) {
        BigDecimal units = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        String cur = item.getUnitPrice() != null ? item.getUnitPrice().getCurrency().getCurrencyCode() : "USD";
        
        BigDecimal includedAllowance = BigDecimal.ZERO;
        Money overageRate = item.getUnitPrice() != null ? item.getUnitPrice() : Money.zero(cur);

        if (parameters != null) {
            if (parameters.containsKey("includedUnits")) {
                includedAllowance = new BigDecimal(String.valueOf(parameters.get("includedUnits")));
            }
            if (parameters.containsKey("overageRate") && parameters.get("overageRate") instanceof Money mr) {
                overageRate = mr;
            }
        }

        BigDecimal billableUnits = units.subtract(includedAllowance).max(BigDecimal.ZERO);
        Money subtotal = overageRate.multiply(billableUnits);

        Money tax = item.getTaxAmount() != null ? item.getTaxAmount() : Money.zero(cur);
        Money disc = item.getDiscountAmount() != null ? item.getDiscountAmount() : Money.zero(cur);
        Money total = subtotal.add(tax).subtract(disc);

        return new PriceCalculation(
            subtotal, tax, disc, total,
            Map.of(
                "strategy", "USAGE_BASED",
                "totalUnits", units,
                "includedUnits", includedAllowance,
                "billableUnits", billableUnits
            )
        );
    }
}

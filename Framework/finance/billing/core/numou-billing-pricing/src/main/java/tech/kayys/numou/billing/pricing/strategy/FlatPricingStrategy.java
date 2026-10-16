package tech.kayys.numou.billing.pricing.strategy;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.billing.domain.plugin.PriceCalculation;
import tech.kayys.numou.billing.domain.valueobject.BillingItem;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.Map;

@ApplicationScoped
public class FlatPricingStrategy implements PricingStrategy {

    @Override
    public PricingStrategyType getType() {
        return PricingStrategyType.FLAT;
    }

    @Override
    public boolean supports(BillingItem item, Map<String, Object> parameters) {
        if (parameters != null && parameters.containsKey("strategy")) {
            return "FLAT".equalsIgnoreCase(String.valueOf(parameters.get("strategy")));
        }
        return true; // Default strategy
    }

    @Override
    public PriceCalculation calculate(BillingItem item, Map<String, Object> parameters) {
        BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE;
        Money unitPrice = item.getUnitPrice();
        String cur = unitPrice != null ? unitPrice.getCurrency().getCurrencyCode() : "USD";
        
        Money subtotal = unitPrice != null ? unitPrice.multiply(qty) : Money.zero(cur);
        Money tax = item.getTaxAmount() != null ? item.getTaxAmount() : Money.zero(cur);
        Money disc = item.getDiscountAmount() != null ? item.getDiscountAmount() : Money.zero(cur);
        Money total = subtotal.add(tax).subtract(disc);

        return new PriceCalculation(subtotal, tax, disc, total, Map.of("strategy", "FLAT", "quantity", qty));
    }
}

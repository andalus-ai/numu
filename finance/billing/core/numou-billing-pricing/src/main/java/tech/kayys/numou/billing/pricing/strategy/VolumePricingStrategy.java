package tech.kayys.numou.billing.pricing.strategy;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.billing.domain.plugin.PriceCalculation;
import tech.kayys.numou.billing.domain.valueobject.BillingItem;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class VolumePricingStrategy implements PricingStrategy {

    public record VolumeTier(BigDecimal minUnits, Money rate) {}

    @Override
    public PricingStrategyType getType() {
        return PricingStrategyType.VOLUME;
    }

    @Override
    public boolean supports(BillingItem item, Map<String, Object> parameters) {
        return parameters != null && "VOLUME".equalsIgnoreCase(String.valueOf(parameters.get("strategy")));
    }

    @Override
    @SuppressWarnings("unchecked")
    public PriceCalculation calculate(BillingItem item, Map<String, Object> parameters) {
        BigDecimal totalQty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE;
        String cur = item.getUnitPrice() != null ? item.getUnitPrice().getCurrency().getCurrencyCode() : "USD";

        List<VolumeTier> volumeTiers = (List<VolumeTier>) parameters.get("volumeTiers");
        Money matchedRate = item.getUnitPrice() != null ? item.getUnitPrice() : Money.zero(cur);

        if (volumeTiers != null) {
            for (VolumeTier vt : volumeTiers) {
                if (totalQty.compareTo(vt.minUnits()) >= 0) {
                    matchedRate = vt.rate();
                }
            }
        }

        Money subtotal = matchedRate.multiply(totalQty);
        Money tax = item.getTaxAmount() != null ? item.getTaxAmount() : Money.zero(cur);
        Money disc = item.getDiscountAmount() != null ? item.getDiscountAmount() : Money.zero(cur);
        Money total = subtotal.add(tax).subtract(disc);

        return new PriceCalculation(subtotal, tax, disc, total, Map.of("strategy", "VOLUME", "rateApplied", matchedRate.getAmount()));
    }
}

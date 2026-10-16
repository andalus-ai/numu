package tech.kayys.numou.billing.pricing.strategy;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.billing.domain.plugin.PriceCalculation;
import tech.kayys.numou.billing.domain.valueobject.BillingItem;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class TieredPricingStrategy implements PricingStrategy {

    public record Tier(BigDecimal upToUnits, Money unitPrice) {}

    @Override
    public PricingStrategyType getType() {
        return PricingStrategyType.TIERED_GRADUATED;
    }

    @Override
    public boolean supports(BillingItem item, Map<String, Object> parameters) {
        return parameters != null && "TIERED_GRADUATED".equalsIgnoreCase(String.valueOf(parameters.get("strategy")));
    }

    @Override
    @SuppressWarnings("unchecked")
    public PriceCalculation calculate(BillingItem item, Map<String, Object> parameters) {
        BigDecimal totalQty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE;
        String cur = item.getUnitPrice() != null ? item.getUnitPrice().getCurrency().getCurrencyCode() : "USD";

        List<Tier> tiers = (List<Tier>) parameters.get("tiers");
        if (tiers == null || tiers.isEmpty()) {
            Money subtotal = item.getUnitPrice() != null ? item.getUnitPrice().multiply(totalQty) : Money.zero(cur);
            return new PriceCalculation(subtotal, Money.zero(cur), Money.zero(cur), subtotal, Map.of("strategy", "TIERED_FALLBACK"));
        }

        Money subtotal = Money.zero(cur);
        BigDecimal remaining = totalQty;
        BigDecimal previousThreshold = BigDecimal.ZERO;

        for (Tier tier : tiers) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal tierCapacity = tier.upToUnits() != null ? tier.upToUnits().subtract(previousThreshold) : remaining;
            BigDecimal unitsInTier = remaining.min(tierCapacity);

            subtotal = subtotal.add(tier.unitPrice().multiply(unitsInTier));
            remaining = remaining.subtract(unitsInTier);
            if (tier.upToUnits() != null) {
                previousThreshold = tier.upToUnits();
            }
        }

        Money tax = item.getTaxAmount() != null ? item.getTaxAmount() : Money.zero(cur);
        Money disc = item.getDiscountAmount() != null ? item.getDiscountAmount() : Money.zero(cur);
        Money total = subtotal.add(tax).subtract(disc);

        return new PriceCalculation(subtotal, tax, disc, total, Map.of("strategy", "TIERED_GRADUATED", "unitsProcessed", totalQty));
    }
}

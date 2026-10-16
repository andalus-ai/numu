package tech.kayys.syirkah.accounting.consolidation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Eliminates unrealized gross margin embedded in intercompany inventory transfers.
 */
public final class UnrealizedProfitEliminationRule implements EliminationRule {

    private final BigDecimal grossMarginRate;

    public UnrealizedProfitEliminationRule(BigDecimal grossMarginRate) {
        this.grossMarginRate = grossMarginRate != null ? grossMarginRate : new BigDecimal("0.20");
    }

    @Override
    public String ruleCode() {
        return "ELIM_UNREALIZED_INVENTORY_PROFIT";
    }

    @Override
    public Optional<ConsolidationRun.Elimination> evaluate(ConsolidationContext context) {
        if (context.sourceAmount() == null || context.sourceAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        // Unrealized profit = Ending Inventory from Intercompany Transfer * Margin Rate
        BigDecimal unrealizedProfit = context.sourceAmount().multiply(grossMarginRate).setScale(4, RoundingMode.HALF_UP);
        return Optional.of(new ConsolidationRun.Elimination(
                context.sourceCompany(),
                context.targetCompany(),
                ConsolidationRun.EliminationKind.INTERCOMPANY_PROFIT,
                unrealizedProfit,
                context.currency(),
                "Unrealized inventory profit elimination at margin " + grossMarginRate
        ));
    }
}

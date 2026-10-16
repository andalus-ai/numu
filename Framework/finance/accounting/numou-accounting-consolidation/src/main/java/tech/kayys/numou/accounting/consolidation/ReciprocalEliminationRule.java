package tech.kayys.syirkah.accounting.consolidation;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Eliminates reciprocal intercompany Accounts Receivable and Accounts Payable.
 */
public final class ReciprocalEliminationRule implements EliminationRule {

    @Override
    public String ruleCode() {
        return "ELIM_RECIPROCAL_AP_AR";
    }

    @Override
    public Optional<ConsolidationRun.Elimination> evaluate(ConsolidationContext context) {
        if (context.sourceAmount() == null || context.targetAmount() == null) {
            return Optional.empty();
        }
        // Offset the lesser of the two reciprocal balances
        BigDecimal elimAmount = context.sourceAmount().min(context.targetAmount());
        if (elimAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        return Optional.of(new ConsolidationRun.Elimination(
                context.sourceCompany(),
                context.targetCompany(),
                ConsolidationRun.EliminationKind.RECIPROCAL_BALANCE,
                elimAmount,
                context.currency(),
                "Reciprocal AR/AP Elimination between " + context.sourceCompany() + " and " + context.targetCompany()
        ));
    }
}


package tech.kayys.numou.accounting.application.consolidation;

import tech.kayys.numou.accounting.domain.consolidation.EliminationEntry;
import tech.kayys.numou.accounting.domain.identifier.AccountId;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Engine executing intercompany reciprocal balance eliminations and NCI calculations.
 */
public class EliminationEngine {

    public record EliminationMatchResult(
            boolean balanced,
            EliminationEntry eliminationEntry,
            BigDecimal varianceAmount
    ) {}

    public EliminationMatchResult eliminateReciprocalBalance(
            String eliminationId,
            AccountId intercompanyPayableAccountId,
            AccountId intercompanyReceivableAccountId,
            Money receivableBalance,
            Money payableBalance) {

        Objects.requireNonNull(receivableBalance);
        Objects.requireNonNull(payableBalance);

        BigDecimal diff = receivableBalance.amount().subtract(payableBalance.amount()).abs();
        boolean isBalanced = diff.compareTo(BigDecimal.ZERO) == 0;

        // Eliminate at lower of the two or exact amount
        BigDecimal eliminatedAmount = receivableBalance.amount().min(payableBalance.amount());
        Money amount = Money.of(eliminatedAmount, receivableBalance.currency());

        EliminationEntry entry = new EliminationEntry(
                eliminationId,
                "RULE-RECIPROCAL-ELIM",
                intercompanyPayableAccountId,
                intercompanyReceivableAccountId,
                amount,
                "Elimination of reciprocal intercompany AR/AP balance"
        );

        return new EliminationMatchResult(isBalanced, entry, diff);
    }
}

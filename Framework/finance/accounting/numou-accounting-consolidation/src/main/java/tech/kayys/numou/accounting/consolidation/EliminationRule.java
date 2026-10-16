package tech.kayys.syirkah.accounting.consolidation;

import java.util.Optional;

/**
 * SPI for evaluating and producing intercompany elimination entries.
 */
public interface EliminationRule {
    String ruleCode();
    Optional<ConsolidationRun.Elimination> evaluate(ConsolidationContext context);

    record ConsolidationContext(
            String sourceCompany,
            String targetCompany,
            String sourceAccount,
            String targetAccount,
            java.math.BigDecimal sourceAmount,
            java.math.BigDecimal targetAmount,
            String currency
    ) {}
}

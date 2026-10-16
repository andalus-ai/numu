
package tech.kayys.syirkah.accounting.application.procurement;

import java.math.BigDecimal;
import java.util.List;

public record MatchResult(
        String poId,
        String receiptId,
        String invoiceId,
        MatchOutcome outcome,
        BigDecimal quantityVariance,
        BigDecimal priceVariance,
        List<String> messages
) {
    public boolean isPayable() {
        return outcome == MatchOutcome.MATCHED || outcome == MatchOutcome.TOLERANCE;
    }
}


package tech.kayys.syirkah.accounting.application.procurement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 3-Way matching engine comparing Purchase Order, Goods Receipt, and Vendor Invoice.
 */
public class MatchingEngine {

    private final BigDecimal quantityTolerancePct;
    private final BigDecimal priceTolerancePct;

    public MatchingEngine(BigDecimal quantityTolerancePct, BigDecimal priceTolerancePct) {
        this.quantityTolerancePct = quantityTolerancePct;
        this.priceTolerancePct = priceTolerancePct;
    }

    public static MatchingEngine defaultEngine() {
        return new MatchingEngine(new BigDecimal("1.0"), new BigDecimal("2.0")); // 1% qty, 2% price
    }

    public MatchResult match3Way(
            String poId, BigDecimal poQty, BigDecimal poUnitPrice,
            String receiptId, BigDecimal receivedQty,
            String invoiceId, BigDecimal billedQty, BigDecimal billedUnitPrice) {

        List<String> messages = new ArrayList<>();

        // 1. Check quantity: billed vs received
        BigDecimal qtyDiff = billedQty.subtract(receivedQty).abs();
        BigDecimal qtyVariancePct = receivedQty.compareTo(BigDecimal.ZERO) == 0 ? 
                BigDecimal.ZERO : qtyDiff.divide(receivedQty, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        // 2. Check unit price: billed vs PO
        BigDecimal priceDiff = billedUnitPrice.subtract(poUnitPrice).abs();
        BigDecimal priceVariancePct = poUnitPrice.compareTo(BigDecimal.ZERO) == 0 ?
                BigDecimal.ZERO : priceDiff.divide(poUnitPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        MatchOutcome outcome;
        if (qtyVariancePct.compareTo(BigDecimal.ZERO) == 0 && priceVariancePct.compareTo(BigDecimal.ZERO) == 0) {
            outcome = MatchOutcome.MATCHED;
            messages.add("Perfect 3-way match.");
        } else if (qtyVariancePct.compareTo(quantityTolerancePct) <= 0 && priceVariancePct.compareTo(priceTolerancePct) <= 0) {
            outcome = MatchOutcome.TOLERANCE;
            messages.add("Matched within acceptable tolerance thresholds.");
        } else {
            outcome = MatchOutcome.MISMATCH;
            messages.add(String.format("Mismatch: Qty variance %s%% (limit %s%%), Price variance %s%% (limit %s%%)",
                    qtyVariancePct, quantityTolerancePct, priceVariancePct, priceTolerancePct));
        }

        return new MatchResult(poId, receiptId, invoiceId, outcome, qtyVariancePct, priceVariancePct, messages);
    }
}

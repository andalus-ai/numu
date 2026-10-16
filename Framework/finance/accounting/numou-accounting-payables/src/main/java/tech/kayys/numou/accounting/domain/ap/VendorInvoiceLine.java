package tech.kayys.syirkah.accounting.domain.ap;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A single line on a {@link VendorInvoice}.
 * Amounts are positive; total = netAmount + taxAmount.
 */
public record VendorInvoiceLine(
        String accountCode,
        String description,
        BigDecimal netAmount,
        BigDecimal taxAmount
) {
    public VendorInvoiceLine {
        Objects.requireNonNull(accountCode, "accountCode");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(netAmount, "netAmount");
        Objects.requireNonNull(taxAmount, "taxAmount");
        if (accountCode.isBlank()) throw new IllegalArgumentException("accountCode must not be blank");
        if (netAmount.signum() < 0) throw new IllegalArgumentException("netAmount must be >= 0");
        if (taxAmount.signum() < 0) throw new IllegalArgumentException("taxAmount must be >= 0");
    }

    public BigDecimal total() { return netAmount.add(taxAmount); }

    public BigDecimal taxRatio() {
        BigDecimal t = total();
        if (t.signum() == 0) return BigDecimal.ZERO;
        return taxAmount.divide(t, 6, RoundingMode.HALF_UP);
    }
}

package tech.kayys.syirkah.accounting.domain.ar;

import java.math.BigDecimal;
import java.util.Objects;

/** A single revenue line on a {@link CustomerInvoice}. */
public record CustomerInvoiceLine(
        String revenueAccount,
        String description,
        BigDecimal netAmount,
        BigDecimal taxAmount
) {
    public CustomerInvoiceLine {
        Objects.requireNonNull(revenueAccount, "revenueAccount");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(netAmount, "netAmount");
        Objects.requireNonNull(taxAmount, "taxAmount");
        if (revenueAccount.isBlank()) throw new IllegalArgumentException("revenueAccount must not be blank");
        if (netAmount.signum() < 0) throw new IllegalArgumentException("netAmount must be >= 0");
        if (taxAmount.signum() < 0) throw new IllegalArgumentException("taxAmount must be >= 0");
    }

    public BigDecimal total() { return netAmount.add(taxAmount); }
}

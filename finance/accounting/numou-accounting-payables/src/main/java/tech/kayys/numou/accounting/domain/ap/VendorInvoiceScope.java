package tech.kayys.numou.accounting.domain.ap;

import java.util.Objects;

/** Immutable accounting ownership scope for a supplier invoice. */
public record VendorInvoiceScope(String tenantId, String companyId, String ledgerId, String periodId) {
    public VendorInvoiceScope {
        tenantId = required(tenantId, "tenantId");
        companyId = required(companyId, "companyId");
        ledgerId = required(ledgerId, "ledgerId");
        periodId = required(periodId, "periodId");
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}

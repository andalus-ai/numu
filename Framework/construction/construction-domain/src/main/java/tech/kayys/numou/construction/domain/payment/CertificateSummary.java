package tech.kayys.syirkah.construction.domain.payment;

import java.math.BigDecimal;
import java.util.Objects;

public record CertificateSummary(
        BigDecimal grossCertifiedWork,
        BigDecimal retentionAmount,
        BigDecimal advanceRecovery,
        BigDecimal deductions
) {
    public CertificateSummary {
        Objects.requireNonNull(grossCertifiedWork);
        Objects.requireNonNull(retentionAmount);
        Objects.requireNonNull(advanceRecovery);
        Objects.requireNonNull(deductions);
    }

    public BigDecimal netPayable() {
        return grossCertifiedWork.subtract(retentionAmount).subtract(advanceRecovery).subtract(deductions);
    }
}

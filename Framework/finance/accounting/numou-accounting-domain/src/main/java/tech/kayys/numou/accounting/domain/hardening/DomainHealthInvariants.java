package tech.kayys.syirkah.accounting.domain.hardening;

import java.math.BigDecimal;
import java.util.Objects;

public final class DomainHealthInvariants {

    public record InvariantCheckResult(boolean healthy, String component, String details) {}

    public static InvariantCheckResult verifyBalance(BigDecimal totalDebits, BigDecimal totalCredits) {
        Objects.requireNonNull(totalDebits, "totalDebits must not be null");
        Objects.requireNonNull(totalCredits, "totalCredits must not be null");
        boolean balanced = totalDebits.compareTo(totalCredits) == 0;
        return new InvariantCheckResult(balanced, "GeneralLedgerBalance",
                balanced ? "Debits equal Credits: " + totalDebits : "Out of balance: Debits=" + totalDebits + ", Credits=" + totalCredits);
    }

    public static InvariantCheckResult verifyAssetDepreciation(BigDecimal acquisitionCost, BigDecimal accumulatedDepr, BigDecimal bookValue) {
        BigDecimal computed = acquisitionCost.subtract(accumulatedDepr);
        boolean valid = computed.compareTo(bookValue) == 0 && bookValue.compareTo(BigDecimal.ZERO) >= 0;
        return new InvariantCheckResult(valid, "AssetDepreciationHealth",
                valid ? "Asset book value invariant holds: " + bookValue : "Invalid asset book value calculation");
    }
}

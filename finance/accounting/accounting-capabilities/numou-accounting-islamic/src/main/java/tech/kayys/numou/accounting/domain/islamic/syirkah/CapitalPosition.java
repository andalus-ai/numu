package tech.kayys.numou.accounting.domain.islamic.syirkah;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/** Verified capital-at-risk position for the exact financial period close date. */
public record CapitalPosition(
        String partyId,
        BigDecimal amountAtRisk,
        String currencyCode,
        int currencyScale,
        LocalDate asOfDate,
        String sourceEvidenceId
) {
    public CapitalPosition {
        partyId = SyirkahPartyTerms.requireText(partyId, "partyId");
        Objects.requireNonNull(amountAtRisk, "amountAtRisk must not be null");
        if (amountAtRisk.signum() < 0) throw new IllegalArgumentException("amountAtRisk must not be negative");
        currencyCode = SyirkahContractVersion.normalizeCurrency(currencyCode);
        SyirkahContractVersion.validateCurrencyScale(currencyScale);
        try {
            amountAtRisk = amountAtRisk.setScale(currencyScale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("amountAtRisk exceeds currency precision", ex);
        }
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");
        sourceEvidenceId = SyirkahPartyTerms.requireText(sourceEvidenceId, "sourceEvidenceId");
    }
}

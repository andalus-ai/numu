package tech.kayys.numou.accounting.domain.islamic.syirkah;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/** Immutable evidence-backed settlement slice against one approved obligation. */
public record DistributionSettlement(
        String settlementId,
        BigDecimal amount,
        String currencyCode,
        int currencyScale,
        String externalReference,
        String evidenceId,
        String recordedBy,
        Instant recordedAt
) {
    public DistributionSettlement {
        settlementId = SyirkahPartyTerms.requireText(settlementId, "settlementId");
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) throw new IllegalArgumentException("settlement amount must be greater than zero");
        currencyCode = SyirkahContractVersion.normalizeCurrency(currencyCode);
        SyirkahContractVersion.validateCurrencyScale(currencyScale);
        try {
            amount = amount.setScale(currencyScale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("settlement amount exceeds currency precision", ex);
        }
        externalReference = SyirkahPartyTerms.requireText(externalReference, "externalReference");
        evidenceId = SyirkahPartyTerms.requireText(evidenceId, "evidenceId");
        recordedBy = SyirkahPartyTerms.requireText(recordedBy, "recordedBy");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
    }
}

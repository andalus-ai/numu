package tech.kayys.numou.accounting.domain.islamic.syirkah;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable calculation artifact; persistence and approval belong to application workflows. */
public record SyirkahCalculationSnapshot(
        String calculationId,
        String contractId,
        int contractVersion,
        LocalDate periodStart,
        LocalDate periodEnd,
        String currencyCode,
        int currencyScale,
        BigDecimal netResult,
        ResultType resultType,
        LossCause lossCause,
        String lossClassificationReference,
        String lossClassifiedBy,
        java.time.Instant lossClassifiedAt,
        String lossPolicyId,
        String lossPolicyVersion,
        List<PartnerAllocation> allocations,
        List<String> evidenceIds,
        String contentHash
) {
    public enum ResultType { PROFIT, LOSS, BREAK_EVEN, RESPONSIBILITY_REVIEW_REQUIRED, POLICY_REVIEW_REQUIRED }

    public SyirkahCalculationSnapshot {
        calculationId = SyirkahPartyTerms.requireText(calculationId, "calculationId");
        contractId = SyirkahPartyTerms.requireText(contractId, "contractId");
        if (contractVersion < 1) throw new IllegalArgumentException("contractVersion must be positive");
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        if (periodEnd.isBefore(periodStart)) throw new IllegalArgumentException("periodEnd must not be before periodStart");
        currencyCode = SyirkahContractVersion.normalizeCurrency(currencyCode);
        SyirkahContractVersion.validateCurrencyScale(currencyScale);
        Objects.requireNonNull(netResult, "netResult must not be null");
        try {
            netResult = netResult.setScale(currencyScale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("netResult exceeds currency precision", ex);
        }
        Objects.requireNonNull(resultType, "resultType must not be null");
        Objects.requireNonNull(lossCause, "lossCause must not be null");
        if (lossCause == LossCause.NOT_APPLICABLE) {
            if (lossClassificationReference != null || lossClassifiedBy != null || lossClassifiedAt != null) {
                throw new IllegalArgumentException("Non-loss snapshot must not contain loss-classification metadata");
            }
        } else {
            lossClassificationReference = SyirkahPartyTerms.requireText(lossClassificationReference, "lossClassificationReference");
            lossClassifiedBy = SyirkahPartyTerms.requireText(lossClassifiedBy, "lossClassifiedBy");
            Objects.requireNonNull(lossClassifiedAt, "lossClassifiedAt must not be null");
        }
        lossPolicyId = SyirkahPartyTerms.requireText(lossPolicyId, "lossPolicyId");
        lossPolicyVersion = SyirkahPartyTerms.requireText(lossPolicyVersion, "lossPolicyVersion");
        allocations = Objects.requireNonNull(allocations, "allocations must not be null").stream()
                .map(a -> {
                    try {
                        return new PartnerAllocation(a.partyId(), a.amount().setScale(currencyScale, RoundingMode.UNNECESSARY));
                    } catch (ArithmeticException ex) {
                        throw new IllegalArgumentException("Allocation exceeds currency precision for party " + a.partyId(), ex);
                    }
                })
                .sorted(Comparator.comparing(PartnerAllocation::partyId))
                .toList();
        evidenceIds = Objects.requireNonNull(evidenceIds, "evidenceIds must not be null").stream()
                .map(id -> SyirkahPartyTerms.requireText(id, "evidenceId"))
                .distinct().sorted().toList();
        contentHash = SyirkahPartyTerms.requireText(contentHash, "contentHash");
        if (!contentHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("contentHash must be a lowercase SHA-256 hex digest");
        Set<String> ids = new HashSet<>();
        for (PartnerAllocation allocation : allocations) {
            if (!ids.add(allocation.partyId())) throw new IllegalArgumentException("Duplicate allocation for party: " + allocation.partyId());
        }
        BigDecimal allocated = allocations.stream().map(PartnerAllocation::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        switch (resultType) {
            case PROFIT -> {
                if (lossCause != LossCause.NOT_APPLICABLE) throw new IllegalArgumentException("Profit snapshot cannot have a loss cause");
                if (netResult.signum() <= 0 || allocated.compareTo(netResult) != 0) throw new IllegalArgumentException("Profit allocations must equal positive net result");
            }
            case LOSS -> {
                if (lossCause != LossCause.ORDINARY_BUSINESS && lossCause != LossCause.FORCE_MAJEURE) throw new IllegalArgumentException("Automatically allocated loss requires an ordinary or approved force-majeure classification");
                if (netResult.signum() >= 0 || allocated.compareTo(netResult.abs()) != 0) throw new IllegalArgumentException("Loss allocations must equal absolute negative net result");
            }
            case BREAK_EVEN -> {
                if (lossCause != LossCause.NOT_APPLICABLE) throw new IllegalArgumentException("Break-even snapshot cannot have a loss cause");
                if (netResult.signum() != 0 || !allocations.isEmpty()) throw new IllegalArgumentException("Break-even snapshot cannot contain allocations");
            }
            case RESPONSIBILITY_REVIEW_REQUIRED -> {
                if (lossCause == LossCause.NOT_APPLICABLE || lossCause == LossCause.ORDINARY_BUSINESS || lossCause == LossCause.FORCE_MAJEURE) throw new IllegalArgumentException("Responsibility review requires a non-ordinary loss classification");
                if (netResult.signum() >= 0 || !allocations.isEmpty()) throw new IllegalArgumentException("Review-required snapshot must be a loss with no automatic allocation");
            }
            case POLICY_REVIEW_REQUIRED -> {
                if (lossCause != LossCause.ORDINARY_BUSINESS && lossCause != LossCause.FORCE_MAJEURE) throw new IllegalArgumentException("Policy review requires an ordinary or approved force-majeure classification");
                if (netResult.signum() >= 0 || !allocations.isEmpty()) throw new IllegalArgumentException("Review-required snapshot must be a loss with no automatic allocation");
            }
        }
    }
}

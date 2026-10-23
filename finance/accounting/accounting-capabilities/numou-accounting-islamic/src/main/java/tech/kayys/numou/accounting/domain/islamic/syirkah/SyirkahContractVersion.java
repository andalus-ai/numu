package tech.kayys.numou.accounting.domain.islamic.syirkah;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, approved contract version. Historical versions are never edited;
 * changes to partners, nisbah, policy, or effective dates require a new version.
 */
public record SyirkahContractVersion(
        String contractId,
        int versionNumber,
        SyirkahType type,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil,
        String currencyCode,
        int currencyScale,
        LossAllocationMode lossAllocationMode,
        String lossPolicyId,
        String lossPolicyVersion,
        String policyApprovalReference,
        String agreementEvidenceId,
        String approvedBy,
        Instant approvedAt,
        List<SyirkahPartyTerms> parties
) {
    public SyirkahContractVersion {
        contractId = SyirkahPartyTerms.requireText(contractId, "contractId");
        if (versionNumber < 1) throw new IllegalArgumentException("versionNumber must be positive");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        if (effectiveUntil != null && effectiveUntil.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveUntil must not be before effectiveFrom");
        }
        currencyCode = normalizeCurrency(currencyCode);
        validateCurrencyScale(currencyScale);
        Objects.requireNonNull(lossAllocationMode, "lossAllocationMode must not be null");
        lossPolicyId = SyirkahPartyTerms.requireText(lossPolicyId, "lossPolicyId");
        lossPolicyVersion = SyirkahPartyTerms.requireText(lossPolicyVersion, "lossPolicyVersion");
        policyApprovalReference = SyirkahPartyTerms.requireText(policyApprovalReference, "policyApprovalReference");
        agreementEvidenceId = SyirkahPartyTerms.requireText(agreementEvidenceId, "agreementEvidenceId");
        approvedBy = SyirkahPartyTerms.requireText(approvedBy, "approvedBy");
        Objects.requireNonNull(approvedAt, "approvedAt must not be null");
        parties = List.copyOf(Objects.requireNonNull(parties, "parties must not be null"));

        if (parties.size() < 2) throw new IllegalArgumentException("A Syirkah contract requires at least two parties");
        Set<String> partyIds = new HashSet<>();
        int totalNisbah = 0;
        for (SyirkahPartyTerms party : parties) {
            if (!partyIds.add(party.partyId())) throw new IllegalArgumentException("Duplicate partyId: " + party.partyId());
            totalNisbah = Math.addExact(totalNisbah, party.profitSharingRatio().basisPoints());
        }
        if (totalNisbah != ProfitSharingRatio.TOTAL_BASIS_POINTS) {
            throw new IllegalArgumentException("Contract nisbah must total exactly 100.00%; got basis points=" + totalNisbah);
        }

        if (lossAllocationMode == LossAllocationMode.CAPITAL_PROVIDER_ONLY && type != SyirkahType.MUDHARABAH) {
            throw new IllegalArgumentException("CAPITAL_PROVIDER_ONLY is only supported for an explicitly modeled Mudharabah contract; other structures require their own approved policy");
        }
        if ((type == SyirkahType.ABDAN || type == SyirkahType.WUJUH || type == SyirkahType.MUFAWADHAH)
                && lossAllocationMode != LossAllocationMode.MANUAL_REVIEW_REQUIRED) {
            throw new IllegalArgumentException(type + " requires a separately approved, structure-specific loss policy; automatic capital-ratio allocation is disabled");
        }

        if (type == SyirkahType.MUDHARABAH) {
            boolean hasCapitalProvider = parties.stream().anyMatch(p -> p.role().contributesCapitalByRole());
            boolean hasManager = parties.stream().anyMatch(p -> p.role().managesBusinessByRole());
            if (!hasCapitalProvider || !hasManager) {
                throw new IllegalArgumentException("Mudharabah requires at least one capital provider and one managing/working partner");
            }
            if (lossAllocationMode == LossAllocationMode.CAPITAL_PROPORTION) {
                throw new IllegalArgumentException("Mudharabah cannot use generic capital-proportion loss allocation; pin a reviewed policy or require manual review");
            }
        }
    }

    public boolean isEffectiveOn(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return !date.isBefore(effectiveFrom)
                && (effectiveUntil == null || !date.isAfter(effectiveUntil));
    }

    public SyirkahPartyTerms party(String partyId) {
        return parties.stream().filter(p -> p.partyId().equals(partyId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Party is not part of contract version: " + partyId));
    }

    public static void validateCurrencyScale(int scale) {
        if (scale < 0 || scale > 6) {
            throw new IllegalArgumentException("currencyScale must be between 0 and 6 and must match the ledger's approved precision policy");
        }
    }

    public static String normalizeCurrency(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("currencyCode must not be blank");
        String normalized = code.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            Currency.getInstance(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported ISO-4217 currency code: " + normalized, ex);
        }
        return normalized;
    }
}

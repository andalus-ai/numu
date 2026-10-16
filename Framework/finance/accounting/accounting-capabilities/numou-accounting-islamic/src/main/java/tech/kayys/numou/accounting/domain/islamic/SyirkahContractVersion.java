package tech.kayys.numou.accounting.domain.islamic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, effective-dated terms for one signed Syirkah contract version.
 * Historical calculations must reference this version and never today's terms.
 */
public record SyirkahContractVersion(
        String contractId,
        int version,
        String tenantId,
        String companyId,
        String currencyCode,
        SyirkahType type,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil,
        String shariaPolicyVersion,
        List<SyirkahParty> parties) {

    public SyirkahContractVersion {
        contractId = requireText(contractId, "contractId");
        tenantId = requireText(tenantId, "tenantId");
        companyId = requireText(companyId, "companyId");
        currencyCode = requireText(currencyCode, "currencyCode").toUpperCase(java.util.Locale.ROOT);
        shariaPolicyVersion = requireText(shariaPolicyVersion, "shariaPolicyVersion");
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
        if (!currencyCode.matches("[A-Z]{3}")) throw new IllegalArgumentException("currencyCode must be ISO-4217 style");
        java.util.Currency.getInstance(currencyCode);
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(effectiveUntil, "effectiveUntil");
        if (effectiveUntil.isBefore(effectiveFrom)) throw new IllegalArgumentException("effectiveUntil precedes effectiveFrom");
        parties = List.copyOf(Objects.requireNonNull(parties, "parties"));
        if (parties.size() < 2) throw new IllegalArgumentException("Syirkah requires at least two parties");
        Set<String> ids = new HashSet<>();
        BigDecimal totalNisbah = BigDecimal.ZERO;
        boolean hasCapitalProvider = false;
        boolean hasPositiveCapitalProvider = false;
        boolean hasPositiveCapital = false;
        for (SyirkahParty party : parties) {
            Objects.requireNonNull(party, "party");
            if (!ids.add(party.partyId())) throw new IllegalArgumentException("duplicate partyId: " + party.partyId());
            totalNisbah = totalNisbah.add(party.profitNisbahPercent());
            hasCapitalProvider |= party.providesCapital();
            hasPositiveCapitalProvider |= party.providesCapital() && party.capitalContribution().signum() > 0;
            hasPositiveCapital |= party.capitalContribution().signum() > 0;
        }
        if (totalNisbah.compareTo(new BigDecimal("100")) != 0) {
            throw new IllegalArgumentException("profit nisbah must total exactly 100 percent");
        }
        if (type == SyirkahType.MUDHARABAH && !hasCapitalProvider) {
            throw new IllegalArgumentException("Mudharabah requires at least one capital provider");
        }
        if (type == SyirkahType.MUDHARABAH && !hasPositiveCapitalProvider) {
            throw new IllegalArgumentException("Mudharabah requires positive capital from a capital provider");
        }
        if ((type == SyirkahType.MUSYARAKAH || type == SyirkahType.INAN) && !hasPositiveCapital) {
            throw new IllegalArgumentException("capital-based partnership requires positive contributed capital");
        }
    }

    public boolean effectiveOn(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return !date.isBefore(effectiveFrom) && !date.isAfter(effectiveUntil);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}

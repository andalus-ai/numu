package tech.kayys.numou.accounting.domain.islamic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Contract-version-specific party terms. Capital is the agreed contributed capital
 * for this contract version, expressed in the contract currency.
 */
public record SyirkahParty(
        String partyId,
        String displayName,
        SyirkahPartyRole role,
        BigDecimal capitalContribution,
        BigDecimal profitNisbahPercent) {

    public SyirkahParty {
        partyId = requireText(partyId, "partyId");
        displayName = requireText(displayName, "displayName");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(capitalContribution, "capitalContribution");
        Objects.requireNonNull(profitNisbahPercent, "profitNisbahPercent");
        if (capitalContribution.signum() < 0) {
            throw new IllegalArgumentException("capitalContribution must not be negative");
        }
        if (profitNisbahPercent.signum() < 0 || profitNisbahPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("profitNisbahPercent must be between 0 and 100");
        }
        try {
            profitNisbahPercent = profitNisbahPercent.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("profitNisbahPercent supports at most four decimal places", ex);
        }
    }

    public boolean providesCapital() {
        return role == SyirkahPartyRole.CAPITAL_PROVIDER
                || role == SyirkahPartyRole.CAPITAL_AND_MANAGING_PARTNER;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}

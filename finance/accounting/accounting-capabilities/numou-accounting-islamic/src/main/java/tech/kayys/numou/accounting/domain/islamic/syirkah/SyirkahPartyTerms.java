package tech.kayys.numou.accounting.domain.islamic.syirkah;

import java.util.Objects;

/** Immutable party terms attached to one signed contract version. */
public record SyirkahPartyTerms(
        String partyId,
        SyirkahPartyRole role,
        ProfitSharingRatio profitSharingRatio
) {
    public SyirkahPartyTerms {
        partyId = requireText(partyId, "partyId");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(profitSharingRatio, "profitSharingRatio must not be null");
    }

    static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

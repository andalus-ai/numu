package tech.kayys.syirkah.ecosystem.domain.party;

import java.util.Objects;

/**
 * Cross-domain lightweight reference to a party identity (config02.md §P4-11 #11).
 */
public record PartyRef(
        PartyId partyId,
        PartyKind kind
) {
    public PartyRef {
        Objects.requireNonNull(partyId, "partyId cannot be null");
        kind = kind != null ? kind : PartyKind.UNKNOWN;
    }

    public static PartyRef of(PartyId partyId) {
        return new PartyRef(partyId, PartyKind.UNKNOWN);
    }

    public static PartyRef of(PartyId partyId, PartyKind kind) {
        return new PartyRef(partyId, kind);
    }
}

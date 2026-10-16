package tech.kayys.syirkah.ecosystem.domain.contact;

import tech.kayys.syirkah.ecosystem.domain.party.PartyId;

import java.io.Serializable;
import java.util.Objects;

/**
 * Associates a Party with an address, usage purpose, and effective validity (config03.md §P4-13 #4).
 */
public record PartyAddress(
        PartyId partyId,
        AddressId addressId,
        ContactPurpose purpose,
        boolean primary,
        EffectivePeriod effectivePeriod
) implements Serializable {

    public PartyAddress {
        Objects.requireNonNull(partyId, "partyId cannot be null");
        Objects.requireNonNull(addressId, "addressId cannot be null");
        Objects.requireNonNull(purpose, "purpose cannot be null");
        Objects.requireNonNull(effectivePeriod, "effectivePeriod cannot be null");
    }

    public static PartyAddress of(PartyId partyId, AddressId addressId, ContactPurpose purpose, boolean primary, EffectivePeriod effectivePeriod) {
        return new PartyAddress(partyId, addressId, purpose, primary, effectivePeriod);
    }

    public PartyAddress withPrimary(boolean isPrimary) {
        return new PartyAddress(partyId, addressId, purpose, isPrimary, effectivePeriod);
    }

    public PartyAddress withEffectivePeriod(EffectivePeriod newPeriod) {
        return new PartyAddress(partyId, addressId, purpose, primary, newPeriod);
    }
}

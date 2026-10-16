package tech.kayys.syirkah.ecosystem.domain.contact;

import tech.kayys.syirkah.ecosystem.domain.party.PartyId;

import java.io.Serializable;
import java.util.Objects;

/**
 * Associates a Party with a contact point, usage purpose, and effective validity (config03.md §P4-13 #4).
 */
public record PartyContactPoint(
        PartyId partyId,
        ContactPointId contactPointId,
        ContactPurpose purpose,
        boolean primary,
        EffectivePeriod effectivePeriod
) implements Serializable {

    public PartyContactPoint {
        Objects.requireNonNull(partyId, "partyId cannot be null");
        Objects.requireNonNull(contactPointId, "contactPointId cannot be null");
        Objects.requireNonNull(purpose, "purpose cannot be null");
        Objects.requireNonNull(effectivePeriod, "effectivePeriod cannot be null");
    }

    public static PartyContactPoint of(PartyId partyId, ContactPointId contactPointId, ContactPurpose purpose, boolean primary, EffectivePeriod effectivePeriod) {
        return new PartyContactPoint(partyId, contactPointId, purpose, primary, effectivePeriod);
    }

    public PartyContactPoint withPrimary(boolean isPrimary) {
        return new PartyContactPoint(partyId, contactPointId, purpose, isPrimary, effectivePeriod);
    }

    public PartyContactPoint withEffectivePeriod(EffectivePeriod newPeriod) {
        return new PartyContactPoint(partyId, contactPointId, purpose, primary, newPeriod);
    }
}

package tech.kayys.syirkah.ecosystem.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.ecosystem.domain.contact.ContactPurpose;
import tech.kayys.syirkah.ecosystem.domain.contact.PartyAddress;
import tech.kayys.syirkah.ecosystem.domain.contact.PartyContactPoint;
import tech.kayys.syirkah.ecosystem.domain.party.PartyId;

import java.util.List;
import java.util.Optional;

/**
 * Port for managing and resolving Party address and contact point associations (config03.md §P4-13 #2).
 */
public interface PartyContactReferencePort {

    Uni<List<PartyAddress>> findAddressesByPartyId(PartyId partyId);

    Uni<Optional<PartyAddress>> findPrimaryAddress(PartyId partyId, ContactPurpose purpose);

    Uni<PartyAddress> savePartyAddress(PartyAddress partyAddress);

    Uni<List<PartyContactPoint>> findContactPointsByPartyId(PartyId partyId);

    Uni<Optional<PartyContactPoint>> findPrimaryContactPoint(PartyId partyId, ContactPurpose purpose);

    Uni<PartyContactPoint> savePartyContactPoint(PartyContactPoint partyContactPoint);
}

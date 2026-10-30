package tech.kayys.numou.ecosystem.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.model.Party;
import tech.kayys.numou.ecosystem.domain.party.PartyId;

import java.util.Optional;

/**
 * Reads canonical Party identity and lifecycle without transferring Party
 * ownership to consuming capabilities.
 */
public interface PartyLookupPort {

    Uni<Optional<Party>> findParty(PartyId partyId);
}

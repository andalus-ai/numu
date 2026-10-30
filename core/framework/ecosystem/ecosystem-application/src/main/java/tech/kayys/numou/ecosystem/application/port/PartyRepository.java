package tech.kayys.numou.ecosystem.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.model.Party;
import tech.kayys.numou.ecosystem.domain.party.PartyId;

import java.util.Optional;

/** Persistence port for canonical Party identity and kind lookup. */
public interface PartyRepository extends PartyKindLookupPort {

    Uni<Party> save(Party party);

    Uni<Optional<Party>> findById(PartyId partyId);
}

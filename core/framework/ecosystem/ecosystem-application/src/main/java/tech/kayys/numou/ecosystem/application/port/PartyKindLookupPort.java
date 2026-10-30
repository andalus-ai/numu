package tech.kayys.numou.ecosystem.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyKind;

import java.util.Optional;

/**
 * Reads the canonical Party registry without transferring Party ownership to
 * the Organization capability. Implementations may be local adapters or
 * remote service clients in a distributed deployment.
 */
public interface PartyKindLookupPort {

    Uni<Optional<PartyKind>> findKind(PartyId partyId);
}

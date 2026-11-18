package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.PartyId;

/** Public-contract adapter used to validate canonical Party existence and lifecycle. */
public interface PartyDirectoryPort {
    Uni<Boolean> isActive(PartyId partyId);
}

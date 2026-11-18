package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.application.port.PartyLookupPort;
import tech.kayys.numou.ecosystem.domain.party.PartyId;

import java.util.Objects;

/** Adapts Ecosystem's canonical Party lookup to Social Graph's active-Party port. */
public final class EcosystemSocialGraphPartyDirectoryAdapter implements PartyDirectoryPort {
    private final PartyLookupPort parties;

    public EcosystemSocialGraphPartyDirectoryAdapter(PartyLookupPort parties) {
        this.parties = Objects.requireNonNull(parties, "parties cannot be null");
    }

    @Override
    public Uni<Boolean> isActive(PartyId partyId) {
        Objects.requireNonNull(partyId, "partyId cannot be null");
        return parties.findParty(partyId)
                .onItem().transform(found -> found != null && found.isPresent() && found.get().isActive());
    }
}

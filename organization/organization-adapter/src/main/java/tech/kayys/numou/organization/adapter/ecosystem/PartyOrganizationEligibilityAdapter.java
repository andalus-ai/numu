package tech.kayys.numou.organization.adapter.ecosystem;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.application.port.PartyLookupPort;
import tech.kayys.numou.ecosystem.domain.model.Party;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.organization.application.port.PartyOrganizationEligibilityPort;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * In-process adapter from Organization's asynchronous port to Ecosystem's
 * public Party lookup contract. It deliberately depends on the application
 * port, not the Ecosystem persistence adapter or its tables.
 *
 * <p>For distributed deployments, replace this adapter with an API client
 * implementing the same Organization application port.</p>
 */
public final class PartyOrganizationEligibilityAdapter implements PartyOrganizationEligibilityPort {

    private final PartyLookupPort parties;

    public PartyOrganizationEligibilityAdapter(PartyLookupPort parties) {
        this.parties = Objects.requireNonNull(parties, "parties");
    }

    @Override
    public CompletionStage<Eligibility> findEligibility(PartyId partyId) {
        Objects.requireNonNull(partyId, "partyId");
        Uni<Optional<Party>> lookup = parties.findParty(partyId);
        return lookup.map(found -> found
                .map(party -> new Eligibility(
                        true,
                        party.isActive(),
                        party.kind() == tech.kayys.numou.ecosystem.domain.party.PartyKind.ORGANIZATION))
                .orElseGet(() -> new Eligibility(false, false, false)))
                .subscribeAsCompletionStage();
    }
}

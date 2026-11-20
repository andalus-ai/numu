package tech.kayys.numou.advertising.adapter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.advertising.application.AdvertisingPartyVerificationPort;
import tech.kayys.numou.advertising.domain.PartyReference;
import tech.kayys.numou.ecosystem.application.port.PartyLookupPort;
import tech.kayys.numou.ecosystem.domain.party.PartyId;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Resolves opaque advertising Party references through the canonical Ecosystem Party lookup contract. */
@ApplicationScoped
public class EcosystemPartyVerificationAdapter implements AdvertisingPartyVerificationPort {
  private final PartyLookupPort parties;

  @Inject
  public EcosystemPartyVerificationAdapter(PartyLookupPort parties) {
    this.parties=Objects.requireNonNull(parties);
  }

  @Override
  public CompletionStage<Boolean> isActiveParty(PartyReference reference) {
    final PartyId partyId;
    try {
      partyId=PartyId.of(UUID.fromString(reference.value()));
    } catch(RuntimeException invalidReference) {
      return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
    return parties.findParty(partyId)
      .map(found->found.map(party->party.isActive()).orElse(false))
      .subscribeAsCompletionStage();
  }
}

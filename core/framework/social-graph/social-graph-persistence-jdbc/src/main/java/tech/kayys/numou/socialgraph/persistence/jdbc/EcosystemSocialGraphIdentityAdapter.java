package tech.kayys.numou.socialgraph.persistence.jdbc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.ecosystem.application.port.PartyIdentityAssociationRepository;
import tech.kayys.numou.ecosystem.application.port.PartyLookupPort;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.application.PartyDirectoryPort;
import tech.kayys.numou.socialgraph.application.SocialGraphActor;
import tech.kayys.numou.socialgraph.application.SocialGraphActorResolver;

import java.util.Objects;
import java.util.Optional;

/**
 * Bridges Social Graph to Ecosystem's public Party and identity-association
 * contracts. Blocking waits are intentional for this JDBC composition adapter:
 * callers must invoke it from a worker thread, never an event-loop thread.
 */
@ApplicationScoped
public class EcosystemSocialGraphIdentityAdapter implements PartyDirectoryPort, SocialGraphActorResolver {
    private final PartyLookupPort parties;
    private final PartyIdentityAssociationRepository associations;

    @Inject
    public EcosystemSocialGraphIdentityAdapter(PartyLookupPort parties,
            PartyIdentityAssociationRepository associations) {
        this.parties = Objects.requireNonNull(parties, "parties cannot be null");
        this.associations = Objects.requireNonNull(associations, "associations cannot be null");
    }

    @Override
    public boolean isActive(PartyId partyId) {
        return parties.findParty(partyId).await().indefinitely()
                .filter(party -> party.isActive())
                .isPresent();
    }

    @Override
    public Optional<SocialGraphActor> resolve(TenantId tenantId, IdentitySubjectRef identitySubject) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(identitySubject, "identitySubject cannot be null");
        return associations.findActiveByTenantAndIdentity(tenantId, identitySubject).await().indefinitely()
                .flatMap(association -> parties.findParty(association.partyId()).await().indefinitely()
                        .filter(party -> party.isActive())
                        .map(party -> new SocialGraphActor(tenantId, party.getId())));
    }
}

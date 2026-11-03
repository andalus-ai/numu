package tech.kayys.numou.socialgraph.persistence.jdbc;

import io.smallrye.mutiny.Uni;
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

/** Bridges Social Graph to Ecosystem's public Party and identity-association contracts. */
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
    public Uni<Boolean> isActive(PartyId partyId) {
        return parties.findParty(partyId).map(optional -> optional.filter(party -> party.isActive()).isPresent());
    }

    @Override
    public Uni<Optional<SocialGraphActor>> resolve(TenantId tenantId, IdentitySubjectRef identitySubject) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(identitySubject, "identitySubject cannot be null");
        return associations.findActiveByTenantAndIdentity(tenantId, identitySubject)
                .flatMap(association -> {
                    if (association.isEmpty()) return Uni.createFrom().item(Optional.empty());
                    return parties.findParty(association.get().partyId())
                            .map(party -> party.filter(value -> value.isActive())
                                    .map(value -> new SocialGraphActor(tenantId, value.getId())));
                });
    }
}

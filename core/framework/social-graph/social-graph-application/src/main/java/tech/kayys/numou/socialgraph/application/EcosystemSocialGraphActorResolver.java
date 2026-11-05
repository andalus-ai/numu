package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.application.port.PartyIdentityAssociationRepository;
import tech.kayys.numou.ecosystem.application.port.PartyLookupPort;
import tech.kayys.numou.ecosystem.domain.model.Party;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolves a verified external identity to its canonical active Party.
 *
 * <p>The association lookup is tenant-scoped. An identity association is
 * linkage only; tenant membership and Social Graph permissions are still
 * enforced independently by {@link SocialGraphAuthorizationPort}.</p>
 */
public final class EcosystemSocialGraphActorResolver implements SocialGraphActorResolver {
    private final PartyIdentityAssociationRepository associations;
    private final PartyLookupPort parties;

    public EcosystemSocialGraphActorResolver(
            PartyIdentityAssociationRepository associations,
            PartyLookupPort parties) {
        this.associations = Objects.requireNonNull(associations, "associations cannot be null");
        this.parties = Objects.requireNonNull(parties, "parties cannot be null");
    }

    @Override
    public Uni<Optional<SocialGraphActor>> resolve(
            TenantId tenantId, IdentitySubjectRef identitySubject) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(identitySubject, "identitySubject cannot be null");

        return associations.findActiveByTenantAndIdentity(tenantId, identitySubject)
                .onItem().transformToUni(association -> {
                    if (association == null || association.isEmpty()) {
                        return Uni.createFrom().item(Optional.empty());
                    }
                    var linked = association.get();
                    // Verify the returned record even though the repository contract is tenant-scoped.
                    if (!linked.isActive()
                            || !tenantId.equals(linked.tenantId())
                            || !identitySubject.equals(linked.identity())) {
                        return Uni.createFrom().item(Optional.empty());
                    }
                    return parties.findParty(linked.partyId())
                            .onItem().transform(found -> {
                                if (found == null || found.isEmpty()) {
                                    return Optional.<SocialGraphActor>empty();
                                }
                                Party party = found.get();
                                if (!party.isActive() || !linked.partyId().equals(party.getId())) {
                                    return Optional.<SocialGraphActor>empty();
                                }
                                return Optional.of(new SocialGraphActor(tenantId, linked.partyId()));
                            });
                });
    }
}

package tech.kayys.numou.support.adapter.security;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.ecosystem.application.port.PartyParticipantAssociationRepository;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyParticipantAssociation;
import tech.kayys.numou.ecosystem.domain.party.PartyParticipantRole;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.support.application.api.SupportPartyContext;
import tech.kayys.numou.support.application.port.SupportPartyParticipantResolver;
import tech.kayys.numou.support.domain.ticket.RequesterRole;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the tenant-specific Support requester identity from Ecosystem's
 * authoritative Party–Participant relationship records.
 *
 * <p>Only CUSTOMER and PARTNER are meaningful to Support. Multiple active
 * supported relationships are treated as ambiguous and denied rather than
 * choosing an arbitrary Participant.
 */
@ApplicationScoped
public class DefaultSupportPartyParticipantResolver implements SupportPartyParticipantResolver {
    private final PartyParticipantAssociationRepository associations;

    @Inject
    public DefaultSupportPartyParticipantResolver(PartyParticipantAssociationRepository associations) {
        this.associations = Objects.requireNonNull(associations, "associations");
    }

    @Override
    public Uni<Optional<SupportPartyContext>> resolve(TenantId tenantId, PartyId partyId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        Instant now = Instant.now();
        return associations.findActiveByTenantAndParty(tenantId, partyId)
                .map(records -> resolveSupported(records, now));
    }

    private static Optional<SupportPartyContext> resolveSupported(
            List<PartyParticipantAssociation> records, Instant now) {
        if (records == null || records.isEmpty()) {
            return Optional.empty();
        }
        List<PartyParticipantAssociation> supported = records.stream()
                .filter(record -> record.isActiveAt(now))
                .filter(record -> record.role() == PartyParticipantRole.CUSTOMER
                        || record.role() == PartyParticipantRole.PARTNER)
                .toList();
        if (supported.size() != 1) {
            return Optional.empty();
        }
        PartyParticipantAssociation association = supported.get(0);
        RequesterRole requesterRole = association.role() == PartyParticipantRole.CUSTOMER
                ? RequesterRole.CUSTOMER : RequesterRole.PARTNER;
        return Optional.of(new SupportPartyContext(association.participantId(), requesterRole));
    }
}

package tech.kayys.numou.ecosystem.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.organization.Organization;
import tech.kayys.numou.ecosystem.domain.party.PartyId;

import java.util.Optional;

/** Outbound persistence port for organization profiles keyed by canonical Party identity. */
public interface OrganizationRepository {

    Uni<Organization> save(Organization organization);

    Uni<Optional<Organization>> findByPartyId(PartyId partyId);
}

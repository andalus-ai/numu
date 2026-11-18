package tech.kayys.numou.talent.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.PartyRef;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

/** Verifies that the referenced canonical Party exists as a PERSON in the caller's tenant scope. */
public interface PersonPartyDirectory {
    Uni<Boolean> existsPerson(TenantId tenantId, PartyRef subject);
}

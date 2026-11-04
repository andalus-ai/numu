package tech.kayys.numou.talent.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.talent.domain.profile.TalentProfile;
import tech.kayys.numou.talent.domain.profile.TalentProfileId;

import java.util.Optional;

/** Tenant-explicit persistence port. Implementations must include tenantId in every lookup and write predicate. */
public interface TalentProfileRepository {
    Uni<Optional<TalentProfile>> findByTenantAndId(TenantId tenantId, TalentProfileId profileId);
    Uni<TalentProfile> save(TenantId tenantId, TalentProfile profile);
}

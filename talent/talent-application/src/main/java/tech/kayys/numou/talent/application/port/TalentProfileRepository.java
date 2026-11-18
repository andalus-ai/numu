package tech.kayys.numou.talent.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.talent.domain.profile.TalentProfile;
import tech.kayys.numou.talent.domain.profile.TalentProfileId;

import java.util.Optional;

/**
 * Tenant-explicit persistence port. Inserts and updates are separate so an
 * adapter cannot accidentally turn a missing update into an upsert. Updates
 * must compare the stored version with expectedVersion in the SQL predicate.
 */
public interface TalentProfileRepository {
    Uni<Optional<TalentProfile>> findByTenantAndId(TenantId tenantId, TalentProfileId profileId);

    Uni<TalentProfile> insert(TenantId tenantId, TalentProfile profile);

    Uni<TalentProfile> update(TenantId tenantId, TalentProfile profile, int expectedVersion);
}

package tech.kayys.numou.socialgraph.application;

import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Optional;

/** Resolves an authenticated issuer/subject to its explicitly associated Party in a tenant. */
public interface SocialGraphActorResolver {
    Optional<SocialGraphActor> resolve(TenantId tenantId, IdentitySubjectRef identitySubject);
}

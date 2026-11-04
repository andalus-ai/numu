package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Optional;

/** Resolves an authenticated issuer/subject to its explicitly associated Party in a tenant. */
public interface SocialGraphActorResolver {
    Uni<Optional<SocialGraphActor>> resolve(TenantId tenantId, IdentitySubjectRef identitySubject);
}

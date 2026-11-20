package tech.kayys.numou.security.socialgraph;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.application.SocialGraphRelationshipReadPage;

/** Internal invocation seam for authenticated, policy-filtered relationship reads. */
public interface SocialGraphRelationshipReadInvoker {
    Uni<SocialGraphRelationshipReadPage> following(
            TenantId tenant, IdentitySubjectRef subject, String cursor, int limit);

    Uni<SocialGraphRelationshipReadPage> followers(
            TenantId tenant, IdentitySubjectRef subject, PartyId profileOwner, String cursor, int limit);
}

package tech.kayys.numou.security.socialgraph;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialConnection;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;
import tech.kayys.numou.socialgraph.domain.SocialRelationship;

/** Internal invocation seam for the trusted command gateway. */
public interface SocialGraphCommandInvoker {
    Uni<SocialConnection> requestConnection(TenantId tenant, IdentitySubjectRef subject, PartyId recipient, String key);
    Uni<SocialConnection> acceptConnection(TenantId tenant, IdentitySubjectRef subject, SocialConnectionId id, String key);
    Uni<SocialConnection> rejectConnection(TenantId tenant, IdentitySubjectRef subject, SocialConnectionId id, String key);
    Uni<SocialConnection> cancelConnection(TenantId tenant, IdentitySubjectRef subject, SocialConnectionId id, String key);
    Uni<SocialConnection> removeConnection(TenantId tenant, IdentitySubjectRef subject, SocialConnectionId id, String key);
    Uni<SocialRelationship> follow(TenantId tenant, IdentitySubjectRef subject, PartyId target, String key);
    Uni<SocialRelationship> unfollow(TenantId tenant, IdentitySubjectRef subject, PartyId target, String key);
    Uni<SocialRelationship> block(TenantId tenant, IdentitySubjectRef subject, PartyId target, String key);
    Uni<SocialRelationship> unblock(TenantId tenant, IdentitySubjectRef subject, PartyId target, String key);
    Uni<SocialRelationship> mute(TenantId tenant, IdentitySubjectRef subject, PartyId target, String key);
    Uni<SocialRelationship> unmute(TenantId tenant, IdentitySubjectRef subject, PartyId target, String key);
}

package tech.kayys.numou.security.socialgraph;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialConnection;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;

public interface SocialGraphCommandInvoker {
    Uni<SocialConnection> requestConnection(TenantId tenant, IdentitySubjectRef subject, PartyId recipient, String key);
    Uni<SocialConnection> acceptConnection(TenantId tenant, IdentitySubjectRef subject, SocialConnectionId id, String key);
    Uni<SocialConnection> rejectConnection(TenantId tenant, IdentitySubjectRef subject, SocialConnectionId id, String key);
    Uni<SocialConnection> cancelConnection(TenantId tenant, IdentitySubjectRef subject, SocialConnectionId id, String key);
    Uni<SocialConnection> removeConnection(TenantId tenant, IdentitySubjectRef subject, SocialConnectionId id, String key);
}

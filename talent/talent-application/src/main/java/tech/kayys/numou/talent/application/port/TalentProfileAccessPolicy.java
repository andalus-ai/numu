package tech.kayys.numou.talent.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.context.ExecutionContext;
import tech.kayys.numou.ecosystem.domain.party.PartyRef;
import tech.kayys.numou.talent.domain.profile.TalentProfile;

/** Authoritative policy boundary for ownership, tenant sharing, consent and moderation decisions. */
public interface TalentProfileAccessPolicy {
    enum Action { CREATE, UPDATE, CHANGE_VISIBILITY, PUBLISH, READ_PRIVATE, READ_NETWORK, READ_PUBLIC }

    Uni<Boolean> isAllowed(ExecutionContext context, Action action, PartyRef subject, TalentProfile profile);
}

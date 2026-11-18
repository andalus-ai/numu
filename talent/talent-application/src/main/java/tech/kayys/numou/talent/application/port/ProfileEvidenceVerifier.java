package tech.kayys.numou.talent.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.PartyRef;
import tech.kayys.numou.foundation.application.context.ExecutionContext;
import tech.kayys.numou.talent.domain.profile.ProfileContent;

/** Validates evidence before a claim may be marked as verified by an issuer or the platform. */
public interface ProfileEvidenceVerifier {
    Uni<Boolean> verifySkill(ExecutionContext context, PartyRef subject, ProfileContent.Skill skill);
    Uni<Boolean> verifyCredential(ExecutionContext context, PartyRef subject, ProfileContent.Credential credential);
}

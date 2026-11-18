package tech.kayys.numou.organization.application;

import tech.kayys.numou.identity.domain.user.UserId;
import java.util.concurrent.CompletionStage;

/** Resolves only active Identity users and their canonical verified email address. */
@FunctionalInterface
public interface VerifiedOrganizationInvitationIdentityPort {
    CompletionStage<VerifiedIdentity> requireVerifiedIdentity(UserId authenticatedUserId);

    record VerifiedIdentity(UserId userId, String verifiedEmail) { }
}
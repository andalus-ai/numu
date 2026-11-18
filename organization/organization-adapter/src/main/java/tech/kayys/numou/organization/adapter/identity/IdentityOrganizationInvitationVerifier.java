package tech.kayys.numou.organization.adapter.identity;

import tech.kayys.numou.identity.application.port.UserRepository;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.identity.domain.user.UserStatus;
import tech.kayys.numou.organization.application.VerifiedOrganizationInvitationIdentityPort;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Bridges invitation acceptance to Identity's canonical user and activation state. */
public final class IdentityOrganizationInvitationVerifier
        implements VerifiedOrganizationInvitationIdentityPort {
    private final UserRepository users;

    public IdentityOrganizationInvitationVerifier(UserRepository users) {
        this.users = Objects.requireNonNull(users);
    }

    @Override
    public CompletionStage<VerifiedIdentity> requireVerifiedIdentity(UserId authenticatedUserId) {
        Objects.requireNonNull(authenticatedUserId);
        return users.findById(authenticatedUserId).subscribeAsCompletionStage().thenApply(optional -> {
            var user = optional.orElseThrow(() -> new SecurityException("Authenticated identity is unavailable"));
            if (user.status() != UserStatus.ACTIVE) {
                throw new SecurityException("Invitation acceptance requires an active, verified identity");
            }
            return new VerifiedIdentity(user.id(), user.email().value());
        });
    }
}
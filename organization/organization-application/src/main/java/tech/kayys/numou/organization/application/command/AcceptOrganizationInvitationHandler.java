package tech.kayys.numou.organization.application.command;

import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.organization.domain.OrganizationMembershipId;
import tech.kayys.numou.organization.spi.OrganizationMembershipRepository;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Accepts an invitation after the caller has been authenticated. */
public final class AcceptOrganizationInvitationHandler {

    private final OrganizationMembershipRepository membershipRepository;

    public AcceptOrganizationInvitationHandler(OrganizationMembershipRepository membershipRepository) {
        this.membershipRepository = Objects.requireNonNull(membershipRepository, "membershipRepository");
    }

    public CompletionStage<Result<OrganizationMembershipId>> handle(
            AcceptOrganizationInvitationCommand command) {
        Objects.requireNonNull(command, "command");
        return membershipRepository.findByTenantAndId(command.tenantId(), command.membershipId())
                .thenCompose(optionalMembership -> {
                    var membership = optionalMembership.orElseThrow(
                            () -> new IllegalArgumentException("Invitation not found"));
                    membership.acceptInvitation(command.authenticatedUserId());
                    return membershipRepository.save(membership, command.tenantId())
                            .thenApply(saved -> Result.success(saved.getId()));
                });
    }
}

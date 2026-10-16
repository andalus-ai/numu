package tech.kayys.syirkah.organization.application.command;

import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.identity.domain.user.UserId;
import tech.kayys.syirkah.organization.application.port.IdentityUserPort;
import tech.kayys.syirkah.organization.domain.OrganizationMembership;
import tech.kayys.syirkah.organization.domain.OrganizationMembershipId;
import tech.kayys.syirkah.organization.spi.OrganizationMembershipRepository;
import tech.kayys.syirkah.organization.spi.OrganizationRepository;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class InviteOrganizationMemberHandler {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final IdentityUserPort identityUserPort;

    public InviteOrganizationMemberHandler(OrganizationRepository organizationRepository,
                                           OrganizationMembershipRepository membershipRepository,
                                           IdentityUserPort identityUserPort) {
        this.organizationRepository = Objects.requireNonNull(organizationRepository);
        this.membershipRepository = Objects.requireNonNull(membershipRepository);
        this.identityUserPort = Objects.requireNonNull(identityUserPort);
    }

    public CompletionStage<Result<OrganizationMembershipId>> handle(InviteOrganizationMemberCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        return organizationRepository.findById(command.organizationId()).thenCompose(optionalOrganization -> {
            var organization = optionalOrganization.orElseThrow(
                    () -> new IllegalArgumentException("Organization not found"));
            if (!organization.getTenantId().equals(command.tenantId())) {
                throw new IllegalArgumentException("Organization not found");
            }
            if (!organization.isActive()) {
                throw new IllegalStateException("Cannot invite members to an inactive organization");
            }
            return exists(command.userId()).thenCompose(exists -> {
                if (!exists) {
                    throw new IllegalArgumentException("Identity user not found");
                }
                return membershipRepository.findByOrganizationAndUser(
                                command.organizationId(), command.userId())
                        .thenCompose(existing -> {
                            if (existing.isPresent()) {
                                throw new IllegalStateException("User already has an organization membership");
                            }
                            var membership = OrganizationMembership.invite(
                                    OrganizationMembershipId.generate(),
                                    command.organizationId(),
                                    command.userId());
                            return membershipRepository.save(membership)
                                    .thenApply(saved -> Result.success(saved.getId()));
                        });
            });
        });
    }

    private CompletionStage<Boolean> exists(UserId userId) {
        return identityUserPort.exists(userId);
    }
}

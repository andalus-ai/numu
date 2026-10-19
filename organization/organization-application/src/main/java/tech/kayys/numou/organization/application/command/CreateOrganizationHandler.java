package tech.kayys.numou.organization.application.command;

import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.organization.domain.Organization;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.spi.OrganizationRepository;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class CreateOrganizationHandler {

    private final OrganizationRepository organizationRepository;

    public CreateOrganizationHandler(OrganizationRepository organizationRepository) {
        this.organizationRepository = Objects.requireNonNull(organizationRepository);
    }

    public CompletionStage<Result<OrganizationId>> handle(CreateOrganizationCommand cmd) {
        OrganizationId id = OrganizationId.generate();
        Organization org = Organization.create(
                id, cmd.tenantId(), cmd.name(), cmd.legalName(), cmd.registrationNumber());
        return organizationRepository.save(org)
                .thenApply(saved -> Result.success(id));
    }
}

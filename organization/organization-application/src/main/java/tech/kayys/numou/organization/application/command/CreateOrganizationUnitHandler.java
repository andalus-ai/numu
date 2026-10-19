package tech.kayys.numou.organization.application.command;

import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.organization.domain.OrganizationUnit;
import tech.kayys.numou.organization.domain.OrganizationUnitId;
import tech.kayys.numou.organization.spi.OrganizationUnitRepository;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class CreateOrganizationUnitHandler {

    private final OrganizationUnitRepository unitRepository;

    public CreateOrganizationUnitHandler(OrganizationUnitRepository unitRepository) {
        this.unitRepository = Objects.requireNonNull(unitRepository);
    }

    public CompletionStage<Result<OrganizationUnitId>> handle(CreateOrganizationUnitCommand cmd) {
        OrganizationUnitId id = OrganizationUnitId.generate();
        OrganizationUnit unit = OrganizationUnit.create(
                id, cmd.organizationId(), cmd.parentUnitId(), cmd.name(), cmd.code());
        return unitRepository.save(unit)
                .thenApply(saved -> Result.success(id));
    }
}

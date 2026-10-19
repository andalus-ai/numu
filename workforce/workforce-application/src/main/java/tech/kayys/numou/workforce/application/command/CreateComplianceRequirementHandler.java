package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.compliance.ComplianceRequirement;
import tech.kayys.numou.workforce.domain.compliance.ComplianceRequirementId;
import tech.kayys.numou.workforce.spi.port.ComplianceRequirementRepository;

import java.util.Objects;

public class CreateComplianceRequirementHandler implements CommandHandler<CreateComplianceRequirementCommand, Result<ComplianceRequirementId>> {

    private final ComplianceRequirementRepository repository;
    private final EventPublisher eventPublisher;

    public CreateComplianceRequirementHandler(ComplianceRequirementRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ComplianceRequirementId>> handle(CreateComplianceRequirementCommand cmd) {
        return Uni.createFrom().completionStage(repository.findByTenantAndCode(cmd.tenantId(), cmd.code()))
                .chain(optExisting -> {
                    if (optExisting.isPresent()) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("REQUIREMENT_EXISTS", "Compliance requirement already exists: " + cmd.code())));
                    }
                    ComplianceRequirement req;
                    try {
                        req = ComplianceRequirement.create(
                                cmd.tenantId(),
                                cmd.code(),
                                cmd.name(),
                                cmd.description(),
                                cmd.type(),
                                cmd.effectiveFrom(),
                                cmd.effectiveTo()
                        );
                    } catch (Exception e) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("INVALID_REQUIREMENT", e.getMessage())));
                    }
                    return Uni.createFrom().completionStage(repository.save(req))
                            .chain(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.getId())));
                });
    }
}

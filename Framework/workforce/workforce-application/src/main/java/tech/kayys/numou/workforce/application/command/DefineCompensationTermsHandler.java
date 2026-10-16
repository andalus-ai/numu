package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.compensation.CompensationTerms;
import tech.kayys.numou.workforce.domain.compensation.CompensationTermsId;
import tech.kayys.numou.workforce.spi.port.CompensationTermsRepository;
import tech.kayys.numou.workforce.spi.port.EmploymentRepository;

import java.util.Objects;

public class DefineCompensationTermsHandler implements CommandHandler<DefineCompensationTermsCommand, Result<CompensationTermsId>> {

    private final EmploymentRepository employmentRepository;
    private final CompensationTermsRepository compensationTermsRepository;
    private final EventPublisher eventPublisher;

    public DefineCompensationTermsHandler(
            EmploymentRepository employmentRepository,
            CompensationTermsRepository compensationTermsRepository,
            EventPublisher eventPublisher
    ) {
        this.employmentRepository = Objects.requireNonNull(employmentRepository);
        this.compensationTermsRepository = Objects.requireNonNull(compensationTermsRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<CompensationTermsId>> handle(DefineCompensationTermsCommand cmd) {
        return Uni.createFrom().completionStage(employmentRepository.findById(cmd.employmentId()))
                .chain(optEmployment -> {
                    if (optEmployment.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("EMPLOYMENT_NOT_FOUND", "Employment not found")));
                    }
                    CompensationTerms terms;
                    try {
                        terms = CompensationTerms.create(
                                CompensationTermsId.generate(),
                                cmd.employmentId(),
                                cmd.basePay(),
                                cmd.payFrequency(),
                                cmd.effectiveFrom()
                        );
                    } catch (Exception e) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("INVALID_COMPENSATION_TERMS", e.getMessage())));
                    }
                    return Uni.createFrom().completionStage(compensationTermsRepository.save(terms))
                            .chain(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.getId())));
                });
    }
}

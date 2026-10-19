package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.benefit.Benefit;
import tech.kayys.numou.workforce.domain.benefit.BenefitId;
import tech.kayys.numou.workforce.spi.port.BenefitRepository;

import java.util.Objects;

public class CreateBenefitHandler implements CommandHandler<CreateBenefitCommand, Result<BenefitId>> {

    private final BenefitRepository benefitRepository;
    private final EventPublisher eventPublisher;

    public CreateBenefitHandler(BenefitRepository benefitRepository, EventPublisher eventPublisher) {
        this.benefitRepository = Objects.requireNonNull(benefitRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<BenefitId>> handle(CreateBenefitCommand cmd) {
        return Uni.createFrom().completionStage(benefitRepository.findByCode(cmd.tenantId(), cmd.code()))
                .chain(existing -> {
                    if (existing.isPresent()) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("BENEFIT_EXISTS", "Benefit with code " + cmd.code() + " already exists")));
                    }
                    Benefit benefit;
                    try {
                        benefit = Benefit.create(
                                BenefitId.generate(),
                                cmd.tenantId(),
                                cmd.code(),
                                cmd.name(),
                                cmd.type()
                        );
                    } catch (Exception e) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("INVALID_BENEFIT", e.getMessage())));
                    }
                    return Uni.createFrom().completionStage(benefitRepository.save(benefit))
                            .chain(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.getId())));
                });
    }
}

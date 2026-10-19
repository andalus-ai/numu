package tech.kayys.numou.crm.application.referral;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.command.CreateReferralCommand;
import tech.kayys.numou.crm.domain.event.referral.ReferralCreated;
import tech.kayys.numou.crm.domain.referral.Referral;
import tech.kayys.numou.crm.domain.repository.ReferralRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;

import java.util.Objects;

/**
 * Handles the creation of a referral.
 */
public final class CreateReferralHandler
        implements CommandHandler<CreateReferralCommand, Result<Referral>> {

    private final ReferralRepository referralRepository;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;

    public CreateReferralHandler(ReferralRepository referralRepository,
                                 EventPublisher eventPublisher,
                                 UnitOfWork unitOfWork) {
        this.referralRepository = Objects.requireNonNull(referralRepository, "referralRepository cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork cannot be null");
    }

    @Override
    public Uni<Result<Referral>> handle(CreateReferralCommand command) {
        return unitOfWork.execute(() -> {
            final Referral referral = Referral.create(
                    command.referralId(),
                    command.referrerAccountId(),
                    command.target(),
                    command.type(),
                    command.code(),
                    command.description()
            );

            return Uni.createFrom().completionStage(referralRepository.save(referral))
                    .flatMap(saved -> eventPublisher.publish(saved.pullDomainEvents())
                            .replaceWith(Result.success(saved)));
        });
    }
}
package tech.kayys.numou.crm.application.referral;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.command.RejectReferralCommand;
import tech.kayys.numou.crm.domain.event.referral.ReferralRejected;
import tech.kayys.numou.crm.domain.referral.Referral;
import tech.kayys.numou.crm.domain.repository.ReferralRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;

import java.util.Objects;
import java.util.Optional;

/**
 * Handles rejecting a referral.
 */
public final class RejectReferralHandler
        implements CommandHandler<RejectReferralCommand, Result<Referral>> {

    private final ReferralRepository referralRepository;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;

    public RejectReferralHandler(ReferralRepository referralRepository,
                                 EventPublisher eventPublisher,
                                 UnitOfWork unitOfWork) {
        this.referralRepository = Objects.requireNonNull(referralRepository, "referralRepository cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork cannot be null");
    }

    @Override
    public Uni<Result<Referral>> handle(RejectReferralCommand command) {
        return unitOfWork.execute(() -> Uni.createFrom().completionStage(
                        referralRepository.findById(command.referralId()))
                .flatMap(optReferral -> {
                    Referral referral = optReferral.orElse(null);
                    if (referral == null) {
                        return Uni.createFrom().item(Result.failure(
                                ApplicationError.of("REFERRAL_NOT_FOUND", "Referral not found with id: " + command.referralId())));
                    }
                    if (referral.status() != tech.kayys.numou.crm.domain.referral.ReferralStatus.PENDING) {
                        return Uni.createFrom().item(Result.failure(
                                ApplicationError.of("REFERRAL_NOT_PENDING", "Only pending referrals can be rejected")));
                    }
                    referral.reject();
                    return Uni.createFrom().completionStage(referralRepository.save(referral))
                            .flatMap(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved)));
                }));
    }
}
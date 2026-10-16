package tech.kayys.syirkah.commerce.offering.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.offering.application.OfferingErrors;
import tech.kayys.syirkah.commerce.offering.application.command.RetireProductOfferingCommand;
import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.offering.spi.port.ProductOfferingRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.util.Objects;

public final class RetireProductOfferingHandler
        implements CommandHandler<RetireProductOfferingCommand, Result<ProductOfferingId>> {

    private final ProductOfferingRepository repository;
    private final EventPublisher eventPublisher;

    public RetireProductOfferingHandler(
            ProductOfferingRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProductOfferingId>> handle(RetireProductOfferingCommand command) {
        return Uni.createFrom()
                .completionStage(repository.findById(command.offeringId()))
                .onItem()
                .transformToUni(offeringOpt -> {
                    if (offeringOpt.isEmpty()) {
                        return Uni.createFrom().item(
                                Result.failure(
                                        ApplicationError.of(
                                                OfferingErrors.OFFERING_NOT_FOUND,
                                                "Offering not found: " + command.offeringId().value()
                                        )
                                )
                        );
                    }

                    var offering = offeringOpt.get();
                    try {
                        offering.retire();
                    } catch (RuntimeException ex) {
                        return Uni.createFrom().item(
                                Result.failure(
                                        ApplicationError.of(
                                                OfferingErrors.INVALID_OFFERING_STATE,
                                                ex.getMessage()
                                        )
                                )
                        );
                    }

                    return Uni.createFrom()
                            .completionStage(repository.save(offering))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }
}

package tech.kayys.numou.project.application.commercial.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.project.application.commercial.CommercialErrors;
import tech.kayys.numou.project.application.commercial.command.ApplyChangeOrderCommand;
import tech.kayys.numou.project.domain.commercial.ChangeOrderId;
import tech.kayys.numou.project.spi.port.ChangeOrderRepository;

import java.util.Objects;

public final class ApplyChangeOrderHandler
        implements CommandHandler<ApplyChangeOrderCommand, Result<ChangeOrderId>> {

    private final ChangeOrderRepository repository;
    private final EventPublisher eventPublisher;

    public ApplyChangeOrderHandler(
            ChangeOrderRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ChangeOrderId>> handle(ApplyChangeOrderCommand command) {
        return Uni.createFrom()
                .completionStage(repository.findById(command.changeOrderId()))
                .onItem()
                .transformToUni(orderOpt -> {
                    if (orderOpt.isEmpty()) {
                        return Uni.createFrom().item(
                                Result.failure(
                                        ApplicationError.of(
                                                CommercialErrors.CHANGE_ORDER_NOT_FOUND,
                                                "Change order not found: " + command.changeOrderId().value()
                                        )
                                )
                        );
                    }

                    var order = orderOpt.get();
                    try {
                        order.apply();
                    } catch (RuntimeException ex) {
                        return Uni.createFrom().item(
                                Result.failure(
                                        ApplicationError.of(
                                                CommercialErrors.INVALID_COMMERCIAL_STATE,
                                                ex.getMessage()
                                        )
                                )
                        );
                    }

                    return Uni.createFrom()
                            .completionStage(repository.save(order))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }
}

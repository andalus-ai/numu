package tech.kayys.syirkah.project.application.commercial.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.application.commercial.CommercialErrors;
import tech.kayys.syirkah.project.application.commercial.command.CancelChangeOrderCommand;
import tech.kayys.syirkah.project.domain.commercial.ChangeOrderId;
import tech.kayys.syirkah.project.spi.port.ChangeOrderRepository;

import java.util.Objects;

public final class CancelChangeOrderHandler
        implements CommandHandler<CancelChangeOrderCommand, Result<ChangeOrderId>> {

    private final ChangeOrderRepository repository;
    private final EventPublisher eventPublisher;

    public CancelChangeOrderHandler(
            ChangeOrderRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ChangeOrderId>> handle(CancelChangeOrderCommand command) {
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
                        order.cancel();
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

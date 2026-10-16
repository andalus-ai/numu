package tech.kayys.syirkah.project.application.commercial.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.application.commercial.CommercialErrors;
import tech.kayys.syirkah.project.application.commercial.command.CreateChangeOrderCommand;
import tech.kayys.syirkah.project.domain.commercial.ChangeOrder;
import tech.kayys.syirkah.project.domain.commercial.ChangeOrderId;
import tech.kayys.syirkah.project.spi.port.ChangeOrderRepository;

import java.util.Objects;

public final class CreateChangeOrderHandler
        implements CommandHandler<CreateChangeOrderCommand, Result<ChangeOrderId>> {

    private final ChangeOrderRepository repository;
    private final EventPublisher eventPublisher;

    public CreateChangeOrderHandler(
            ChangeOrderRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ChangeOrderId>> handle(CreateChangeOrderCommand command) {
        return Uni.createFrom()
                .completionStage(repository.findByNumber(command.projectId(), command.number()))
                .onItem()
                .transformToUni(existingOpt -> {
                    if (existingOpt.isPresent()) {
                        return Uni.createFrom().item(
                                Result.failure(
                                        ApplicationError.of(
                                                CommercialErrors.CHANGE_ORDER_NUMBER_ALREADY_EXISTS,
                                                "Change order number already exists: " + command.number()
                                        )
                                )
                        );
                    }

                    var id = ChangeOrderId.generate();
                    var order = ChangeOrder.create(
                            id,
                            command.projectId(),
                            command.contractId(),
                            command.changeRequestId(),
                            command.number(),
                            command.title(),
                            command.description(),
                            command.impact()
                    );

                    return Uni.createFrom()
                            .completionStage(repository.save(order))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }
}

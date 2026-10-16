package tech.kayys.syirkah.construction.application.variation.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.construction.application.variation.command.CreateChangeOrderCommand;
import tech.kayys.syirkah.construction.domain.variation.ChangeOrder;
import tech.kayys.syirkah.construction.spi.variation.ChangeOrderRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import java.util.Objects;

public final class CreateChangeOrderHandler implements CommandHandler<CreateChangeOrderCommand, ChangeOrder> {
    private final ChangeOrderRepository repository;
    private final EventPublisher eventPublisher;

    public CreateChangeOrderHandler(ChangeOrderRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<ChangeOrder> handle(CreateChangeOrderCommand command) {
        var co = ChangeOrder.create(command.projectId(), command.orderNumber(), command.title(), command.type(), command.reason());
        return Uni.createFrom()
                .completionStage(repository.save(co))
                .call(saved -> eventPublisher.publish(saved.pullDomainEvents()));
    }
}

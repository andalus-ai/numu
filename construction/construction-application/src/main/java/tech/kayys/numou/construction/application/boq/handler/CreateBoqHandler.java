package tech.kayys.numou.construction.application.boq.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.construction.application.boq.command.CreateBoqCommand;
import tech.kayys.numou.construction.domain.boq.Boq;
import tech.kayys.numou.construction.spi.boq.BoqRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import java.util.Objects;

public final class CreateBoqHandler implements CommandHandler<CreateBoqCommand, Boq> {
    private final BoqRepository repository;
    private final EventPublisher eventPublisher;

    public CreateBoqHandler(BoqRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Boq> handle(CreateBoqCommand command) {
        var boq = Boq.create(command.projectId(), command.name());
        return Uni.createFrom()
                .completionStage(repository.save(boq))
                .call(saved -> eventPublisher.publish(saved.pullDomainEvents()));
    }
}

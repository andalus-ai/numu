package tech.kayys.syirkah.construction.application.boq.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.construction.application.boq.command.CreateBoqCommand;
import tech.kayys.syirkah.construction.domain.boq.Boq;
import tech.kayys.syirkah.construction.spi.boq.BoqRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
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

package tech.kayys.syirkah.construction.application.document.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.construction.application.document.command.CreateDrawingCommand;
import tech.kayys.syirkah.construction.domain.document.Drawing;
import tech.kayys.syirkah.construction.spi.document.DrawingRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import java.util.Objects;

public final class CreateDrawingHandler implements CommandHandler<CreateDrawingCommand, Drawing> {
    private final DrawingRepository repository;
    private final EventPublisher eventPublisher;

    public CreateDrawingHandler(DrawingRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Drawing> handle(CreateDrawingCommand command) {
        var drawing = Drawing.create(command.projectId(), command.drawingNumber(), command.title(), command.discipline(), command.revision());
        return Uni.createFrom()
                .completionStage(repository.save(drawing))
                .call(saved -> eventPublisher.publish(saved.pullDomainEvents()));
    }
}

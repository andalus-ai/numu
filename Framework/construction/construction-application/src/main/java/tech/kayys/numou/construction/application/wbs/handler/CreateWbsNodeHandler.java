package tech.kayys.syirkah.construction.application.wbs.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.construction.application.wbs.command.CreateWbsNodeCommand;
import tech.kayys.syirkah.construction.domain.wbs.WbsNode;
import tech.kayys.syirkah.construction.spi.wbs.WbsNodeRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import java.util.Objects;

public final class CreateWbsNodeHandler implements CommandHandler<CreateWbsNodeCommand, WbsNode> {
    private final WbsNodeRepository repository;
    private final EventPublisher eventPublisher;

    public CreateWbsNodeHandler(WbsNodeRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<WbsNode> handle(CreateWbsNodeCommand command) {
        var node = WbsNode.create(command.projectId(), command.parentNodeId(), command.code(), command.name(), command.type());
        return Uni.createFrom()
                .completionStage(repository.save(node))
                .call(saved -> eventPublisher.publish(saved.pullDomainEvents()));
    }
}

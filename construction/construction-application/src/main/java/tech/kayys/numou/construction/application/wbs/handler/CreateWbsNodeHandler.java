package tech.kayys.numou.construction.application.wbs.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.construction.application.wbs.command.CreateWbsNodeCommand;
import tech.kayys.numou.construction.domain.wbs.WbsNode;
import tech.kayys.numou.construction.spi.wbs.WbsNodeRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.scope.domain.BreakdownNode;
import tech.kayys.numou.scope.domain.BreakdownNodeId;
import tech.kayys.numou.scope.domain.BreakdownStructureId;
import tech.kayys.numou.scope.domain.BreakdownTreeValidator;

import java.util.ArrayList;
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
                .completionStage(repository.findByProjectId(command.projectId()))
                .onItem().transformToUni(existing -> {
                    var candidateTree = new ArrayList<BreakdownNode>(existing.size() + 1);
                    existing.forEach(existingNode -> candidateTree.add(toBreakdownNode(existingNode)));
                    candidateTree.add(toBreakdownNode(node));

                    var violations = BreakdownTreeValidator.validate(candidateTree);
                    if (!violations.isEmpty()) {
                        var details = violations.stream()
                                .map(violation -> violation.code() + ": " + violation.message())
                                .distinct()
                                .reduce((left, right) -> left + "; " + right)
                                .orElse("Invalid WBS tree");
                        return Uni.createFrom().failure(
                                new IllegalArgumentException("WBS tree validation failed: " + details));
                    }

                    return Uni.createFrom()
                            .completionStage(repository.save(node))
                            .call(saved -> eventPublisher.publish(saved.pullDomainEvents()));
                });
    }

    private static BreakdownNode toBreakdownNode(WbsNode node) {
        return new BreakdownNode(
                new BreakdownNodeId(node.id().value()),
                new BreakdownStructureId(node.projectId()),
                node.parentNodeId() == null ? null : new BreakdownNodeId(node.parentNodeId()),
                node.code(),
                node.name(),
                node.type().name(),
                0);
    }
}

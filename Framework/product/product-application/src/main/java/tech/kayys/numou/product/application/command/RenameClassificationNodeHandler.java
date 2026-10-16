package tech.kayys.syirkah.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.product.domain.classification.ClassificationNode;
import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;
import tech.kayys.syirkah.product.spi.port.ClassificationNodeRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Renames an active classification node. The archived guard is enforced
 * by the aggregate itself.
 */
public final class RenameClassificationNodeHandler
        implements CommandHandler<
        RenameClassificationNodeCommand, Result<ClassificationNodeId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of(
                    "CLASSIFICATION_NODE_NOT_FOUND",
                    "Classification node does not exist"
            );

    private final ClassificationNodeRepository nodes;
    private final EventPublisher eventPublisher;

    public RenameClassificationNodeHandler(
            ClassificationNodeRepository nodes,
            EventPublisher eventPublisher
    ) {
        this.nodes = Objects.requireNonNull(nodes);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ClassificationNodeId>> handle(
            RenameClassificationNodeCommand command
    ) {
        return Uni.createFrom()
                .completionStage(nodes.findById(command.nodeId()))
                .onItem()
                .transformToUni(maybeNode -> rename(maybeNode, command));
    }

    private Uni<Result<ClassificationNodeId>> rename(
            Optional<ClassificationNode> maybeNode,
            RenameClassificationNodeCommand command
    ) {
        if (maybeNode.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var node = maybeNode.get();
        node.rename(command.name());

        return Uni.createFrom()
                .completionStage(nodes.save(node))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}

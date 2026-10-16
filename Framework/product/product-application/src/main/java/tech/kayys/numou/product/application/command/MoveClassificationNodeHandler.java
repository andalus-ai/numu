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
 * Moves a classification node under a new parent (or to a root when the
 * new parent is null). The node aggregate only rejects becoming its own
 * parent; this handler additionally rejects a move that would create a
 * cycle by walking the candidate parent's ancestor chain, and verifies
 * the new parent lives in the same scheme (product02.md, section 5).
 */
public final class MoveClassificationNodeHandler
        implements CommandHandler<
        MoveClassificationNodeCommand, Result<ClassificationNodeId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of(
                    "CLASSIFICATION_NODE_NOT_FOUND",
                    "Classification node does not exist"
            );

    private static final ApplicationError PARENT_NOT_FOUND =
            ApplicationError.of(
                    "CLASSIFICATION_NODE_PARENT_NOT_FOUND",
                    "Parent classification node does not exist"
            );

    private static final ApplicationError PARENT_WRONG_SCHEME =
            ApplicationError.of(
                    "CLASSIFICATION_NODE_PARENT_WRONG_SCHEME",
                    "Parent classification node belongs to a different scheme"
            );

    private static final ApplicationError CYCLE_DETECTED =
            ApplicationError.of(
                    "CLASSIFICATION_NODE_CYCLE_DETECTED",
                    "Move would create a cycle in the classification tree"
            );

    private final ClassificationNodeRepository nodes;
    private final EventPublisher eventPublisher;

    public MoveClassificationNodeHandler(
            ClassificationNodeRepository nodes,
            EventPublisher eventPublisher
    ) {
        this.nodes = Objects.requireNonNull(nodes);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ClassificationNodeId>> handle(
            MoveClassificationNodeCommand command
    ) {
        return Uni.createFrom()
                .completionStage(nodes.findById(command.nodeId()))
                .onItem()
                .transformToUni(maybeNode -> move(maybeNode, command));
    }

    private Uni<Result<ClassificationNodeId>> move(
            Optional<ClassificationNode> maybeNode,
            MoveClassificationNodeCommand command
    ) {
        if (maybeNode.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var node = maybeNode.get();

        if (command.newParentNodeId() == null) {
            return applyMove(node, null);
        }

        return Uni.createFrom()
                .completionStage(nodes.findById(command.newParentNodeId()))
                .onItem()
                .transformToUni(parent -> validateParent(parent, node, command));
    }

    private Uni<Result<ClassificationNodeId>> validateParent(
            Optional<ClassificationNode> maybeParent,
            ClassificationNode node,
            MoveClassificationNodeCommand command
    ) {
        if (maybeParent.isEmpty()) {
            return Uni.createFrom().item(Result.failure(PARENT_NOT_FOUND));
        }

        var parent = maybeParent.get();

        if (!parent.schemeId().equals(node.schemeId())) {
            return Uni.createFrom().item(Result.failure(PARENT_WRONG_SCHEME));
        }

        return createsCycle(command.newParentNodeId(), node.id())
                .onItem()
                .transformToUni(cycle -> cycle
                        ? Uni.createFrom().item(Result.failure(CYCLE_DETECTED))
                        : applyMove(node, command.newParentNodeId()));
    }

    /**
     * True when {@code nodeId} appears in the ancestor chain reached by
     * walking up from {@code candidateParentId} - i.e. the candidate
     * parent is the node itself or one of its descendants.
     */
    private Uni<Boolean> createsCycle(
            ClassificationNodeId candidateParentId,
            ClassificationNodeId nodeId
    ) {
        return Uni.createFrom()
                .completionStage(nodes.findById(candidateParentId))
                .onItem()
                .transformToUni(maybeAncestor -> {
                    if (maybeAncestor.isEmpty()) {
                        return Uni.createFrom().item(false);
                    }

                    var ancestor = maybeAncestor.get();

                    if (ancestor.id().equals(nodeId)) {
                        return Uni.createFrom().item(true);
                    }

                    if (ancestor.parentNodeId() == null) {
                        return Uni.createFrom().item(false);
                    }

                    return createsCycle(ancestor.parentNodeId(), nodeId);
                });
    }

    private Uni<Result<ClassificationNodeId>> applyMove(
            ClassificationNode node,
            ClassificationNodeId newParentNodeId
    ) {
        node.moveTo(newParentNodeId);

        return Uni.createFrom()
                .completionStage(nodes.save(node))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}

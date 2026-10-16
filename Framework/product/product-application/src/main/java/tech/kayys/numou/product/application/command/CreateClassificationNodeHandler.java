package tech.kayys.syirkah.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.product.domain.classification.ClassificationNode;
import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;
import tech.kayys.syirkah.product.domain.classification.ClassificationScheme;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeStatus;
import tech.kayys.syirkah.product.spi.port.ClassificationNodeRepository;
import tech.kayys.syirkah.product.spi.port.ClassificationSchemeRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Creates a classification node under an active scheme. Enforces the
 * cross-aggregate rules the node aggregate cannot see on its own: the
 * scheme must exist and be active, an optional parent must exist and
 * belong to the same scheme, and the node code must be unique within
 * the scheme (product02.md, sections 5 and 8).
 */
public final class CreateClassificationNodeHandler
        implements CommandHandler<
        CreateClassificationNodeCommand, Result<ClassificationNodeId>> {

    private static final ApplicationError SCHEME_NOT_FOUND =
            ApplicationError.of(
                    "CLASSIFICATION_SCHEME_NOT_FOUND",
                    "Classification scheme does not exist"
            );

    private static final ApplicationError SCHEME_NOT_ACTIVE =
            ApplicationError.of(
                    "CLASSIFICATION_SCHEME_NOT_ACTIVE",
                    "Classification scheme is not active"
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

    private static final ApplicationError CODE_ALREADY_EXISTS =
            ApplicationError.of(
                    "CLASSIFICATION_NODE_CODE_ALREADY_EXISTS",
                    "A classification node with this code already exists in the scheme"
            );

    private final ClassificationSchemeRepository schemes;
    private final ClassificationNodeRepository nodes;
    private final EventPublisher eventPublisher;

    public CreateClassificationNodeHandler(
            ClassificationSchemeRepository schemes,
            ClassificationNodeRepository nodes,
            EventPublisher eventPublisher
    ) {
        this.schemes = Objects.requireNonNull(schemes);
        this.nodes = Objects.requireNonNull(nodes);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ClassificationNodeId>> handle(
            CreateClassificationNodeCommand command
    ) {
        return Uni.createFrom()
                .completionStage(schemes.findById(command.schemeId()))
                .onItem()
                .transformToUni(scheme -> validateScheme(scheme, command));
    }

    private Uni<Result<ClassificationNodeId>> validateScheme(
            Optional<ClassificationScheme> maybeScheme,
            CreateClassificationNodeCommand command
    ) {
        if (maybeScheme.isEmpty()) {
            return Uni.createFrom().item(Result.failure(SCHEME_NOT_FOUND));
        }

        if (maybeScheme.get().status() != ClassificationSchemeStatus.ACTIVE) {
            return Uni.createFrom().item(Result.failure(SCHEME_NOT_ACTIVE));
        }

        if (command.parentNodeId() == null) {
            return rejectDuplicateCode(command);
        }

        return Uni.createFrom()
                .completionStage(nodes.findById(command.parentNodeId()))
                .onItem()
                .transformToUni(parent -> validateParent(parent, command));
    }

    private Uni<Result<ClassificationNodeId>> validateParent(
            Optional<ClassificationNode> maybeParent,
            CreateClassificationNodeCommand command
    ) {
        if (maybeParent.isEmpty()) {
            return Uni.createFrom().item(Result.failure(PARENT_NOT_FOUND));
        }

        if (!maybeParent.get().schemeId().equals(command.schemeId())) {
            return Uni.createFrom().item(Result.failure(PARENT_WRONG_SCHEME));
        }

        return rejectDuplicateCode(command);
    }

    private Uni<Result<ClassificationNodeId>> rejectDuplicateCode(
            CreateClassificationNodeCommand command
    ) {
        return Uni.createFrom()
                .completionStage(
                        nodes.existsByCode(command.schemeId(), command.code()))
                .onItem()
                .transformToUni(exists ->
                        exists ? duplicate() : create(command));
    }

    private Uni<Result<ClassificationNodeId>> duplicate() {
        return Uni.createFrom().item(Result.failure(CODE_ALREADY_EXISTS));
    }

    private Uni<Result<ClassificationNodeId>> create(
            CreateClassificationNodeCommand command
    ) {
        var node = ClassificationNode.create(
                ClassificationNodeId.generate(),
                command.schemeId(),
                command.parentNodeId(),
                command.code(),
                command.name(),
                command.sortOrder()
        );

        return Uni.createFrom()
                .completionStage(nodes.save(node))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}

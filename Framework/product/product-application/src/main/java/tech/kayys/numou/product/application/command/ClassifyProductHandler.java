package tech.kayys.syirkah.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.product.domain.classification.ClassificationNode;
import tech.kayys.syirkah.product.domain.classification.ClassificationNodeStatus;
import tech.kayys.syirkah.product.domain.classification.ClassificationScheme;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeStatus;
import tech.kayys.syirkah.product.domain.classification.ProductClassification;
import tech.kayys.syirkah.product.domain.event.ProductClassified;
import tech.kayys.syirkah.product.spi.port.ClassificationNodeRepository;
import tech.kayys.syirkah.product.spi.port.ClassificationSchemeRepository;
import tech.kayys.syirkah.product.spi.port.ProductClassificationRepository;
import tech.kayys.syirkah.product.spi.port.ProductRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Classifies a product under a node of a scheme. This is the correct
 * place for the cross-aggregate consistency rules the individual
 * aggregates cannot enforce alone (product02.md, sections 8 and 10):
 * the product must exist, the scheme and node must both be active, the
 * node must belong to the scheme, and the classification must not
 * already exist.
 */
public final class ClassifyProductHandler
        implements CommandHandler<
        ClassifyProductCommand, Result<ProductClassification>> {

    private static final ApplicationError PRODUCT_NOT_FOUND =
            ApplicationError.of(
                    "PRODUCT_NOT_FOUND",
                    "Product does not exist"
            );

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

    private static final ApplicationError NODE_NOT_FOUND =
            ApplicationError.of(
                    "CLASSIFICATION_NODE_NOT_FOUND",
                    "Classification node does not exist"
            );

    private static final ApplicationError NODE_NOT_ACTIVE =
            ApplicationError.of(
                    "CLASSIFICATION_NODE_NOT_ACTIVE",
                    "Classification node is not active"
            );

    private static final ApplicationError NODE_WRONG_SCHEME =
            ApplicationError.of(
                    "CLASSIFICATION_NODE_WRONG_SCHEME",
                    "Classification node does not belong to the scheme"
            );

    private static final ApplicationError ALREADY_CLASSIFIED =
            ApplicationError.of(
                    "PRODUCT_ALREADY_CLASSIFIED",
                    "Product is already classified under this scheme and node"
            );

    private final ProductRepository products;
    private final ClassificationSchemeRepository schemes;
    private final ClassificationNodeRepository nodes;
    private final ProductClassificationRepository classifications;
    private final EventPublisher eventPublisher;

    public ClassifyProductHandler(
            ProductRepository products,
            ClassificationSchemeRepository schemes,
            ClassificationNodeRepository nodes,
            ProductClassificationRepository classifications,
            EventPublisher eventPublisher
    ) {
        this.products = Objects.requireNonNull(products);
        this.schemes = Objects.requireNonNull(schemes);
        this.nodes = Objects.requireNonNull(nodes);
        this.classifications = Objects.requireNonNull(classifications);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProductClassification>> handle(
            ClassifyProductCommand command
    ) {
        return Uni.createFrom()
                .completionStage(products.existsById(command.productId()))
                .onItem()
                .transformToUni(productExists ->
                        productExists
                                ? loadScheme(command)
                                : failure(PRODUCT_NOT_FOUND));
    }

    private Uni<Result<ProductClassification>> loadScheme(
            ClassifyProductCommand command
    ) {
        return Uni.createFrom()
                .completionStage(schemes.findById(command.schemeId()))
                .onItem()
                .transformToUni(scheme -> validateScheme(scheme, command));
    }

    private Uni<Result<ProductClassification>> validateScheme(
            Optional<ClassificationScheme> maybeScheme,
            ClassifyProductCommand command
    ) {
        if (maybeScheme.isEmpty()) {
            return failure(SCHEME_NOT_FOUND);
        }

        if (maybeScheme.get().status() != ClassificationSchemeStatus.ACTIVE) {
            return failure(SCHEME_NOT_ACTIVE);
        }

        return Uni.createFrom()
                .completionStage(nodes.findById(command.nodeId()))
                .onItem()
                .transformToUni(node -> validateNode(node, command));
    }

    private Uni<Result<ProductClassification>> validateNode(
            Optional<ClassificationNode> maybeNode,
            ClassifyProductCommand command
    ) {
        if (maybeNode.isEmpty()) {
            return failure(NODE_NOT_FOUND);
        }

        var node = maybeNode.get();

        if (node.status() != ClassificationNodeStatus.ACTIVE) {
            return failure(NODE_NOT_ACTIVE);
        }

        if (!node.schemeId().equals(command.schemeId())) {
            return failure(NODE_WRONG_SCHEME);
        }

        return Uni.createFrom()
                .completionStage(classifications.exists(
                        command.productId(),
                        command.schemeId(),
                        command.nodeId()))
                .onItem()
                .transformToUni(exists ->
                        exists ? failure(ALREADY_CLASSIFIED) : save(command));
    }

    private Uni<Result<ProductClassification>> save(
            ClassifyProductCommand command
    ) {
        var classification = new ProductClassification(
                command.productId(),
                command.schemeId(),
                command.nodeId()
        );

        return Uni.createFrom()
                .completionStage(classifications.save(classification))
                .onItem()
                .transformToUni(ignored -> eventPublisher
                        .publish(List.of(new ProductClassified(
                                UUID.randomUUID(),
                                Instant.now(),
                                command.productId(),
                                command.schemeId(),
                                command.nodeId())))
                        .replaceWith(Result.success(classification)));
    }

    private static Uni<Result<ProductClassification>> failure(
            ApplicationError error
    ) {
        return Uni.createFrom().item(Result.failure(error));
    }
}

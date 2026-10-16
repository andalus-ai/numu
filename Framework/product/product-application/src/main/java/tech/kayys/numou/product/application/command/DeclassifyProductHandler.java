package tech.kayys.syirkah.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.product.domain.classification.ProductClassification;
import tech.kayys.syirkah.product.domain.event.ProductDeclassified;
import tech.kayys.syirkah.product.spi.port.ProductClassificationRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Removes a product's classification under a scheme/node, rejecting the
 * command when no such classification exists.
 */
public final class DeclassifyProductHandler
        implements CommandHandler<
        DeclassifyProductCommand, Result<ProductClassification>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of(
                    "PRODUCT_CLASSIFICATION_NOT_FOUND",
                    "Product is not classified under this scheme and node"
            );

    private final ProductClassificationRepository classifications;
    private final EventPublisher eventPublisher;

    public DeclassifyProductHandler(
            ProductClassificationRepository classifications,
            EventPublisher eventPublisher
    ) {
        this.classifications = Objects.requireNonNull(classifications);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProductClassification>> handle(
            DeclassifyProductCommand command
    ) {
        return Uni.createFrom()
                .completionStage(classifications.exists(
                        command.productId(),
                        command.schemeId(),
                        command.nodeId()))
                .onItem()
                .transformToUni(exists ->
                        exists ? remove(command) : failure());
    }

    private Uni<Result<ProductClassification>> remove(
            DeclassifyProductCommand command
    ) {
        var classification = new ProductClassification(
                command.productId(),
                command.schemeId(),
                command.nodeId()
        );

        return Uni.createFrom()
                .completionStage(classifications.remove(
                        command.productId(),
                        command.schemeId(),
                        command.nodeId()))
                .onItem()
                .transformToUni(ignored -> eventPublisher
                        .publish(List.of(new ProductDeclassified(
                                UUID.randomUUID(),
                                Instant.now(),
                                command.productId(),
                                command.schemeId(),
                                command.nodeId())))
                        .replaceWith(Result.success(classification)));
    }

    private Uni<Result<ProductClassification>> failure() {
        return Uni.createFrom().item(Result.failure(NOT_FOUND));
    }
}

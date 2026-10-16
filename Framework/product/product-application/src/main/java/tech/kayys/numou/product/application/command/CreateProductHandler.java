package tech.kayys.syirkah.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.product.domain.product.Product;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.spi.port.ProductRepository;

import java.util.Objects;

/**
 * Creates a draft product, rejecting a duplicate product code.
 */
public final class CreateProductHandler
        implements CommandHandler<CreateProductCommand, Result<ProductId>> {

    private static final ApplicationError CODE_ALREADY_EXISTS =
            ApplicationError.of(
                    "PRODUCT_CODE_ALREADY_EXISTS",
                    "A product with this code already exists"
            );

    private final ProductRepository products;
    private final EventPublisher eventPublisher;

    public CreateProductHandler(
            ProductRepository products,
            EventPublisher eventPublisher
    ) {
        this.products = Objects.requireNonNull(products);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProductId>> handle(CreateProductCommand command) {
        return Uni.createFrom()
                .completionStage(products.existsByCode(command.code()))
                .onItem()
                .transformToUni(exists ->
                        exists ? duplicate() : create(command));
    }

    private Uni<Result<ProductId>> duplicate() {
        return Uni.createFrom().item(Result.failure(CODE_ALREADY_EXISTS));
    }

    private Uni<Result<ProductId>> create(CreateProductCommand command) {
        var product = Product.create(
                ProductId.generate(),
                command.code(),
                command.name(),
                command.description(),
                command.type()
        );

        return Uni.createFrom()
                .completionStage(products.save(product))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
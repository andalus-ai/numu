package tech.kayys.syirkah.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.product.domain.identifier.DefaultIdentifierNormalizer;
import tech.kayys.syirkah.product.domain.identifier.DefaultIdentifierValidator;
import tech.kayys.syirkah.product.domain.identifier.IdentifierNormalizer;
import tech.kayys.syirkah.product.domain.identifier.IdentifierValidator;
import tech.kayys.syirkah.product.domain.identifier.ProductIdentifier;
import tech.kayys.syirkah.product.domain.product.Product;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.spi.port.ProductRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Adds an external identifier to a product. Duplicate identifiers
 * are rejected by the aggregate.
 * <p>
 * Normalizes the value first (e.g. " 123456789012 " → "123456789012")
 * and validates the shape, so uniqueness checks and persistence use
 * canonical values (product02.md).
 */
public final class AddProductIdentifierHandler
        implements CommandHandler<
        AddProductIdentifierCommand, Result<ProductId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of(
                    "PRODUCT_NOT_FOUND",
                    "Product does not exist"
            );

    private static final ApplicationError BUSINESS_RULE =
            ApplicationError.of(
                    "BUSINESS_RULE_VIOLATION",
                    "The identifier is invalid or already exists"
            );

    private final ProductRepository products;
    private final EventPublisher eventPublisher;
    private final IdentifierNormalizer normalizer;
    private final IdentifierValidator validator;

    public AddProductIdentifierHandler(
            ProductRepository products,
            EventPublisher eventPublisher
    ) {
        this(products, eventPublisher,
                new DefaultIdentifierNormalizer(),
                new DefaultIdentifierValidator());
    }

    public AddProductIdentifierHandler(
            ProductRepository products,
            EventPublisher eventPublisher,
            IdentifierNormalizer normalizer,
            IdentifierValidator validator
    ) {
        this.products = Objects.requireNonNull(products);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.normalizer = Objects.requireNonNull(normalizer);
        this.validator = Objects.requireNonNull(validator);
    }

    @Override
    public Uni<Result<ProductId>> handle(
            AddProductIdentifierCommand command
    ) {
        return Uni.createFrom()
                .completionStage(products.findById(command.productId()))
                .onItem()
                .transformToUni(maybeProduct ->
                        add(maybeProduct, command));
    }

    private Uni<Result<ProductId>> add(
            Optional<Product> maybeProduct,
            AddProductIdentifierCommand command
    ) {
        if (maybeProduct.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var normalizedOr = normalize(command.identifier());
        if (normalizedOr.isFailure()) {
            return Uni.createFrom().item(Result.failure(BUSINESS_RULE));
        }

        var product = maybeProduct.get();
        var normalized = normalizedOr.orElseThrow();
        try {
            product.addIdentifier(normalized);
        } catch (BusinessRuleViolation e) {
            return Uni.createFrom().item(Result.failure(BUSINESS_RULE));
        }

        return Uni.createFrom()
                .completionStage(products.save(product))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }

    private Result<ProductIdentifier> normalize(ProductIdentifier identifier) {
        var value = normalizer.normalize(
                identifier.type(), identifier.value());
        try {
            validator.validate(new ProductIdentifier(
                    identifier.type(), value, identifier.scope()));
        } catch (BusinessRuleViolation | IllegalArgumentException e) {
            return Result.failure(BUSINESS_RULE);
        }
        return Result.success(
                new ProductIdentifier(
                        identifier.type(), value, identifier.scope()));
    }
}
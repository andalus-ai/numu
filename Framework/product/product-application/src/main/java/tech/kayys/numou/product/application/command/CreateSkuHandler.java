package tech.kayys.syirkah.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.product.domain.sku.Sku;
import tech.kayys.syirkah.product.domain.sku.SkuId;
import tech.kayys.syirkah.product.domain.variant.ProductVariant;
import tech.kayys.syirkah.product.spi.port.ProductRepository;
import tech.kayys.syirkah.product.spi.port.ProductVariantRepository;
import tech.kayys.syirkah.product.spi.port.SkuRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Creates a stock keeping unit (product02.md).
 *
 * Verifies the product exists, SKU code is unique, and when a variant
 * is supplied it belongs to the same product.
 */
public final class CreateSkuHandler
        implements CommandHandler<CreateSkuCommand, Result<SkuId>> {

    private static final ApplicationError PRODUCT_NOT_FOUND =
            ApplicationError.of(
                    "PRODUCT_NOT_FOUND",
                    "Product does not exist"
            );

    private static final ApplicationError VARIANT_NOT_FOUND =
            ApplicationError.of(
                    "VARIANT_NOT_FOUND",
                    "Product variant does not exist"
            );

    private static final ApplicationError VARIANT_MISMATCH =
            ApplicationError.of(
                    "VARIANT_PRODUCT_MISMATCH",
                    "Product variant does not belong to product"
            );

    private static final ApplicationError CODE_ALREADY_EXISTS =
            ApplicationError.of(
                    "SKU_CODE_ALREADY_EXISTS",
                    "A SKU with this code already exists"
            );

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final SkuRepository skus;
    private final EventPublisher eventPublisher;

    public CreateSkuHandler(
            ProductRepository products,
            SkuRepository skus,
            EventPublisher eventPublisher
    ) {
        this(products, null, skus, eventPublisher);
    }

    public CreateSkuHandler(
            ProductRepository products,
            ProductVariantRepository variants,
            SkuRepository skus,
            EventPublisher eventPublisher
    ) {
        this.products = Objects.requireNonNull(products);
        this.variants = variants;
        this.skus = Objects.requireNonNull(skus);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<SkuId>> handle(CreateSkuCommand command) {
        return Uni.createFrom()
                .completionStage(products.findById(command.productId()))
                .onItem()
                .transformToUni(maybeProduct -> {
                    if (maybeProduct.isEmpty()) {
                        return Uni.createFrom().item(
                                Result.failure(PRODUCT_NOT_FOUND)
                        );
                    }
                    return verifyVariant(command);
                });
    }

    private Uni<Result<SkuId>> verifyVariant(CreateSkuCommand command) {
        if (command.variantId() == null) {
            return checkCode(command);
        }
        if (variants == null) {
            return checkCode(command);
        }
        return Uni.createFrom()
                .completionStage(variants.findById(command.variantId()))
                .onItem()
                .transformToUni(maybeVariant -> {
                    if (maybeVariant.isEmpty()) {
                        return Uni.createFrom().item(
                                Result.failure(VARIANT_NOT_FOUND));
                    }
                    ProductVariant variant = maybeVariant.get();
                    if (!variant.productId().equals(command.productId())) {
                        return Uni.createFrom().item(
                                Result.failure(VARIANT_MISMATCH));
                    }
                    return checkCode(command);
                });
    }

    private Uni<Result<SkuId>> checkCode(CreateSkuCommand command) {
        return Uni.createFrom()
                .completionStage(skus.existsByCode(command.code()))
                .onItem()
                .transformToUni(exists -> exists
                        ? Uni.createFrom().item(
                                Result.failure(CODE_ALREADY_EXISTS))
                        : create(command));
    }

    private Uni<Result<SkuId>> create(CreateSkuCommand command) {
        var sku = Sku.create(
                SkuId.generate(),
                command.productId(),
                command.variantId(),
                command.code(),
                command.name()
        );

        return Uni.createFrom()
                .completionStage(skus.save(sku))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}

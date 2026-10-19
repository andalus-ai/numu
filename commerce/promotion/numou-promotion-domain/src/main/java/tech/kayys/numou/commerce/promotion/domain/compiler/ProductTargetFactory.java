package tech.kayys.numou.commerce.promotion.domain.compiler;

import tech.kayys.numou.commerce.promotion.domain.target.ProductTarget;
import tech.kayys.numou.commerce.promotion.domain.target.SingleTargetSelection;
import tech.kayys.numou.product.domain.product.ProductId;

import java.util.UUID;

/** Compiles {@code target: product} into a {@link ProductTarget} (product02.md §18). */
public final class ProductTargetFactory implements CompiledTargetFactory {

    private static final TargetType TYPE = TargetType.of("product");

    @Override
    public TargetType type() {
        return TYPE;
    }

    @Override
    public CompiledTarget compile(TargetDefinition definition) {
        UUID productId = ParameterReader.uuid(definition.parameters(), "productId");
        ProductTarget target = new ProductTarget(ProductId.of(productId));
        return context -> new SingleTargetSelection(target);
    }
}
package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.target.SingleTargetSelection;
import tech.kayys.syirkah.commerce.promotion.domain.target.SkuTarget;
import tech.kayys.syirkah.product.domain.sku.SkuId;

import java.util.UUID;

/** Compiles {@code target: sku} into a {@link SkuTarget} (product02.md §18). */
public final class SkuTargetFactory implements CompiledTargetFactory {

    private static final TargetType TYPE = TargetType.of("sku");

    @Override
    public TargetType type() {
        return TYPE;
    }

    @Override
    public CompiledTarget compile(TargetDefinition definition) {
        UUID skuId = ParameterReader.uuid(definition.parameters(), "skuId");
        SkuTarget target = new SkuTarget(SkuId.of(skuId));
        return context -> new SingleTargetSelection(target);
    }
}
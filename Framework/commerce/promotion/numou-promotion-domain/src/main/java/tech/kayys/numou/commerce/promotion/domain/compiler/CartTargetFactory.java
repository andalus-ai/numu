package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.target.CartTarget;
import tech.kayys.syirkah.commerce.promotion.domain.target.SingleTargetSelection;

public final class CartTargetFactory implements CompiledTargetFactory {

    private static final TargetType TYPE = TargetType.of("cart");

    @Override
    public TargetType type() {
        return TYPE;
    }

    @Override
    public CompiledTarget compile(TargetDefinition definition) {
        return context -> new SingleTargetSelection(CartTarget.INSTANCE);
    }
}

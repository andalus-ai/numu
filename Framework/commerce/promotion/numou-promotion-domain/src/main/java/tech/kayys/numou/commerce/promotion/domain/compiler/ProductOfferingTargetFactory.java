package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.promotion.domain.target.ProductOfferingTarget;
import tech.kayys.syirkah.commerce.promotion.domain.target.SingleTargetSelection;

import java.util.UUID;

/**
 * Compiles {@code target: offering} into a {@link ProductOfferingTarget}
 * (product02.md §18) — the identity marketplace promotions actually target.
 */
public final class ProductOfferingTargetFactory implements CompiledTargetFactory {

    private static final TargetType TYPE = TargetType.of("offering");

    @Override
    public TargetType type() {
        return TYPE;
    }

    @Override
    public CompiledTarget compile(TargetDefinition definition) {
        UUID offeringId = ParameterReader.uuid(definition.parameters(), "offeringId");
        ProductOfferingTarget target = new ProductOfferingTarget(ProductOfferingId.of(offeringId));
        return context -> new SingleTargetSelection(target);
    }
}
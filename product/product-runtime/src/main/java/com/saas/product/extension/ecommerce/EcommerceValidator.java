package com.saas.product.extension.ecommerce;

import com.saas.product.core.ProductAggregate;
import com.saas.product.core.model.ProductType;
import com.saas.product.spi.ProductValidationException;
import com.saas.product.spi.ProductValidator;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

/**
 * E-commerce-specific product validation rules.
 */
@ApplicationScoped
public class EcommerceValidator implements ProductValidator {

    @Override
    public String supports() {
        return EcommerceExtension.CONTEXT;
    }

    @Override
    public void validate(ProductAggregate product) {
        EcommerceExtension ext = product.requireExtension(EcommerceExtension.CONTEXT);
        List<String> violations = new ArrayList<>();

        // Base price must be positive
        if (ext.getBasePrice().isZero()) {
            violations.add("ecommerce: base price must be greater than zero");
        }

        // Compare-at price must be higher than base price if set
        if (ext.getCompareAtPrice() != null
                && !ext.getCompareAtPrice().isGreaterThan(ext.getBasePrice())) {
            violations.add("ecommerce: compare-at price must be greater than base price");
        }

        // Physical products must have a shipping profile
        if (product.getCore().getType() == ProductType.PHYSICAL
                && ext.getShippingProfile() == null) {
            violations.add("ecommerce: physical products must have a shipping profile");
        }

        // Variant must reference a parent
        if (product.getCore().getType() == ProductType.VARIANT
                && (ext.getParentVariantId() == null || ext.getParentVariantId().isBlank())) {
            violations.add("ecommerce: variant product must specify parentVariantId");
        }

        // VARIANT_PARENT must declare option keys
        if (product.getCore().getType() == ProductType.VARIANT_PARENT
                && ext.getVariantOptionKeys().isEmpty()) {
            violations.add("ecommerce: variant parent must declare at least one variantOptionKey");
        }

        if (!violations.isEmpty()) {
            throw new ProductValidationException(violations);
        }
    }
}

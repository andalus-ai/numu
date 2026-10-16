package tech.kayys.syirkah.product.domain.bundle;

import tech.kayys.syirkah.product.domain.product.ProductId;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One component of a commercial bundle, e.g. Coffee x 1.
 */
public record BundleComponent(
        ProductId productId,
        BigDecimal quantity
) {

    public BundleComponent {
        Objects.requireNonNull(
                productId,
                "productId cannot be null"
        );
        Objects.requireNonNull(
                quantity,
                "quantity cannot be null"
        );

        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Bundle quantity must be positive"
            );
        }
    }
}
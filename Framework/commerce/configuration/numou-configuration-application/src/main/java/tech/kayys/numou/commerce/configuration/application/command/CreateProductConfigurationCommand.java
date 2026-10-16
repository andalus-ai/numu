package tech.kayys.numou.commerce.configuration.application.command;

import tech.kayys.numou.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.product.ProductId;
import tech.kayys.numou.product.domain.specification.ProductSpecificationId;

import java.util.Objects;

/** Creates a draft product configuration (product02.md). */
public record CreateProductConfigurationCommand(
        ProductConfigurationId configurationId,
        ProductId productId,
        ProductSpecificationId specificationId
) implements Command {

    public CreateProductConfigurationCommand {
        Objects.requireNonNull(productId, "productId cannot be null");
        Objects.requireNonNull(specificationId, "specificationId cannot be null");
        if (configurationId == null) {
            configurationId = ProductConfigurationId.generate();
        }
    }

    public CreateProductConfigurationCommand(
            ProductId productId,
            ProductSpecificationId specificationId
    ) {
        this(ProductConfigurationId.generate(), productId, specificationId);
    }
}

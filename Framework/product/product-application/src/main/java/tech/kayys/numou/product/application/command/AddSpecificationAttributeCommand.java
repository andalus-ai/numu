package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.specification.AttributeDefinition;
import tech.kayys.syirkah.product.domain.specification.ProductSpecificationId;

import java.util.Objects;

/**
 * Adds one typed attribute definition to a specification
 * (e.g. sugar=ENUM, ram=NUMBER).
 */
public record AddSpecificationAttributeCommand(
        ProductSpecificationId specificationId,
        AttributeDefinition attribute
) implements Command {

    public AddSpecificationAttributeCommand {
        Objects.requireNonNull(
                specificationId,
                "specificationId cannot be null"
        );
        Objects.requireNonNull(
                attribute,
                "attribute cannot be null"
        );
    }
}
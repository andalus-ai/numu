package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.specification.ProductSpecificationId;

import java.util.Objects;

/** Removes an attribute definition from a specification. */
public record RemoveSpecificationAttributeCommand(
        ProductSpecificationId specificationId,
        String attributeCode
) implements Command {

    public RemoveSpecificationAttributeCommand {
        Objects.requireNonNull(specificationId, "specificationId cannot be null");
        Objects.requireNonNull(attributeCode, "attributeCode cannot be null");
        if (attributeCode.isBlank()) {
            throw new IllegalArgumentException("attributeCode cannot be blank");
        }
        attributeCode = attributeCode.trim();
    }
}

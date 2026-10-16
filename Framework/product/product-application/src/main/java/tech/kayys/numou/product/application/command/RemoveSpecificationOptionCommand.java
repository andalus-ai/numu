package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.specification.ProductSpecificationId;

import java.util.Objects;

/** Removes an option from an option group on a specification. */
public record RemoveSpecificationOptionCommand(
        ProductSpecificationId specificationId,
        String groupCode,
        String optionCode
) implements Command {

    public RemoveSpecificationOptionCommand {
        Objects.requireNonNull(specificationId, "specificationId cannot be null");
        Objects.requireNonNull(groupCode, "groupCode cannot be null");
        Objects.requireNonNull(optionCode, "optionCode cannot be null");
        if (groupCode.isBlank()) {
            throw new IllegalArgumentException("groupCode cannot be blank");
        }
        if (optionCode.isBlank()) {
            throw new IllegalArgumentException("optionCode cannot be blank");
        }
        groupCode = groupCode.trim();
        optionCode = optionCode.trim();
    }
}

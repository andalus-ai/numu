package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.specification.OptionDefinition;
import tech.kayys.numou.product.domain.specification.ProductSpecificationId;

import java.util.Objects;

/** Adds an option to an existing option group on a specification. */
public record AddSpecificationOptionCommand(
        ProductSpecificationId specificationId,
        String groupCode,
        OptionDefinition option
) implements Command {

    public AddSpecificationOptionCommand {
        Objects.requireNonNull(specificationId, "specificationId cannot be null");
        Objects.requireNonNull(groupCode, "groupCode cannot be null");
        Objects.requireNonNull(option, "option cannot be null");
        if (groupCode.isBlank()) {
            throw new IllegalArgumentException("groupCode cannot be blank");
        }
        groupCode = groupCode.trim();
    }
}

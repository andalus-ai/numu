package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.specification.OptionGroup;
import tech.kayys.numou.product.domain.specification.ProductSpecificationId;

import java.util.Objects;

/**
 * Adds one option group (e.g. SIZE with SMALL/MEDIUM/LARGE) to a
 * specification.
 */
public record AddSpecificationOptionGroupCommand(
        ProductSpecificationId specificationId,
        OptionGroup optionGroup
) implements Command {

    public AddSpecificationOptionGroupCommand {
        Objects.requireNonNull(
                specificationId,
                "specificationId cannot be null"
        );
        Objects.requireNonNull(
                optionGroup,
                "optionGroup cannot be null"
        );
    }
}
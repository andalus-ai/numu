package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.specification.OptionGroup;
import tech.kayys.syirkah.product.domain.specification.ProductSpecificationId;

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
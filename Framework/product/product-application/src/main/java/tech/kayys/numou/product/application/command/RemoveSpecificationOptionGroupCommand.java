package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.specification.ProductSpecificationId;

import java.util.Objects;

/** Removes an option group from a specification. */
public record RemoveSpecificationOptionGroupCommand(
        ProductSpecificationId specificationId,
        String groupCode
) implements Command {

    public RemoveSpecificationOptionGroupCommand {
        Objects.requireNonNull(specificationId, "specificationId cannot be null");
        Objects.requireNonNull(groupCode, "groupCode cannot be null");
        if (groupCode.isBlank()) {
            throw new IllegalArgumentException("groupCode cannot be blank");
        }
        groupCode = groupCode.trim();
    }
}

package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.specification.ProductSpecificationId;

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

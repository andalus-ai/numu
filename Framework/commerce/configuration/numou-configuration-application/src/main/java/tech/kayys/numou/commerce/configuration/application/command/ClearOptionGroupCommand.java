package tech.kayys.numou.commerce.configuration.application.command;

import tech.kayys.numou.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.specification.OptionGroupId;

import java.util.Objects;

public record ClearOptionGroupCommand(
        ProductConfigurationId configurationId,
        OptionGroupId optionGroupId
) implements Command {

    public ClearOptionGroupCommand {
        Objects.requireNonNull(configurationId, "configurationId cannot be null");
        Objects.requireNonNull(optionGroupId, "optionGroupId cannot be null");
    }
}

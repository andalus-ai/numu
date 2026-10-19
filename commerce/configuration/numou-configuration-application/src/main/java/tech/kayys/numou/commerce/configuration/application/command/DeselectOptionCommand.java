package tech.kayys.numou.commerce.configuration.application.command;

import tech.kayys.numou.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.specification.OptionGroupId;
import tech.kayys.numou.product.domain.specification.OptionId;

import java.util.Objects;

public record DeselectOptionCommand(
        ProductConfigurationId configurationId,
        OptionGroupId optionGroupId,
        OptionId optionId
) implements Command {

    public DeselectOptionCommand {
        Objects.requireNonNull(configurationId, "configurationId cannot be null");
        Objects.requireNonNull(optionGroupId, "optionGroupId cannot be null");
        Objects.requireNonNull(optionId, "optionId cannot be null");
    }
}

package tech.kayys.syirkah.commerce.configuration.application.command;

import tech.kayys.syirkah.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.specification.OptionGroupId;

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

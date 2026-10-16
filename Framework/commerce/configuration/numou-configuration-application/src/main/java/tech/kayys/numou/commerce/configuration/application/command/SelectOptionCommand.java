package tech.kayys.syirkah.commerce.configuration.application.command;

import tech.kayys.syirkah.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.specification.OptionGroupId;
import tech.kayys.syirkah.product.domain.specification.OptionId;

import java.util.Objects;

public record SelectOptionCommand(
        ProductConfigurationId configurationId,
        OptionGroupId optionGroupId,
        OptionId optionId
) implements Command {

    public SelectOptionCommand {
        Objects.requireNonNull(configurationId, "configurationId cannot be null");
        Objects.requireNonNull(optionGroupId, "optionGroupId cannot be null");
        Objects.requireNonNull(optionId, "optionId cannot be null");
    }

    public SelectOptionCommand(
            ProductConfigurationId configurationId,
            String groupCode,
            String optionCode
    ) {
        this(configurationId, OptionGroupId.of(groupCode), OptionId.of(optionCode));
    }
}

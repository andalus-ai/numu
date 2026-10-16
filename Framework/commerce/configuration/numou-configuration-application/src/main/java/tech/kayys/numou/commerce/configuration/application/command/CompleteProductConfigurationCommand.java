package tech.kayys.syirkah.commerce.configuration.application.command;

import tech.kayys.syirkah.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.syirkah.foundation.application.command.Command;

import java.util.Objects;

public record CompleteProductConfigurationCommand(
        ProductConfigurationId configurationId
) implements Command {

    public CompleteProductConfigurationCommand {
        Objects.requireNonNull(configurationId, "configurationId cannot be null");
    }
}

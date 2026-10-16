package tech.kayys.numou.commerce.configuration.application.command;

import tech.kayys.numou.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;

public record CompleteProductConfigurationCommand(
        ProductConfigurationId configurationId
) implements Command {

    public CompleteProductConfigurationCommand {
        Objects.requireNonNull(configurationId, "configurationId cannot be null");
    }
}

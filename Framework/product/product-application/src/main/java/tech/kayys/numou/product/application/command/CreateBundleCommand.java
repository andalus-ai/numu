package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;

/** Creates a draft commercial bundle. */
public record CreateBundleCommand(
        String code,
        String name
) implements Command {

    public CreateBundleCommand {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
    }
}

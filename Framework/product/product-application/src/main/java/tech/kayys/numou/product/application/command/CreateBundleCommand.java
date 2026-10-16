package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;

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

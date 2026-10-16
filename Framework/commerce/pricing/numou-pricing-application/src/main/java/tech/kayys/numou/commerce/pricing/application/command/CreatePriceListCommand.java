package tech.kayys.syirkah.commerce.pricing.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;

import java.util.Objects;

/** Creates a draft price list with a tenant-defined code. */
public record CreatePriceListCommand(
        String code,
        String name
) implements Command {

    public CreatePriceListCommand {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        code = code.trim();
        name = name.trim();
        if (code.isBlank()) {
            throw new IllegalArgumentException("code cannot be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
    }
}

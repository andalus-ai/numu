package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.classification.ClassificationSchemeType;

import java.util.Objects;

/** Creates a draft classification scheme. */
public record CreateClassificationSchemeCommand(
        String code,
        String name,
        ClassificationSchemeType type
) implements Command {

    public CreateClassificationSchemeCommand {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
    }
}

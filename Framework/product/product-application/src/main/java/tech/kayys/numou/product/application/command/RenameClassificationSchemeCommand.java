package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.classification.ClassificationSchemeId;

import java.util.Objects;

/** Renames a non-archived classification scheme. */
public record RenameClassificationSchemeCommand(
        ClassificationSchemeId schemeId,
        String name
) implements Command {

    public RenameClassificationSchemeCommand {
        Objects.requireNonNull(schemeId, "schemeId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
    }
}

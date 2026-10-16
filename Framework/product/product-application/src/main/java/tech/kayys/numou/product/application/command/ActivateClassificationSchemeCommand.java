package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;

import java.util.Objects;

/** Activates a draft classification scheme. */
public record ActivateClassificationSchemeCommand(
        ClassificationSchemeId schemeId
) implements Command {

    public ActivateClassificationSchemeCommand {
        Objects.requireNonNull(schemeId, "schemeId cannot be null");
    }
}

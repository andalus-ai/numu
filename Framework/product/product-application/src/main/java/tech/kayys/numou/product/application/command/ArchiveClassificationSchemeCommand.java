package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;

import java.util.Objects;

/** Archives an active classification scheme. */
public record ArchiveClassificationSchemeCommand(
        ClassificationSchemeId schemeId
) implements Command {

    public ArchiveClassificationSchemeCommand {
        Objects.requireNonNull(schemeId, "schemeId cannot be null");
    }
}

package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.bundle.BundleId;

import java.util.Objects;

/** Archives a discontinued bundle (terminal state). */
public record ArchiveBundleCommand(
        BundleId bundleId
) implements Command {

    public ArchiveBundleCommand {
        Objects.requireNonNull(bundleId, "bundleId cannot be null");
    }
}

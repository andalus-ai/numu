package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.bundle.BundleId;

import java.util.Objects;

/** Archives a discontinued bundle (terminal state). */
public record ArchiveBundleCommand(
        BundleId bundleId
) implements Command {

    public ArchiveBundleCommand {
        Objects.requireNonNull(bundleId, "bundleId cannot be null");
    }
}

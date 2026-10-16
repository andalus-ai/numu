package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.bundle.BundleId;

import java.util.Objects;

/** Discontinues an active bundle. */
public record DiscontinueBundleCommand(
        BundleId bundleId
) implements Command {

    public DiscontinueBundleCommand {
        Objects.requireNonNull(bundleId, "bundleId cannot be null");
    }
}

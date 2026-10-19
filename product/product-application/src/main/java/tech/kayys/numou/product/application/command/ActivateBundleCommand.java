package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.bundle.BundleId;

import java.util.Objects;

/** Activates a draft bundle that has at least one component. */
public record ActivateBundleCommand(
        BundleId bundleId
) implements Command {

    public ActivateBundleCommand {
        Objects.requireNonNull(bundleId, "bundleId cannot be null");
    }
}

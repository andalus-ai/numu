package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.bundle.BundleId;

import java.util.Objects;

/** Activates a draft bundle that has at least one component. */
public record ActivateBundleCommand(
        BundleId bundleId
) implements Command {

    public ActivateBundleCommand {
        Objects.requireNonNull(bundleId, "bundleId cannot be null");
    }
}

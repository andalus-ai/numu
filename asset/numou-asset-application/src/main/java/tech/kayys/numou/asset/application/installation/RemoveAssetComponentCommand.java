package tech.kayys.numou.asset.application.installation;

import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;
import java.util.UUID;

/** Remove an installed component from its parent asset (see ASSET-18). */
public record RemoveAssetComponentCommand(
        String tenantId,
        UUID componentAssetId,
        UUID parentAssetId,
        String removedBy
) implements Command {

    public RemoveAssetComponentCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(componentAssetId, "componentAssetId cannot be null");
        Objects.requireNonNull(parentAssetId, "parentAssetId cannot be null");
    }
}

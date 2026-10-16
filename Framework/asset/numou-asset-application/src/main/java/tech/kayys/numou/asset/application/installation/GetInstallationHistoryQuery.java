package tech.kayys.numou.asset.application.installation;

import java.util.Objects;
import java.util.UUID;

/** Query the installation history of an asset (either side), ordered by installedAt (see ASSET-18). */
public record GetInstallationHistoryQuery(String tenantId, UUID assetId) {

    public GetInstallationHistoryQuery {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
    }
}

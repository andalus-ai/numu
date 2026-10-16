package tech.kayys.numou.asset.application.document;

import java.time.Duration;
import java.util.Objects;

/** Find references whose {@code validUntil} falls within the window (ASSET-24 §15). */
public record GetExpiringAssetDocumentsQuery(String tenantId, Duration window) {

    public GetExpiringAssetDocumentsQuery {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(window, "window cannot be null");
    }
}

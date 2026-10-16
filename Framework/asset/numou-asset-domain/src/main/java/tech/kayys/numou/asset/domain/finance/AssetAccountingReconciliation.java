package tech.kayys.syirkah.asset.domain.finance;

import tech.kayys.syirkah.asset.domain.identifier.AssetId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Reconciliation entry comparing the operational side with the Accounting side
 * (ASSET-25 §34). Recorded from trusted application inputs / Accounting
 * events — never by querying Accounting synchronously.
 */
public record AssetAccountingReconciliation(
        UUID id,
        String tenantId,
        AssetId assetId,
        UUID accountingAssetId,
        ReconciliationStatus status,
        String detail,
        Instant reconciledAt,
        String reconciledBy
) {

    public AssetAccountingReconciliation {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(reconciledAt, "reconciledAt cannot be null");
    }

    public enum ReconciliationStatus {
        MATCHED,
        MISSING_LINK,
        ORPHAN_ACCOUNTING_ASSET,
        LIFECYCLE_MISMATCH
    }
}

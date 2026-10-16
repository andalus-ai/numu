package tech.kayys.numou.asset.domain.warranty;

/**
 * Warranty lifecycle (ASSET-23 §7): EXPIRED = time/meter ended, EXHAUSTED = limit consumed.
 */
public enum WarrantyStatus {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    EXPIRED,
    CANCELLED,
    EXHAUSTED
}

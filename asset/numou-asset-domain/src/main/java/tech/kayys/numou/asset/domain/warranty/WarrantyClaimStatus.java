package tech.kayys.numou.asset.domain.warranty;

/** Claim lifecycle (ASSET-23 §14-15). Independent from the maintenance WO lifecycle. */
public enum WarrantyClaimStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    IN_SERVICE,
    COMPLETED,
    CANCELLED
}

package tech.kayys.syirkah.asset.domain.inspection;

/** Lifecycle status of an asset inspection (see ASSET-20 section 20.4). */
public enum AssetInspectionStatus {
    DRAFT,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

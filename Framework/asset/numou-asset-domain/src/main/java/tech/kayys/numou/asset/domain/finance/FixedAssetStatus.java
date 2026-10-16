package tech.kayys.syirkah.asset.domain.finance;

/** Asset-side read-model copy of the Accounting-owned status (ASSET-25 §10). */
public enum FixedAssetStatus {
    DRAFT,
    CAPITALIZED,
    DEPRECIATING,
    FULLY_DEPRECIATED,
    IMPAIRED,
    DISPOSED
}

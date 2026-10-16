package tech.kayys.syirkah.asset.domain.inspection;

/**
 * Observed physical condition of an asset or component (see ASSET-20
 * section 20.7).
 *
 * <p>Availability is deliberately not modelled here: it is an
 * operational/read-model concept, not a physical condition.</p>
 */
public enum AssetCondition {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    CRITICAL
}

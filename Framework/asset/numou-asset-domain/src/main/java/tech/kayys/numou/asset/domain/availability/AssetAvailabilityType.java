package tech.kayys.syirkah.asset.domain.availability;

/**
 * The operational polarity of an availability period (ASSET-26 §6).
 *
 * <p>Only two values are modelled deliberately: an asset is either declared
 * usable for a window, or explicitly not usable. {@code RESERVED}/{@code IN_USE}
 * belong to separate dimensions (reservation, utilization) and must not be
 * flattened into this enum.</p>
 */
public enum AssetAvailabilityType {

    AVAILABLE,

    UNAVAILABLE
}

package tech.kayys.syirkah.asset.domain.availability;

/**
 * Coarse availability projection of an asset at "now" (ASSET-26 §5).
 *
 * <p>This is an operational read-side status, deliberately kept separate from
 * the Asset lifecycle ({@code valueobject.AssetStatus}). It is never stored on
 * the {@code Asset} aggregate.</p>
 */
public enum AssetAvailabilityStatus {

    AVAILABLE,

    UNAVAILABLE,

    RESERVED,

    IN_USE
}

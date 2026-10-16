package tech.kayys.syirkah.asset.domain.availability;

/**
 * Why an availability period exists (ASSET-26 §6).
 *
 * <p>The reason is descriptive business context; it never changes the
 * availability polarity (that is {@link AssetAvailabilityType}).</p>
 */
public enum AssetAvailabilityReason {

    NORMAL_OPERATION,

    MAINTENANCE,

    INSPECTION,

    REPAIR,

    DAMAGE,

    ACCIDENT,

    RESERVED,

    PROJECT,

    TRANSFER,

    OTHER
}

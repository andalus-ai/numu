package tech.kayys.numou.asset.domain.meter;

/**
 * Semantic kind of a reading (ASSET-21 §21.9).
 *
 * <p>Corrections are modelled as new immutable records (type
 * {@code CORRECTED}) rather than in-place updates (§21.10, §21.30).</p>
 */
public enum MeterReadingType {
    NORMAL,
    INITIAL,
    CORRECTED,
    ESTIMATED
}

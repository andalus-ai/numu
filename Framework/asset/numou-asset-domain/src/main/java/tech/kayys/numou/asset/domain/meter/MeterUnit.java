package tech.kayys.numou.asset.domain.meter;

/**
 * Canonical units for meters (ASSET-21 §21.4).
 *
 * <p>A meter owns exactly one canonical unit. Inputs in a compatible unit are
 * converted at the application layer before reaching the domain.</p>
 */
public enum MeterUnit {
    KM,
    MILE,
    HOUR,
    CYCLE,
    LITER,
    KWH,
    OTHER
}

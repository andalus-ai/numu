package tech.kayys.numou.asset.domain.meter;

/**
 * Monotonic behaviour lives on the meter configuration, not on the generic
 * type (ASSET-21 §21.11-21.12), because real businesses vary (e.g. a counter
 * that resets after maintenance or a replaced meter).
 */
public enum MeterBehavior {
    MONOTONIC,
    NON_MONOTONIC
}

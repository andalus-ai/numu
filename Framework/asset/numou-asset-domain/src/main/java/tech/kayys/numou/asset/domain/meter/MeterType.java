package tech.kayys.numou.asset.domain.meter;

/** Meter types (ASSET-21 §21.3). Compatibility with asset types stays configurable, not hard-coded. */
public enum MeterType {
    ODOMETER,
    ENGINE_HOURS,
    OPERATING_HOURS,
    CYCLES,
    DISTANCE,
    FUEL_CONSUMPTION,
    ENERGY_CONSUMPTION,
    OTHER
}

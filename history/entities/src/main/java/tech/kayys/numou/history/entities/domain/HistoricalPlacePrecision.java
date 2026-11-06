package tech.kayys.numou.history.entities.domain;

/** Meaning of a place coordinate assertion; it is not a measurement accuracy guarantee. */
public enum HistoricalPlacePrecision {
    EXACT_FEATURE,
    APPROXIMATE_LOCATION,
    REPRESENTATIVE_CENTROID
}

package tech.kayys.numou.restaurant.venue.domain;

import tech.kayys.numou.restaurant.kernel.domain.StationId;

/** A cooking/prep station (grill, fry, cold, bar, pastry, expo) and how many items it works on at once. */
public record StationRef(StationId id, String name, int parallelism) {

    public StationRef {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Station name is required");
        }
        if (parallelism < 1 || parallelism > 50) {
            throw new IllegalArgumentException("Station parallelism must be 1-50");
        }
    }
}

package tech.kayys.syirkah.restaurant.reservation.domain;

import java.time.LocalTime;
import java.util.Objects;

/** A bookable window within one day, e.g. dinner 17:00-21:30 (last seating, not closing time). */
public record ServicePeriod(String name, LocalTime opens, LocalTime lastSeating) {

    public ServicePeriod {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(opens, "opens cannot be null");
        Objects.requireNonNull(lastSeating, "lastSeating cannot be null");
        if (lastSeating.isBefore(opens)) {
            throw new IllegalArgumentException("lastSeating cannot be before opens");
        }
    }
}

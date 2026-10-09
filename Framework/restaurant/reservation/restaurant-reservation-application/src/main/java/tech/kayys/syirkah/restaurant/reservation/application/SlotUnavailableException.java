package tech.kayys.syirkah.restaurant.reservation.application;

import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;

import java.time.Instant;
import java.util.List;

/** The requested slot is full; carries the nearest bookable alternatives so the UI can offer them (HTTP 409). */
public final class SlotUnavailableException extends BusinessRuleViolation {

    private final transient List<Instant> alternatives;

    public SlotUnavailableException(String message, List<Instant> alternatives) {
        super(message);
        this.alternatives = List.copyOf(alternatives);
    }

    public List<Instant> alternatives() {
        return alternatives;
    }
}

package tech.kayys.numou.reservation.application;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

import java.time.Instant;
import java.util.List;

/**
 * The requested slot is full; carries the nearest bookable alternatives so the UI can offer them (HTTP 409).
 */
public class SlotUnavailableException extends BusinessRuleViolation {

    private final transient List<Instant> alternatives;

    public SlotUnavailableException(int partySize, Instant startsAt) {
        super("No availability for " + partySize + " capacity at " + startsAt);
        this.alternatives = List.of();
    }

    public SlotUnavailableException(String message, List<Instant> alternatives) {
        super(message);
        this.alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
    }

    public List<Instant> alternatives() {
        return alternatives;
    }
}

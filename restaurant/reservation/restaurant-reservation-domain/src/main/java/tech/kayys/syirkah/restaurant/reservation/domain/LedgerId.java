package tech.kayys.syirkah.restaurant.reservation.domain;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Identity of one venue's bookings for one calendar day. Deterministic: the same (venue, day) always maps to
 * the same id, so concurrent first bookings of a day collide on the primary key instead of creating two ledgers.
 */
public record LedgerId(UUID value) implements DomainId<UUID>, Serializable {

    public LedgerId {
        Objects.requireNonNull(value, "LedgerId value must not be null");
    }

    public static LedgerId forVenueDay(VenueId venueId, LocalDate day) {
        return new LedgerId(UUID.nameUUIDFromBytes(("ledger:" + venueId.value() + ":" + day).getBytes(StandardCharsets.UTF_8)));
    }

    public static LedgerId of(UUID value) {
        return new LedgerId(value);
    }
}

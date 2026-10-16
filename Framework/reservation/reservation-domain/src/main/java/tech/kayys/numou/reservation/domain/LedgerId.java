package tech.kayys.syirkah.reservation.domain;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record LedgerId(UUID value) implements DomainId<UUID> {
    public LedgerId {
        Objects.requireNonNull(value, "LedgerId value cannot be null");
    }

    public static LedgerId of(UUID value) {
        return new LedgerId(value);
    }

    public static LedgerId of(String value) {
        return new LedgerId(UUID.fromString(value));
    }

    public static LedgerId forFacilityDate(FacilityId facilityId, LocalDate day) {
        return new LedgerId(UUID.nameUUIDFromBytes((facilityId.value() + "/" + day).getBytes(StandardCharsets.UTF_8)));
    }
}

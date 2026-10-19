package tech.kayys.numou.reservation.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.reservation.domain.BookingRules;
import tech.kayys.numou.reservation.domain.FacilityId;

import java.util.UUID;

public interface BookingRulesPort {
    Uni<BookingRules> rulesFor(UUID tenantId, FacilityId facilityId);
}

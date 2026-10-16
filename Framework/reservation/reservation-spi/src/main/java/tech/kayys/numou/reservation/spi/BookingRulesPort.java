package tech.kayys.syirkah.reservation.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.reservation.domain.BookingRules;
import tech.kayys.syirkah.reservation.domain.FacilityId;

import java.util.UUID;

public interface BookingRulesPort {
    Uni<BookingRules> rulesFor(UUID tenantId, FacilityId facilityId);
}

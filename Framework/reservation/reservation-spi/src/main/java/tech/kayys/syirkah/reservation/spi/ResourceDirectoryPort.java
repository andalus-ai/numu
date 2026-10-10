package tech.kayys.syirkah.reservation.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.reservation.domain.BookableResource;
import tech.kayys.syirkah.reservation.domain.FacilityId;

import java.util.List;
import java.util.UUID;

/**
 * Cross-boundary port supplying available bookable resources for a facility.
 */
public interface ResourceDirectoryPort {
    Uni<List<BookableResource>> resourcesFor(UUID tenantId, FacilityId facilityId);
}

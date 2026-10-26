package tech.kayys.numou.hospitality.spi;

import io.smallrye.mutiny.Uni;
import java.util.Optional;
import java.util.UUID;
import tech.kayys.numou.hospitality.domain.AccommodationUnit;

/** Tenant-scoped persistence port for sellable unit descriptors. */
public interface AccommodationUnitRepository {
    Uni<Optional<AccommodationUnit>> findById(String tenantId, UUID listingId, UUID unitId);
}

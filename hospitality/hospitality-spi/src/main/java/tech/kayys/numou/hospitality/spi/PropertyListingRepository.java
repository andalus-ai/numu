package tech.kayys.numou.hospitality.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.hospitality.domain.ListingStatus;
import tech.kayys.numou.hospitality.domain.PropertyListing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary. Implementations must scope all reads and writes by tenant. */
public interface PropertyListingRepository {
    Uni<PropertyListing> save(String tenantId, PropertyListing listing);
    Uni<Optional<PropertyListing>> findById(String tenantId, UUID listingId);
    Uni<List<PropertyListing>> findByProvider(String tenantId, String providerId);
    Uni<List<PropertyListing>> findPublished(String tenantId);
    Uni<Boolean> existsById(String tenantId, UUID listingId);
}

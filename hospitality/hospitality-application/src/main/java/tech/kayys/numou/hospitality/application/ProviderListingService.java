package tech.kayys.numou.hospitality.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.hospitality.domain.PropertyListing;
import tech.kayys.numou.hospitality.domain.PropertyType;
import tech.kayys.numou.hospitality.spi.PropertyListingRepository;
import tech.kayys.numou.hospitality.spi.ProviderAccessPolicy;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Provider-side listing use cases. Actor and tenant context must come from trusted
 * authentication middleware, not client-supplied request fields.
 */
public final class ProviderListingService {
    private final PropertyListingRepository listings;
    private final ProviderAccessPolicy access;

    public ProviderListingService(PropertyListingRepository listings, ProviderAccessPolicy access) {
        this.listings = Objects.requireNonNull(listings, "listings");
        this.access = Objects.requireNonNull(access, "access");
    }

    public record CreateListing(String tenantId, String actorId, String providerId,
                                PropertyType propertyType, String title) { }

    public record UpdateListing(String tenantId, String actorId, String providerId, UUID listingId,
                                String title, String address, String timeZone,
                                LocalTime checkInFrom, LocalTime checkInUntil, LocalTime checkOutTime) { }

    public record ListingAction(String tenantId, String actorId, String providerId, UUID listingId) { }

    public Uni<PropertyListing> createDraft(CreateListing command) {
        Objects.requireNonNull(command, "command");
        requireContext(command.tenantId(), command.actorId(), command.providerId());
        final var listing = PropertyListing.draft(UUID.randomUUID(), command.tenantId(), command.providerId(),
                Objects.requireNonNull(command.propertyType(), "propertyType"), command.title());
        return access.requireCanManageListings(command.tenantId(), command.actorId(), command.providerId())
                .flatMap(ignored -> listings.save(command.tenantId(), listing));
    }

    public Uni<PropertyListing> updateDetails(UpdateListing command) {
        Objects.requireNonNull(command, "command");
        requireContext(command.tenantId(), command.actorId(), command.providerId());
        return access.requireCanManageListings(command.tenantId(), command.actorId(), command.providerId())
                .flatMap(ignored -> requireOwnedListing(command.tenantId(), command.providerId(), command.listingId()))
                .flatMap(listing -> {
                    listing.updateDetails(command.title(), command.address(), command.timeZone(),
                            command.checkInFrom(), command.checkInUntil(), command.checkOutTime());
                    return listings.save(command.tenantId(), listing);
                });
    }

    public Uni<PropertyListing> publish(ListingAction command) {
        return transition(command, PropertyListing::publish);
    }

    public Uni<PropertyListing> suspend(ListingAction command) {
        return transition(command, PropertyListing::suspend);
    }

    public Uni<PropertyListing> archive(ListingAction command) {
        return transition(command, PropertyListing::archive);
    }

    public Uni<List<PropertyListing>> listProviderListings(String tenantId, String actorId, String providerId) {
        requireContext(tenantId, actorId, providerId);
        return access.requireCanManageListings(tenantId, actorId, providerId)
                .flatMap(ignored -> listings.findByProvider(tenantId, providerId));
    }

    private Uni<PropertyListing> transition(ListingAction command, java.util.function.Consumer<PropertyListing> action) {
        Objects.requireNonNull(command, "command");
        requireContext(command.tenantId(), command.actorId(), command.providerId());
        return access.requireCanManageListings(command.tenantId(), command.actorId(), command.providerId())
                .flatMap(ignored -> requireOwnedListing(command.tenantId(), command.providerId(), command.listingId()))
                .flatMap(listing -> {
                    action.accept(listing);
                    return listings.save(command.tenantId(), listing);
                });
    }

    private Uni<PropertyListing> requireOwnedListing(String tenantId, String providerId, UUID listingId) {
        Objects.requireNonNull(listingId, "listingId");
        return listings.findById(tenantId, listingId).flatMap(found -> {
            if (found.isEmpty() || !providerId.equals(found.get().providerId())) {
                // Do not disclose whether a foreign provider or tenant owns the identifier.
                return Uni.createFrom().failure(new ListingNotFoundException());
            }
            return Uni.createFrom().item(found.get());
        });
    }

    private static void requireContext(String tenantId, String actorId, String providerId) {
        requireText(tenantId, "tenantId");
        requireText(actorId, "actorId");
        requireText(providerId, "providerId");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}

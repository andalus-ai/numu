package tech.kayys.numou.hospitality.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.smallrye.mutiny.Uni;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.hospitality.domain.ListingStatus;
import tech.kayys.numou.hospitality.domain.PropertyListing;
import tech.kayys.numou.hospitality.domain.PropertyType;
import tech.kayys.numou.hospitality.spi.PropertyListingRepository;
import tech.kayys.numou.hospitality.spi.ProviderAccessPolicy;

class ProviderListingServiceTest {
    @Test
    void providerCanCreateConfigureAndPublishListing() {
        InMemoryListings repo = new InMemoryListings();
        RecordingAccess access = new RecordingAccess();
        ProviderListingService service = new ProviderListingService(repo, access);

        PropertyListing draft = service.createDraft(new ProviderListingService.CreateListing(
                "tenant-a", "actor-a", "provider-a", PropertyType.HOMESTAY, "Hill House")).await().indefinitely();

        assertThat(draft.status()).isEqualTo(ListingStatus.DRAFT);
        PropertyListing configured = service.updateDetails(new ProviderListingService.UpdateListing(
                "tenant-a", "actor-a", "provider-a", draft.id(), "Hill House",
                "Bandung, Indonesia", "Asia/Jakarta", LocalTime.of(14, 0),
                LocalTime.of(21, 0), LocalTime.of(11, 0))).await().indefinitely();
        PropertyListing published = service.publish(new ProviderListingService.ListingAction(
                "tenant-a", "actor-a", "provider-a", configured.id())).await().indefinitely();

        assertThat(published.isBookable()).isTrue();
        assertThat(repo.findPublished("tenant-a").await().indefinitely()).containsExactly(published);
        assertThat(access.calls).isEqualTo(3);
    }

    @Test
    void doesNotDiscloseListingsOwnedByAnotherProvider() {
        InMemoryListings repo = new InMemoryListings();
        PropertyListing foreign = PropertyListing.draft(UUID.randomUUID(), "tenant-a", "provider-b",
                PropertyType.HOTEL, "Private listing");
        repo.save("tenant-a", foreign).await().indefinitely();
        ProviderListingService service = new ProviderListingService(repo, new RecordingAccess());

        assertThatThrownBy(() -> service.publish(new ProviderListingService.ListingAction(
                "tenant-a", "actor-a", "provider-a", foreign.id())).await().indefinitely())
                .isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    void rejectsMissingActorContextBeforeCallingAuthorizationPort() {
        RecordingAccess access = new RecordingAccess();
        ProviderListingService service = new ProviderListingService(new InMemoryListings(), access);

        assertThatThrownBy(() -> service.createDraft(new ProviderListingService.CreateListing(
                "tenant-a", " ", "provider-a", PropertyType.HOTEL, "Hotel")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(access.calls).isZero();
    }

    private static final class RecordingAccess implements ProviderAccessPolicy {
        int calls;
        @Override
        public Uni<Void> requireCanManageListings(String tenantId, String actorId, String providerId) {
            calls++;
            return Uni.createFrom().voidItem();
        }
    }

    private static final class InMemoryListings implements PropertyListingRepository {
        private final Map<String, Map<UUID, PropertyListing>> tenants = new HashMap<>();

        @Override
        public Uni<PropertyListing> save(String tenantId, PropertyListing listing) {
            tenants.computeIfAbsent(tenantId, ignored -> new HashMap<>()).put(listing.id(), listing);
            return Uni.createFrom().item(listing);
        }

        @Override
        public Uni<Optional<PropertyListing>> findById(String tenantId, UUID listingId) {
            return Uni.createFrom().item(Optional.ofNullable(
                    tenants.getOrDefault(tenantId, Map.of()).get(listingId)));
        }

        @Override
        public Uni<List<PropertyListing>> findByProvider(String tenantId, String providerId) {
            List<PropertyListing> result = new ArrayList<>(tenants.getOrDefault(tenantId, Map.of()).values()
                    .stream().filter(item -> item.providerId().equals(providerId)).toList());
            return Uni.createFrom().item(result);
        }

        @Override
        public Uni<List<PropertyListing>> findPublished(String tenantId) {
            return Uni.createFrom().item(tenants.getOrDefault(tenantId, Map.of()).values().stream()
                    .filter(PropertyListing::isBookable).toList());
        }

        @Override
        public Uni<Boolean> existsById(String tenantId, UUID listingId) {
            return Uni.createFrom().item(tenants.getOrDefault(tenantId, Map.of()).containsKey(listingId));
        }
    }
}

package tech.kayys.numou.hospitality.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.smallrye.mutiny.Uni;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.hospitality.domain.AccommodationUnit;
import tech.kayys.numou.hospitality.domain.ListingStatus;
import tech.kayys.numou.hospitality.domain.PropertyListing;
import tech.kayys.numou.hospitality.domain.PropertyType;
import tech.kayys.numou.hospitality.domain.StayQuote;
import tech.kayys.numou.hospitality.domain.StaySearch;
import tech.kayys.numou.hospitality.spi.AccommodationUnitRepository;
import tech.kayys.numou.hospitality.spi.PropertyListingRepository;
import tech.kayys.numou.hospitality.spi.StayAvailabilityPort;
import tech.kayys.numou.hospitality.spi.StayQuotePolicy;

class CustomerStayServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");

    @Test
    void quotesPublishedAvailableUnitForExactStay() {
        Fixture fixture = new Fixture();
        CustomerStayService service = fixture.service(true);
        StaySearch search = new StaySearch(fixture.listing.id(), fixture.unit.id(),
                LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-03"), 2);

        StayQuote quote = service.quote("tenant-a", search, "IDR").await().indefinitely();

        assertThat(quote.nights()).isEqualTo(2);
        assertThat(quote.total()).isEqualByComparingTo("1050000");
        assertThat(fixture.availabilityCalls).isEqualTo(1);
    }

    @Test
    void rejectsUnpublishedListingBeforeCheckingAvailability() {
        Fixture fixture = new Fixture();
        fixture.listing.suspend();
        CustomerStayService service = fixture.service(true);

        assertThatThrownBy(() -> service.quote("tenant-a", fixture.search(), "IDR").await().indefinitely())
                .isInstanceOf(ListingNotFoundException.class);
        assertThat(fixture.availabilityCalls).isZero();
    }

    @Test
    void rejectsUnavailableStayWithoutIssuingQuote() {
        Fixture fixture = new Fixture();
        CustomerStayService service = fixture.service(false);

        assertThatThrownBy(() -> service.quote("tenant-a", fixture.search(), "IDR").await().indefinitely())
                .isInstanceOf(StayUnavailableException.class);
        assertThat(fixture.quoteCalls).isZero();
    }

    @Test
    void rejectsQuotePolicyResultForDifferentStay() {
        Fixture fixture = new Fixture();
        CustomerStayService service = fixture.service(true, true);

        assertThatThrownBy(() -> service.quote("tenant-a", fixture.search(), "IDR").await().indefinitely())
                .isInstanceOf(IllegalStateException.class);
    }

    private static final class Fixture {
        final PropertyListing listing;
        final AccommodationUnit unit;
        final Map<UUID, PropertyListing> listingData = new HashMap<>();
        int availabilityCalls;
        int quoteCalls;
        boolean mismatchQuote;

        Fixture() {
            listing = PropertyListing.draft(UUID.randomUUID(), "tenant-a", "provider-a",
                    PropertyType.HOMESTAY, "Hill House");
            listing.updateDetails("Hill House", "Bandung", "Asia/Jakarta",
                    LocalTime.of(14, 0), LocalTime.of(21, 0), LocalTime.of(11, 0));
            listing.publish();
            listingData.put(listing.id(), listing);
            unit = new AccommodationUnit(UUID.randomUUID(), listing.id(), "Entire home", 4, 1);
        }

        StaySearch search() {
            return new StaySearch(listing.id(), unit.id(), LocalDate.parse("2026-11-01"),
                    LocalDate.parse("2026-11-03"), 2);
        }

        CustomerStayService service(boolean available) { return service(available, false); }

        CustomerStayService service(boolean available, boolean mismatchQuote) {
            this.mismatchQuote = mismatchQuote;
            PropertyListingRepository listingRepository = new PropertyListingRepository() {
                @Override public Uni<PropertyListing> save(String tenantId, PropertyListing value) {
                    listingData.put(value.id(), value);
                    return Uni.createFrom().item(value);
                }
                @Override public Uni<Optional<PropertyListing>> findById(String tenantId, UUID id) {
                    return Uni.createFrom().item("tenant-a".equals(tenantId)
                            ? Optional.ofNullable(listingData.get(id)) : Optional.empty());
                }
                @Override public Uni<List<PropertyListing>> findByProvider(String tenantId, String providerId) {
                    return Uni.createFrom().item(List.of());
                }
                @Override public Uni<List<PropertyListing>> findPublished(String tenantId) {
                    return Uni.createFrom().item(listingData.values().stream().filter(PropertyListing::isBookable).toList());
                }
                @Override public Uni<Boolean> existsById(String tenantId, UUID id) {
                    return Uni.createFrom().item(listingData.containsKey(id));
                }
            };
            AccommodationUnitRepository unitRepository = (tenantId, listingId, unitId) ->
                    Uni.createFrom().item("tenant-a".equals(tenantId) && listing.id().equals(listingId)
                            && unit.id().equals(unitId) ? Optional.of(unit) : Optional.empty());
            StayAvailabilityPort availabilityPort = (tenantId, listingId, unitId, checkIn, checkOut, guests) -> {
                availabilityCalls++;
                return Uni.createFrom().item(available);
            };
            StayQuotePolicy quotePolicy = (tenantId, request, currency, quoteId) -> {
                quoteCalls++;
                LocalDate checkIn = mismatchQuote ? request.checkIn().plusDays(1) : request.checkIn();
                long nights = java.time.temporal.ChronoUnit.DAYS.between(checkIn, request.checkOut());
                List<BigDecimal> rates = java.util.stream.LongStream.range(0, nights)
                        .mapToObj(i -> new BigDecimal("500000")).toList();
                BigDecimal total = rates.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .add(new BigDecimal("25000")).add(new BigDecimal("25000"));
                return Uni.createFrom().item(new StayQuote(quoteId, listing.id(), unit.id(), checkIn,
                        request.checkOut(), request.guestCount(), currency, rates, new BigDecimal("25000"),
                        new BigDecimal("25000"), total, "cancellation-v1", NOW, NOW.plusSeconds(900)));
            };
            Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
            return new CustomerStayService(listingRepository, unitRepository, availabilityPort, quotePolicy, clock);
        }
    }
}

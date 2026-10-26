package tech.kayys.numou.hospitality.application;

import io.smallrye.mutiny.Uni;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.kayys.numou.hospitality.domain.AccommodationUnit;
import tech.kayys.numou.hospitality.domain.PropertyListing;
import tech.kayys.numou.hospitality.domain.StayQuote;
import tech.kayys.numou.hospitality.domain.StaySearch;
import tech.kayys.numou.hospitality.spi.AccommodationUnitRepository;
import tech.kayys.numou.hospitality.spi.PropertyListingRepository;
import tech.kayys.numou.hospitality.spi.StayAvailabilityPort;
import tech.kayys.numou.hospitality.spi.StayQuotePolicy;

/** Customer-side availability and quote orchestration. This does not create an inventory hold. */
public final class CustomerStayService {
    private final PropertyListingRepository listings;
    private final AccommodationUnitRepository units;
    private final StayAvailabilityPort availability;
    private final StayQuotePolicy quotePolicy;
    private final Clock clock;

    public CustomerStayService(PropertyListingRepository listings, AccommodationUnitRepository units,
                               StayAvailabilityPort availability, StayQuotePolicy quotePolicy, Clock clock) {
        this.listings = Objects.requireNonNull(listings, "listings");
        this.units = Objects.requireNonNull(units, "units");
        this.availability = Objects.requireNonNull(availability, "availability");
        this.quotePolicy = Objects.requireNonNull(quotePolicy, "quotePolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Uni<StayQuote> quote(String tenantId, StaySearch search, String currency) {
        requireText(tenantId, "tenantId");
        Objects.requireNonNull(search, "search");
        requireText(currency, "currency");
        return listings.findById(tenantId, search.listingId()).flatMap(foundListing -> {
            if (foundListing.isEmpty() || !foundListing.get().isBookable()) {
                return Uni.createFrom().failure(new ListingNotFoundException());
            }
            PropertyListing listing = foundListing.get();
            return units.findById(tenantId, listing.id(), search.unitId()).flatMap(foundUnit -> {
                if (foundUnit.isEmpty()) {
                    return Uni.createFrom().failure(new ListingNotFoundException());
                }
                AccommodationUnit unit = foundUnit.get();
                if (!unit.listingId().equals(listing.id()) || !unit.supportsGuests(search.guestCount())) {
                    return Uni.createFrom().failure(new StayUnavailableException());
                }
                return availability.isAvailable(tenantId, listing.id(), unit.id(),
                        search.checkIn(), search.checkOut(), search.guestCount()).flatMap(available -> {
                    if (!Boolean.TRUE.equals(available)) {
                        return Uni.createFrom().failure(new StayUnavailableException());
                    }
                    UUID quoteId = UUID.randomUUID();
                    return quotePolicy.quote(tenantId, search, currency, quoteId).map(quote -> {
                        if (!quote.id().equals(quoteId)
                                || !quote.listingId().equals(listing.id())
                                || !quote.unitId().equals(unit.id())
                                || !quote.checkIn().equals(search.checkIn())
                                || !quote.checkOut().equals(search.checkOut())
                                || quote.guestCount() != search.guestCount()) {
                            throw new IllegalStateException("Quote policy returned a quote for a different stay");
                        }
                        Instant now = clock.instant();
                        if (quote.isExpiredAt(now)) {
                            throw new IllegalStateException("Quote policy returned an already-expired quote");
                        }
                        return quote;
                    });
                });
            });
        });
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}

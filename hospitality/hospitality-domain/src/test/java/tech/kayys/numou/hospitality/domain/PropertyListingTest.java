package tech.kayys.numou.hospitality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PropertyListingTest {
    @Test
    void draftMustHaveCompleteValidDetailsBeforePublishing() {
        PropertyListing listing = PropertyListing.draft(UUID.randomUUID(), "tenant-a", "provider-a",
                PropertyType.HOMESTAY, "Riverside Homestay");
        assertThat(listing.isBookable()).isFalse();
        assertThatThrownBy(listing::publish).isInstanceOf(IllegalStateException.class);

        listing.updateDetails("Riverside Homestay", "Jl. Example 1, Bandung", "Asia/Jakarta",
                LocalTime.of(14, 0), LocalTime.of(21, 0), LocalTime.of(11, 0));
        listing.publish();
        assertThat(listing.status()).isEqualTo(ListingStatus.PUBLISHED);
        assertThat(listing.isBookable()).isTrue();
    }

    @Test
    void publishedListingMustBeSuspendedBeforeEditing() {
        PropertyListing listing = PropertyListing.draft(UUID.randomUUID(), "tenant-a", "provider-a",
                PropertyType.HOTEL, "Hotel");
        listing.updateDetails("Hotel", "Address", "Asia/Jakarta", LocalTime.of(14, 0),
                LocalTime.of(20, 0), LocalTime.of(11, 0));
        listing.publish();

        assertThatThrownBy(() -> listing.updateDetails("Changed", "Address", "Asia/Jakarta",
                LocalTime.of(14, 0), LocalTime.of(20, 0), LocalTime.of(11, 0)))
                .isInstanceOf(IllegalStateException.class);
        listing.suspend();
        listing.updateDetails("Changed", "Address", "Asia/Jakarta", LocalTime.of(14, 0),
                LocalTime.of(20, 0), LocalTime.of(11, 0));
        listing.publish();
        assertThat(listing.title()).isEqualTo("Changed");
    }

    @Test
    void rejectsInvalidTimeZoneAndCheckInWindow() {
        PropertyListing listing = PropertyListing.draft(UUID.randomUUID(), "tenant-a", "provider-a",
                PropertyType.VILLA, "Villa");
        assertThatThrownBy(() -> listing.updateDetails("Villa", "Address", "Not/AZone",
                LocalTime.of(14, 0), LocalTime.of(20, 0), LocalTime.of(11, 0)))
                .isInstanceOf(java.time.DateTimeException.class);
        assertThatThrownBy(() -> listing.updateDetails("Villa", "Address", "Asia/Jakarta",
                LocalTime.of(20, 0), LocalTime.of(14, 0), LocalTime.of(11, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

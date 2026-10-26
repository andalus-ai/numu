package tech.kayys.numou.hospitality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccommodationUnitTest {
    @Test
    void reportsWhetherUnitCanAccommodateRequestedGuests() {
        AccommodationUnit unit = new AccommodationUnit(UUID.randomUUID(), UUID.randomUUID(),
                "Deluxe Room", 3, 5);

        assertThat(unit.supportsGuests(1)).isTrue();
        assertThat(unit.supportsGuests(3)).isTrue();
        assertThat(unit.supportsGuests(4)).isFalse();
        assertThat(unit.supportsGuests(0)).isFalse();
    }

    @Test
    void rejectsInvalidCapacityAndInventory() {
        assertThatThrownBy(() -> new AccommodationUnit(UUID.randomUUID(), UUID.randomUUID(),
                "Room", 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccommodationUnit(UUID.randomUUID(), UUID.randomUUID(),
                "Room", 2, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}

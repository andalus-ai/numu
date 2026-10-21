package tech.kayys.syirkah.restaurant.kernel;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.restaurant.kernel.domain.GuestContact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KernelTest {

    @Test
    void guestContactToStringNeverLeaksPersonalData() {
        final var g = new GuestContact("Budi Santoso", "+62 812 3456 7890", "budi@example.com");
        assertThat(g.toString()).doesNotContain("Budi", "Santoso", "3456", "7890 ", "budi@");
        assertThat(g.toString()).contains("***90", "@example.com");
    }

    @Test
    void guestContactNeedsAWayToReachTheGuestAndValidInput() {
        assertThatThrownBy(() -> new GuestContact("A", null, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuestContact("A", "abc", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuestContact("A", null, "not-an-email")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new GuestContact("A", null, "a@b.co").email()).isEqualTo("a@b.co");
    }
}

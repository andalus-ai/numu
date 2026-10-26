package tech.kayys.numou.reservation.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.reservation.domain.BookingSource;
import tech.kayys.numou.reservation.domain.CustomerContact;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationRequestFingerprintTest {
    private static final CustomerContact CUSTOMER =
            new CustomerContact("Customer", "+628123456789", "customer@example.test");

    private ReservationRequestFingerprint.CreateReservationCommand command(
            Set<String> requirements, Set<String> tags, String notes) {
        return new ReservationRequestFingerprint.CreateReservationCommand(
                "tenant-1", "facility-1", CUSTOMER, 2,
                Instant.parse("2026-10-27T03:00:00Z"), Duration.ofMinutes(45),
                BookingSource.WEB, notes, requirements, tags);
    }

    @Test
    void fingerprintIsStableWhenSetIterationOrderChanges() {
        var first = command(Set.of("quiet", "projector"), Set.of("lift", "bay"), "Routine service");
        var second = command(Set.of("projector", "quiet"), Set.of("bay", "lift"), "Routine service");

        assertThat(ReservationRequestFingerprint.of(first))
                .isEqualTo(ReservationRequestFingerprint.of(second));
    }

    @Test
    void fingerprintChangesWhenBookingSemanticsChange() {
        var original = command(Set.of("quiet"), Set.of("lift"), "Routine service");
        var differentNotes = command(Set.of("quiet"), Set.of("lift"), "Urgent repair");
        var differentDuration = new ReservationRequestFingerprint.CreateReservationCommand(
                "tenant-1", "facility-1", CUSTOMER, 2,
                Instant.parse("2026-10-27T03:00:00Z"), Duration.ofMinutes(60),
                BookingSource.WEB, "Routine service", Set.of("quiet"), Set.of("lift"));

        assertThat(ReservationRequestFingerprint.of(original))
                .isNotEqualTo(ReservationRequestFingerprint.of(differentNotes));
        assertThat(ReservationRequestFingerprint.of(original))
                .isNotEqualTo(ReservationRequestFingerprint.of(differentDuration));
    }

    @Test
    void fingerprintIsFixedLengthSha256HexAndDoesNotExposeContactDetails() {
        var fingerprint = ReservationRequestFingerprint.of(
                command(Set.of("quiet"), Set.of("lift"), "Routine service"));

        assertThat(fingerprint).matches("[0-9a-f]{64}");
        assertThat(fingerprint).doesNotContain("customer", "628123456789", "example.test");
    }
}

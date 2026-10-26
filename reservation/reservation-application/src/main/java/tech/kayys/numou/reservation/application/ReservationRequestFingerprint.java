package tech.kayys.numou.reservation.application;

import tech.kayys.numou.reservation.domain.BookingSource;
import tech.kayys.numou.reservation.domain.CustomerContact;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Produces a deterministic, privacy-preserving fingerprint for a reservation command.
 *
 * <p>Only the SHA-256 digest should be persisted with an idempotency key; the canonical
 * representation may contain customer contact details and must not be logged.</p>
 */
public final class ReservationRequestFingerprint {
    private ReservationRequestFingerprint() { }

    public static String of(CreateReservationCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        return digest(canonical(command.tenantId(), command.facilityId(), command.customer(),
                command.partySize(), command.startsAt(), command.duration(), command.source(),
                command.notes(), command.requirements(), command.requiredResourceTags()));
    }

    /** Transport-neutral input shared by generic and domain-specific booking entry points. */
    public record CreateReservationCommand(
            String tenantId,
            String facilityId,
            CustomerContact customer,
            int partySize,
            Instant startsAt,
            Duration duration,
            BookingSource source,
            String notes,
            Set<String> requirements,
            Set<String> requiredResourceTags
    ) { }

    static String canonical(String tenantId, String facilityId, CustomerContact customer,
                            int partySize, Instant startsAt, Duration duration, BookingSource source,
                            String notes, Set<String> requirements, Set<String> requiredResourceTags) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(facilityId, "facilityId cannot be null");
        Objects.requireNonNull(customer, "customer cannot be null");
        Objects.requireNonNull(startsAt, "startsAt cannot be null");
        Objects.requireNonNull(duration, "duration cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
        if (partySize < 1) throw new IllegalArgumentException("partySize must be positive");
        if (duration.isNegative() || duration.isZero()) throw new IllegalArgumentException("duration must be positive");

        return field("tenant", tenantId)
                + field("facility", facilityId)
                + field("customer.name", customer.name())
                + field("customer.phone", customer.phone())
                + field("customer.email", customer.email())
                + field("partySize", Integer.toString(partySize))
                + field("startsAt", startsAt.toString())
                + field("durationSeconds", Long.toString(duration.getSeconds()))
                + field("durationNanos", Integer.toString(duration.getNano()))
                + field("source", source.name())
                + field("notes", notes == null ? "" : notes)
                + field("requirements", sorted(requirements))
                + field("resourceTags", sorted(requiredResourceTags));
    }

    private static String field(String name, String value) {
        String safe = value == null ? "" : value;
        return name.length() + ":" + name + safe.length() + ":" + safe;
    }

    private static String sorted(Set<String> values) {
        if (values == null || values.isEmpty()) return "0:";
        var builder = new StringBuilder();
        var sorted = new TreeSet<>(values);
        builder.append(sorted.size()).append(':');
        for (String value : sorted) {
            String safe = value == null ? "" : value;
            builder.append(safe.length()).append(':').append(safe);
        }
        return builder.toString();
    }

    private static String digest(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }
}

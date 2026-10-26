package tech.kayys.numou.hospitality.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Hospitality booking snapshot. Inventory authority remains with the atomic hold adapter;
 * this aggregate models the booking lifecycle and never claims a hold was acquired by itself.
 */
public final class StayBooking {
    private final UUID id;
    private final String tenantId;
    private final String customerId;
    private final UUID listingId;
    private final UUID unitId;
    private final LocalDate checkIn;
    private final LocalDate checkOut;
    private final int guestCount;
    private final UUID quoteId;
    private final String currency;
    private final String idempotencyKey;
    private final Instant createdAt;
    private final Instant holdExpiresAt;
    private StayBookingStatus status;

    private StayBooking(UUID id, String tenantId, String customerId, UUID listingId, UUID unitId,
                        LocalDate checkIn, LocalDate checkOut, int guestCount, UUID quoteId, String currency,
                        String idempotencyKey, Instant createdAt, Instant holdExpiresAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = requireText(tenantId, "tenantId");
        this.customerId = requireText(customerId, "customerId");
        this.listingId = Objects.requireNonNull(listingId, "listingId");
        this.unitId = Objects.requireNonNull(unitId, "unitId");
        this.checkIn = Objects.requireNonNull(checkIn, "checkIn");
        this.checkOut = Objects.requireNonNull(checkOut, "checkOut");
        if (!checkOut.isAfter(checkIn)) throw new IllegalArgumentException("checkOut must be after checkIn");
        if (guestCount < 1) throw new IllegalArgumentException("guestCount must be positive");
        this.guestCount = guestCount;
        this.quoteId = Objects.requireNonNull(quoteId, "quoteId");
        this.currency = requireText(currency, "currency");
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        if (this.idempotencyKey.length() > 200) throw new IllegalArgumentException("idempotencyKey must not exceed 200 characters");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.holdExpiresAt = Objects.requireNonNull(holdExpiresAt, "holdExpiresAt");
        if (!holdExpiresAt.isAfter(createdAt)) throw new IllegalArgumentException("holdExpiresAt must be after createdAt");
        this.status = StayBookingStatus.HELD;
    }

    public static StayBooking held(UUID id, String tenantId, String customerId, StayQuote quote,
                                   String idempotencyKey, Instant now, Instant holdExpiresAt) {
        Objects.requireNonNull(quote, "quote");
        if (quote.isExpiredAt(now)) throw new IllegalArgumentException("Cannot create booking from expired quote");
        return new StayBooking(id, tenantId, customerId, quote.listingId(), quote.unitId(), quote.checkIn(),
                quote.checkOut(), quote.guestCount(), quote.id(), quote.currency(), idempotencyKey, now, holdExpiresAt);
    }

    public void confirm(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status != StayBookingStatus.HELD) throw new IllegalStateException("Only held bookings can be confirmed");
        if (!now.isBefore(holdExpiresAt)) {
            status = StayBookingStatus.EXPIRED;
            throw new IllegalStateException("Booking hold has expired");
        }
        status = StayBookingStatus.CONFIRMED;
    }

    public void cancel() {
        if (status == StayBookingStatus.CANCELLED) return;
        if (status != StayBookingStatus.HELD && status != StayBookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only held or confirmed bookings can be cancelled");
        }
        status = StayBookingStatus.CANCELLED;
    }

    public void expire(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status == StayBookingStatus.HELD && !now.isBefore(holdExpiresAt)) status = StayBookingStatus.EXPIRED;
    }

    public UUID id() { return id; }
    public String tenantId() { return tenantId; }
    public String customerId() { return customerId; }
    public UUID listingId() { return listingId; }
    public UUID unitId() { return unitId; }
    public LocalDate checkIn() { return checkIn; }
    public LocalDate checkOut() { return checkOut; }
    public int guestCount() { return guestCount; }
    public UUID quoteId() { return quoteId; }
    public String currency() { return currency; }
    public String idempotencyKey() { return idempotencyKey; }
    public Instant createdAt() { return createdAt; }
    public Instant holdExpiresAt() { return holdExpiresAt; }
    public StayBookingStatus status() { return status; }

    public boolean matches(StayQuote quote, String customerId) {
        return this.customerId.equals(customerId)
                && listingId.equals(quote.listingId())
                && unitId.equals(quote.unitId())
                && checkIn.equals(quote.checkIn())
                && checkOut.equals(quote.checkOut())
                && guestCount == quote.guestCount()
                && quoteId.equals(quote.id())
                && currency.equals(quote.currency());
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}

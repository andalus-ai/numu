package tech.kayys.numou.events.application;

import tech.kayys.numou.events.spi.TicketBookingStore;
import tech.kayys.numou.events.spi.TicketBookingStore.BookingSnapshot;
import tech.kayys.numou.events.spi.TicketBookingStore.HoldCommand;
import tech.kayys.numou.events.spi.TicketBookingStore.Inventory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Channel-neutral use cases. Authentication/authorization must establish tenantId and actorId
 * before invoking this service; request payloads must never be trusted to choose tenant identity.
 */
public final class TicketBookingService {
    private final TicketBookingStore store;
    private final Clock clock;
    private final Duration holdTtl;

    public TicketBookingService(TicketBookingStore store, Clock clock, Duration holdTtl) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.holdTtl = Objects.requireNonNull(holdTtl, "holdTtl");
        if (holdTtl.isNegative() || holdTtl.isZero() || holdTtl.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException("holdTtl must be greater than zero and no more than 30 minutes");
        }
    }

    public void createInventory(Inventory inventory) {
        store.createInventory(Objects.requireNonNull(inventory, "inventory"));
    }

    public BookingSnapshot hold(HoldRequest request) {
        Objects.requireNonNull(request, "request");
        Instant now = clock.instant();
        String fingerprint = fingerprint(request);
        return store.placeHold(new HoldCommand(
                request.tenantId(), request.actorId(), request.correlationId(), request.idempotencyKey(),
                fingerprint, UUID.randomUUID(), request.eventId(), request.productId(), request.salesChannel(),
                request.quantity(), request.unitPrice(), request.currency(), now, now.plus(holdTtl)));
    }

    /** Called only after the Payment capability reports a verified, matching successful payment. */
    public BookingSnapshot confirm(String tenantId, UUID bookingId, String verifiedPaymentReference) {
        return store.confirm(tenantId, bookingId, verifiedPaymentReference, clock.instant());
    }

    public BookingSnapshot release(String tenantId, UUID bookingId, String reason) {
        return store.release(tenantId, bookingId, reason, clock.instant());
    }

    public int expireDueHolds(int limit) {
        return store.expireDueHolds(clock.instant(), limit);
    }

    private static String fingerprint(HoldRequest r) {
        String canonical = String.join("\n",
                r.tenantId(), r.actorId(), r.eventId(), r.productId(), r.salesChannel(),
                Integer.toString(r.quantity()), r.unitPrice().stripTrailingZeros().toPlainString(),
                r.currency().toUpperCase(java.util.Locale.ROOT));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    public record HoldRequest(String tenantId, String actorId, String correlationId, String idempotencyKey,
                              String eventId, String productId, String salesChannel, int quantity,
                              BigDecimal unitPrice, String currency) {
        public HoldRequest {
            tenantId = required(tenantId, "tenantId");
            actorId = required(actorId, "actorId");
            correlationId = required(correlationId, "correlationId");
            idempotencyKey = required(idempotencyKey, "idempotencyKey");
            eventId = required(eventId, "eventId");
            productId = required(productId, "productId");
            salesChannel = required(salesChannel, "salesChannel");
            if (quantity < 1) throw new IllegalArgumentException("quantity must be positive");
            Objects.requireNonNull(unitPrice, "unitPrice");
            if (unitPrice.signum() < 0 || unitPrice.scale() > 4) throw new IllegalArgumentException("invalid unitPrice");
            currency = required(currency, "currency").toUpperCase(java.util.Locale.ROOT);
            if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be 3 letters");
        }
        private static String required(String value, String field) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
            return value.trim();
        }
    }
}

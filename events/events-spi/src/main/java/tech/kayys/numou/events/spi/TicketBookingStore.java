package tech.kayys.numou.events.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Public persistence port. Implementations must enforce idempotency and capacity atomically across processes. */
public interface TicketBookingStore {
    void createInventory(Inventory inventory);

    BookingSnapshot placeHold(HoldCommand command);

    BookingSnapshot confirm(String tenantId, UUID bookingId, String paymentReference, Instant now);

    BookingSnapshot release(String tenantId, UUID bookingId, String reason, Instant now);

    int expireDueHolds(Instant now, int limit);

    record Inventory(String tenantId, String eventId, String productId, String currency,
                     BigDecimal unitPrice, int capacity, int maxPerOrder) {
        public Inventory {
            tenantId = required(tenantId, "tenantId");
            eventId = required(eventId, "eventId");
            productId = required(productId, "productId");
            currency = required(currency, "currency").toUpperCase(java.util.Locale.ROOT);
            Objects.requireNonNull(unitPrice, "unitPrice");
            if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be ISO-style 3-letter code");
            if (unitPrice.signum() < 0 || unitPrice.scale() > 4) throw new IllegalArgumentException("invalid unitPrice");
            if (capacity < 1 || maxPerOrder < 1 || maxPerOrder > capacity) throw new IllegalArgumentException("invalid capacity limits");
        }
    }

    record HoldCommand(String tenantId, String actorId, String correlationId, String idempotencyKey,
                       String requestFingerprint, UUID bookingId, String eventId, String productId,
                       String salesChannel, int quantity, BigDecimal unitPrice, String currency,
                       Instant createdAt, Instant expiresAt) {
        public HoldCommand {
            tenantId = required(tenantId, "tenantId");
            actorId = required(actorId, "actorId");
            correlationId = required(correlationId, "correlationId");
            idempotencyKey = required(idempotencyKey, "idempotencyKey");
            requestFingerprint = required(requestFingerprint, "requestFingerprint");
            if (!requestFingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("fingerprint must be lowercase SHA-256 hex");
            Objects.requireNonNull(bookingId, "bookingId");
            eventId = required(eventId, "eventId");
            productId = required(productId, "productId");
            salesChannel = required(salesChannel, "salesChannel");
            if (quantity < 1) throw new IllegalArgumentException("quantity must be positive");
            Objects.requireNonNull(unitPrice, "unitPrice");
            if (unitPrice.signum() < 0 || unitPrice.scale() > 4) throw new IllegalArgumentException("invalid unitPrice");
            currency = required(currency, "currency").toUpperCase(java.util.Locale.ROOT);
            if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be 3 letters");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(expiresAt, "expiresAt");
            if (!expiresAt.isAfter(createdAt)) throw new IllegalArgumentException("expiresAt must follow createdAt");
        }
    }

    record BookingSnapshot(UUID bookingId, String tenantId, String eventId, String productId,
                           String actorId, String correlationId, String idempotencyKey,
                           String requestFingerprint, String salesChannel, int quantity,
                           BigDecimal unitPrice, BigDecimal totalAmount, String currency,
                           String status, Instant createdAt, Instant expiresAt,
                           String paymentReference, String releaseReason) { }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}

package tech.kayys.numou.events.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Framework-neutral ticketing rules shared by web, mobile, box-office, kiosk,
 * partner and marketplace adapters. Persistence adapters must serialize writes
 * for a product/ticket; Java synchronization alone is not a distributed lock.
 */
public final class TicketingDomain {
    private TicketingDomain() {
    }

    public enum SalesChannel {
        WEB, MOBILE_APP, BOX_OFFICE, KIOSK, PARTNER_API, MARKETPLACE, ORGANIZER
    }

    public enum CredentialType {
        QR_CODE, BARCODE, RFID, NFC, WALLET_PASS, MANUAL_LOOKUP
    }

    public enum BookingStatus {
        HELD, PAYMENT_PENDING, CONFIRMED, EXPIRED, CANCELLED, PAYMENT_FAILED
    }

    public enum TicketStatus {
        ACTIVE, REVOKED, USED
    }

    public enum AdmissionDecision {
        ADMITTED, ALREADY_ADMITTED, INVALID_CREDENTIAL, REVOKED, WRONG_EVENT, NOT_YET_VALID
    }

    public static final class TicketProduct {
        private final String tenantId;
        private final String eventId;
        private final String productId;
        private final String currency;
        private final BigDecimal unitPrice;
        private final int capacity;
        private final int maxPerOrder;
        private final Instant salesStart;
        private final Instant salesEnd;
        private int held;
        private int sold;

        public TicketProduct(String tenantId, String eventId, String productId, String currency,
                             BigDecimal unitPrice, int capacity, int maxPerOrder,
                             Instant salesStart, Instant salesEnd) {
            this.tenantId = required(tenantId, "tenantId");
            this.eventId = required(eventId, "eventId");
            this.productId = required(productId, "productId");
            this.currency = normalizeCurrency(currency);
            this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
            if (unitPrice.signum() < 0 || unitPrice.scale() > 4) {
                throw new IllegalArgumentException("unitPrice must be non-negative with at most 4 fractional digits");
            }
            if (capacity < 1 || maxPerOrder < 1 || maxPerOrder > capacity) {
                throw new IllegalArgumentException("capacity and maxPerOrder must be positive and consistent");
            }
            this.capacity = capacity;
            this.maxPerOrder = maxPerOrder;
            this.salesStart = Objects.requireNonNull(salesStart, "salesStart");
            this.salesEnd = Objects.requireNonNull(salesEnd, "salesEnd");
            if (!salesEnd.isAfter(salesStart)) {
                throw new IllegalArgumentException("salesEnd must be after salesStart");
            }
        }

        public synchronized void hold(int quantity, Instant now) {
            Objects.requireNonNull(now, "now");
            if (now.isBefore(salesStart) || !now.isBefore(salesEnd)) {
                throw new IllegalStateException("ticket sales are not open");
            }
            if (quantity < 1 || quantity > maxPerOrder) {
                throw new IllegalArgumentException("quantity is outside the per-order limit");
            }
            if (capacity - sold - held < quantity) {
                throw new IllegalStateException("insufficient ticket inventory");
            }
            held += quantity;
        }

        public synchronized void releaseHold(int quantity) {
            requireHeldQuantity(quantity);
            held -= quantity;
        }

        public synchronized void confirmHold(int quantity) {
            requireHeldQuantity(quantity);
            held -= quantity;
            sold += quantity;
        }

        private void requireHeldQuantity(int quantity) {
            if (quantity < 1 || quantity > held) {
                throw new IllegalStateException("cannot release or confirm more inventory than is held");
            }
        }

        public String tenantId() { return tenantId; }
        public String eventId() { return eventId; }
        public String productId() { return productId; }
        public String currency() { return currency; }
        public BigDecimal unitPrice() { return unitPrice; }
        public int capacity() { return capacity; }
        public synchronized int held() { return held; }
        public synchronized int sold() { return sold; }
        public synchronized int available() { return capacity - held - sold; }
    }

    /**
     * Commercial booking snapshot. Payment is confirmed only by a trusted
     * payment-capability result, never by a browser redirect or client claim.
     */
    public static final class Booking {
        private final String tenantId;
        private final String bookingId;
        private final String eventId;
        private final String productId;
        private final SalesChannel channel;
        private final String idempotencyKey;
        private final int quantity;
        private final BigDecimal totalAmount;
        private final String currency;
        private final Instant holdExpiresAt;
        private BookingStatus status = BookingStatus.HELD;
        private String paymentReference;

        public Booking(String tenantId, String bookingId, String eventId, String productId,
                       SalesChannel channel, String idempotencyKey, int quantity,
                       BigDecimal totalAmount, String currency, Instant holdExpiresAt) {
            this.tenantId = required(tenantId, "tenantId");
            this.bookingId = required(bookingId, "bookingId");
            this.eventId = required(eventId, "eventId");
            this.productId = required(productId, "productId");
            this.channel = Objects.requireNonNull(channel, "channel");
            this.idempotencyKey = required(idempotencyKey, "idempotencyKey");
            if (quantity < 1) throw new IllegalArgumentException("quantity must be positive");
            this.quantity = quantity;
            this.totalAmount = Objects.requireNonNull(totalAmount, "totalAmount");
            if (totalAmount.signum() < 0 || totalAmount.scale() > 4) {
                throw new IllegalArgumentException("totalAmount must be non-negative with at most 4 fractional digits");
            }
            this.currency = normalizeCurrency(currency);
            this.holdExpiresAt = Objects.requireNonNull(holdExpiresAt, "holdExpiresAt");
        }

        public synchronized void beginPayment(Instant now) {
            expireIfDue(now);
            if (status != BookingStatus.HELD) throw new IllegalStateException("booking is not held");
            status = BookingStatus.PAYMENT_PENDING;
        }

        public synchronized void confirmPayment(String trustedPaymentReference, Instant now) {
            Objects.requireNonNull(now, "now");
            String reference = required(trustedPaymentReference, "trustedPaymentReference");
            expireIfDue(now);
            if (status == BookingStatus.CONFIRMED && reference.equals(paymentReference)) return;
            if (status == BookingStatus.EXPIRED) {
                throw new IllegalStateException("booking hold expired before payment confirmation");
            }
            if (status != BookingStatus.HELD && status != BookingStatus.PAYMENT_PENDING) {
                throw new IllegalStateException("booking cannot be confirmed from " + status);
            }
            if (!now.isBefore(holdExpiresAt)) {
                status = BookingStatus.EXPIRED;
                throw new IllegalStateException("booking hold expired before payment confirmation");
            }
            paymentReference = reference;
            status = BookingStatus.CONFIRMED;
        }

        public synchronized void paymentFailed(Instant now) {
            expireIfDue(now);
            if (status != BookingStatus.PAYMENT_PENDING) {
                throw new IllegalStateException("booking is not awaiting payment");
            }
            status = BookingStatus.PAYMENT_FAILED;
        }

        public synchronized void cancel() {
            if (status != BookingStatus.HELD && status != BookingStatus.CONFIRMED) {
                throw new IllegalStateException("booking cannot be cancelled from " + status);
            }
            status = BookingStatus.CANCELLED;
        }

        public synchronized BookingStatus expireIfDue(Instant now) {
            Objects.requireNonNull(now, "now");
            if ((status == BookingStatus.HELD || status == BookingStatus.PAYMENT_PENDING)
                    && !now.isBefore(holdExpiresAt)) {
                status = BookingStatus.EXPIRED;
            }
            return status;
        }

        public String tenantId() { return tenantId; }
        public String bookingId() { return bookingId; }
        public String eventId() { return eventId; }
        public String productId() { return productId; }
        public SalesChannel channel() { return channel; }
        public String idempotencyKey() { return idempotencyKey; }
        public int quantity() { return quantity; }
        public BigDecimal totalAmount() { return totalAmount; }
        public String currency() { return currency; }
        public Instant holdExpiresAt() { return holdExpiresAt; }
        public synchronized BookingStatus status() { return status; }
        public synchronized String paymentReference() { return paymentReference; }
    }

    /** Stores credential digests only; raw QR/barcode/RFID secrets belong at issuance edges. */
    public record Credential(CredentialType type, String tokenDigest, int version) {
        public Credential {
            Objects.requireNonNull(type, "type");
            tokenDigest = required(tokenDigest, "tokenDigest");
            if (tokenDigest.length() < 32) throw new IllegalArgumentException("tokenDigest is too short");
            if (version < 1) throw new IllegalArgumentException("credential version must be positive");
        }
    }

    /**
     * One admission entitlement may carry multiple credentials (for example QR
     * and RFID wristband) but all credentials resolve to this same single-use
     * ticket state. Scanner adapters must supply a unique, durable scan ID.
     */
    public static final class AdmissionTicket {
        private final String tenantId;
        private final String eventId;
        private final String ticketId;
        private final Instant validFrom;
        private final Instant validUntil;
        private final Map<CredentialType, Credential> credentials = new HashMap<>();
        private final Map<String, AdmissionDecision> processedScans = new HashMap<>();
        private TicketStatus status = TicketStatus.ACTIVE;

        public AdmissionTicket(String tenantId, String eventId, String ticketId,
                               Instant validFrom, Instant validUntil, Set<Credential> credentials) {
            this.tenantId = required(tenantId, "tenantId");
            this.eventId = required(eventId, "eventId");
            this.ticketId = required(ticketId, "ticketId");
            this.validFrom = Objects.requireNonNull(validFrom, "validFrom");
            this.validUntil = Objects.requireNonNull(validUntil, "validUntil");
            if (!validUntil.isAfter(validFrom)) throw new IllegalArgumentException("validUntil must follow validFrom");
            Objects.requireNonNull(credentials, "credentials");
            if (credentials.isEmpty()) throw new IllegalArgumentException("at least one admission credential is required");
            for (Credential credential : credentials) {
                if (this.credentials.putIfAbsent(credential.type(), credential) != null) {
                    throw new IllegalArgumentException("only one active credential per type is allowed");
                }
            }
        }

        public synchronized AdmissionDecision admit(String requestedEventId, CredentialType type,
                                                    String presentedTokenDigest, String scanId, Instant now) {
            String event = required(requestedEventId, "requestedEventId");
            String id = required(scanId, "scanId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(now, "now");
            AdmissionDecision previous = processedScans.get(id);
            if (previous != null) return previous;
            AdmissionDecision decision;
            if (!eventId.equals(event)) {
                decision = AdmissionDecision.WRONG_EVENT;
            } else if (status == TicketStatus.REVOKED) {
                decision = AdmissionDecision.REVOKED;
            } else if (status == TicketStatus.USED) {
                decision = AdmissionDecision.ALREADY_ADMITTED;
            } else if (now.isBefore(validFrom) || !now.isBefore(validUntil)) {
                decision = AdmissionDecision.NOT_YET_VALID;
            } else {
                Credential expected = credentials.get(type);
                if (expected == null || presentedTokenDigest == null
                        || !constantTimeEquals(expected.tokenDigest(), presentedTokenDigest)) {
                    decision = AdmissionDecision.INVALID_CREDENTIAL;
                } else {
                    status = TicketStatus.USED;
                    decision = AdmissionDecision.ADMITTED;
                }
            }
            processedScans.put(id, decision);
            return decision;
        }

        public synchronized void revoke() {
            if (status == TicketStatus.USED) throw new IllegalStateException("an admitted ticket cannot be revoked");
            status = TicketStatus.REVOKED;
        }

        public synchronized TicketStatus status() { return status; }
        public String tenantId() { return tenantId; }
        public String eventId() { return eventId; }
        public String ticketId() { return ticketId; }
        public synchronized int processedScanCount() { return processedScans.size(); }
    }

    private static boolean constantTimeEquals(String left, String right) {
        byte[] a = left.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = right.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }

    private static String normalizeCurrency(String currency) {
        String normalized = required(currency, "currency").toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be a 3-letter code");
        return normalized;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}

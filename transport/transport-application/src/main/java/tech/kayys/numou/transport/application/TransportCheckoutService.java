package tech.kayys.numou.transport.application;

import tech.kayys.numou.transport.domain.HoldNotActiveException;
import tech.kayys.numou.transport.domain.PassengerHold;
import tech.kayys.numou.transport.domain.TransportCheckout;
import tech.kayys.numou.transport.domain.TransportCheckoutStatus;
import tech.kayys.numou.transport.domain.TransportCheckoutRefundStatus;
import tech.kayys.numou.transport.spi.TransportCheckoutConcurrencyException;
import tech.kayys.numou.transport.spi.TransportCheckoutRepository;
import tech.kayys.numou.transport.spi.TransportFareQuotePort;
import tech.kayys.numou.transport.spi.TransportPaymentPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

/**
 * Coordinates a durable, idempotent checkout saga across Transport inventory and Payment.
 * A checkout is not an order or ticket; order creation and fulfillment remain separate contracts.
 */
public final class TransportCheckoutService {
    private static final int MAX_ATTEMPTS = 3;
    private static final String KEY_PREFIX = "transport-checkout:";

    private final DepartureBookingService bookings;
    private final TransportCheckoutRepository checkouts;
    private final TransportFareQuotePort fares;
    private final TransportPaymentPort payments;
    private final Clock clock;
    private final long holdDurationSeconds;

    public TransportCheckoutService(DepartureBookingService bookings,
                                    TransportCheckoutRepository checkouts,
                                    TransportFareQuotePort fares,
                                    TransportPaymentPort payments,
                                    Clock clock,
                                    long holdDurationSeconds) {
        this.bookings = Objects.requireNonNull(bookings, "bookings");
        this.checkouts = Objects.requireNonNull(checkouts, "checkouts");
        this.fares = Objects.requireNonNull(fares, "fares");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (holdDurationSeconds < 1 || holdDurationSeconds > 86_400) {
            throw new IllegalArgumentException("holdDurationSeconds must be between 1 and 86400");
        }
        this.holdDurationSeconds = holdDurationSeconds;
    }

    /** Creates or resumes checkout using a trusted server-side fare quote. */
    public TransportCheckout start(StartCommand command) {
        Objects.requireNonNull(command, "command");
        String fingerprint = fingerprint(command);
        var existing = checkouts.findByIdempotencyKey(command.tenantId(), command.idempotencyKey());
        if (existing.isPresent()) return resume(requireSameRequest(existing.get(), fingerprint));

        Instant now = clock.instant();
        TransportFareQuotePort.Quote quote = fares.quote(new TransportFareQuotePort.QuoteRequest(
                command.tenantId(), command.departureId(), command.fromSequence(),
                command.toSequence(), command.passengerCount()));
        if (!quote.expiresAt().isAfter(now)) throw new IllegalStateException("fare quote has expired");
        Instant holdExpiry = quote.expiresAt().isBefore(now.plusSeconds(holdDurationSeconds))
                ? quote.expiresAt() : now.plusSeconds(holdDurationSeconds);
        if (!holdExpiry.isAfter(now)) throw new IllegalStateException("no valid hold window remains");

        TransportCheckout proposed = new TransportCheckout(command.tenantId(), UUID.randomUUID(),
                command.idempotencyKey(), fingerprint, command.departureId(), quote.quoteId(),
                command.fromSequence(), command.toSequence(), command.passengerCount(),
                quote.amount(), quote.currency(), holdExpiry, UUID.randomUUID(), null, null, null, null,
                TransportCheckoutStatus.REQUESTED, 0, now, now);
        try {
            checkouts.create(proposed);
            return resume(proposed);
        } catch (TransportCheckoutConcurrencyException duplicateOrRace) {
            TransportCheckout winner = checkouts.findByIdempotencyKey(command.tenantId(), command.idempotencyKey())
                    .orElseThrow(() -> duplicateOrRace);
            return resume(requireSameRequest(winner, fingerprint));
        }
    }

    /**
     * Reconciles a bounded batch of old, nonterminal checkouts. Updated snapshots move their
     * updatedAt forward even when Payment is still pending, so the next poll observes backoff
     * instead of hammering the same provider operation on every scheduler tick.
     *
     * @return bounded batch outcome; failed items are delayed for the next retry window when possible
     */
    public ReconciliationResult reconcileDue(Duration minimumAge, int batchSize) {
        Objects.requireNonNull(minimumAge, "minimumAge");
        if (minimumAge.isNegative() || minimumAge.isZero() || minimumAge.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("minimumAge must be between 0 and 24 hours");
        }
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        Instant cutoff = clock.instant().minus(minimumAge);
        List<TransportCheckout> due = checkouts.findStaleNonTerminal(cutoff, batchSize);
        int succeeded = 0;
        int failed = 0;
        List<UUID> failedCheckoutIds = new ArrayList<>();
        for (TransportCheckout checkout : due) {
            try {
                refresh(checkout.tenantId(), checkout.checkoutId());
                succeeded++;
            } catch (RuntimeException failure) {
                failed++;
                failedCheckoutIds.add(checkout.checkoutId());
                // Advance the retry cursor so one broken provider/checkout cannot monopolize
                // every bounded batch. Do not overwrite a concurrent transition.
                try {
                    TransportCheckout latest = checkouts.find(checkout.tenantId(), checkout.checkoutId())
                            .orElse(null);
                    if (latest != null && !isTerminal(latest.status())) {
                        checkouts.save(latest.withStatus(latest.status(), clock.instant()), latest.version());
                    }
                } catch (RuntimeException retryCursorFailure) {
                    failure.addSuppressed(retryCursorFailure);
                }
            }
        }
        return new ReconciliationResult(due.size(), succeeded, failed, failedCheckoutIds);
    }

    /** Reconciles a bounded batch of pending refund operations using their persisted reason and idempotency key. */
    public ReconciliationResult reconcileDueRefunds(Duration minimumAge, int batchSize) {
        Objects.requireNonNull(minimumAge, "minimumAge");
        if (minimumAge.isNegative() || minimumAge.isZero() || minimumAge.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("minimumAge must be between 0 and 24 hours");
        }
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        Instant cutoff = clock.instant().minus(minimumAge);
        List<TransportCheckout> due = checkouts.findStaleRefunds(cutoff, batchSize);
        int succeeded = 0;
        int failed = 0;
        List<UUID> failedCheckoutIds = new ArrayList<>();
        for (TransportCheckout checkout : due) {
            try {
                requestCompensationRefund(checkout.tenantId(), checkout.checkoutId(), checkout.refundReason());
                succeeded++;
            } catch (RuntimeException failure) {
                failed++;
                failedCheckoutIds.add(checkout.checkoutId());
                try {
                    TransportCheckout latest = checkouts.find(checkout.tenantId(), checkout.checkoutId()).orElse(null);
                    if (latest != null && latest.status() == TransportCheckoutStatus.REFUND_PENDING) {
                        checkouts.save(latest.withStatus(latest.status(), clock.instant()), latest.version());
                    }
                } catch (RuntimeException retryCursorFailure) {
                    failure.addSuppressed(retryCursorFailure);
                }
            }
        }
        return new ReconciliationResult(due.size(), succeeded, failed, failedCheckoutIds);
    }

    /** Reads checkout state without contacting Payment or mutating inventory. */
    public TransportCheckout get(String tenantId, UUID checkoutId) {
        return checkouts.find(tenantId, checkoutId)
                .orElseThrow(() -> new IllegalArgumentException("checkout not found"));
    }

    /**
     * Requests or re-reads an idempotent full refund when payment succeeded but the capacity
     * hold could not be confirmed. This is an explicit operator/customer-support action, not an
     * automatic side effect of polling. A failed refund requires a separate operator decision.
     */
    public TransportCheckout requestCompensationRefund(String tenantId, UUID checkoutId, String reason) {
        TransportCheckout checkout = get(tenantId, checkoutId);
        if (checkout.status() == TransportCheckoutStatus.REFUNDED) return checkout;
        if (checkout.status() == TransportCheckoutStatus.REFUND_FAILED) {
            throw new IllegalStateException("refund failed; a new refund attempt requires operator review");
        }
        if (checkout.status() != TransportCheckoutStatus.RECONCILIATION_REQUIRED
                && checkout.status() != TransportCheckoutStatus.REFUND_PENDING) {
            throw new IllegalStateException("checkout is not eligible for compensation refund");
        }
        if (checkout.paymentId() == null) {
            throw new IllegalStateException("checkout has no payment to refund");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank() || normalizedReason.length() > 500) {
            throw new IllegalArgumentException("refund reason must contain 1-500 characters");
        }
        if (checkout.status() == TransportCheckoutStatus.REFUND_PENDING) {
            if (!normalizedReason.equals(checkout.refundReason())) {
                throw new IllegalArgumentException("refund reason must match the original refund request");
            }
            normalizedReason = checkout.refundReason();
        }
        TransportPaymentPort.PaymentSnapshot payment = payments.find(tenantId, checkout.paymentId());
        verifyPayment(checkout, payment, true);
        if (checkout.status() == TransportCheckoutStatus.RECONCILIATION_REQUIRED
                && payment.status() != TransportPaymentPort.PaymentState.SUCCEEDED) {
            throw new IllegalStateException("only a verified successful payment can be refunded for compensation");
        }
        // REFUND_PENDING retries use the persisted reason and same idempotency key to retrieve
        // the existing refund operation, even if Payment has advanced beyond SUCCEEDED.
        TransportPaymentPort.RefundSnapshot refund = payments.requestRefund(
                new TransportPaymentPort.RefundCommand(tenantId, checkout.paymentId(),
                        refundKey(checkout.checkoutId()), checkout.amount(), normalizedReason));
        if (!tenantId.equals(refund.tenantId())
                || !checkout.paymentId().equals(refund.paymentId())
                || checkout.amount().compareTo(refund.amount()) != 0
                || !checkout.currency().equals(refund.currency())) {
            throw new IllegalStateException("refund snapshot does not match the persisted checkout");
        }
        return saveOrReload(checkout, checkout.withRefund(refund.refundId(),
                TransportCheckoutRefundStatus.valueOf(refund.status().name()), normalizedReason, clock.instant()));
    }

    /** Refreshes persisted payment state and applies safe hold settlement transitions. */
    public TransportCheckout refresh(String tenantId, UUID checkoutId) {
        TransportCheckout checkout = checkouts.find(tenantId, checkoutId)
                .orElseThrow(() -> new IllegalArgumentException("checkout not found"));
        if (isTerminal(checkout.status())) return checkout;
        if (checkout.paymentId() == null) return resume(checkout);
        return reconcile(checkout);
    }

    private TransportCheckout resume(TransportCheckout initial) {
        TransportCheckout current = initial;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (isTerminal(current.status())) return current;
            if (current.status() == TransportCheckoutStatus.REQUESTED) {
                if (!current.holdExpiresAt().isAfter(clock.instant())) {
                    return persistStatus(current, TransportCheckoutStatus.CANCELLED);
                }
                PassengerHold hold = bookings.hold(current.holdId(), new DepartureBookingService.HoldCommand(
                        current.tenantId(), current.departureId(), holdKey(current.checkoutId()),
                        current.fromSequence(), current.toSequence(), current.passengerCount(),
                        current.holdExpiresAt()));
                current = saveOrReload(current, current.withHold(hold.id(), clock.instant()));
                if (current.status() == TransportCheckoutStatus.REQUESTED) continue;
            }
            if (current.status() == TransportCheckoutStatus.HOLD_CREATED) {
                TransportPaymentPort.PaymentSnapshot payment = payments.createOrGet(paymentCommand(current));
                verifyPayment(current, payment, false);
                current = saveOrReload(current, current.withPayment(payment.paymentId(), payment.actionUri(),
                        TransportCheckoutStatus.PAYMENT_PENDING, clock.instant()));
            }
            if (current.status() == TransportCheckoutStatus.PAYMENT_PENDING
                    || current.status() == TransportCheckoutStatus.PAYMENT_ACTION_REQUIRED) {
                return reconcile(current);
            }
            return current;
        }
        throw new TransportCheckoutConcurrencyException("checkout changed repeatedly; retry with the same idempotency key");
    }

    private TransportCheckout reconcile(TransportCheckout checkout) {
        TransportPaymentPort.PaymentSnapshot payment = payments.find(checkout.tenantId(), checkout.paymentId());
        verifyPayment(checkout, payment, true);
        TransportCheckoutStatus next;
        switch (payment.status()) {
            case SUCCEEDED -> {
                try {
                    bookings.confirm(checkout.tenantId(), checkout.departureId(), checkout.holdId());
                    next = TransportCheckoutStatus.PAYMENT_SUCCEEDED;
                } catch (HoldNotActiveException cannotConfirm) {
                    // Payment may settle after capacity expires. Never claim a booking succeeded
                    // without confirmed capacity; require refund/manual reconciliation.
                    next = TransportCheckoutStatus.RECONCILIATION_REQUIRED;
                }
            }
            case FAILED, CANCELLED, EXPIRED -> {
                // Infrastructure failures propagate so the pending checkout can be retried;
                // only a confirmed business-state mismatch is terminal reconciliation work.
                bookings.release(checkout.tenantId(), checkout.departureId(), checkout.holdId());
                next = payment.status() == TransportPaymentPort.PaymentState.CANCELLED
                        ? TransportCheckoutStatus.CANCELLED : TransportCheckoutStatus.PAYMENT_FAILED;
            }
            case REQUIRES_CUSTOMER_ACTION -> next = TransportCheckoutStatus.PAYMENT_ACTION_REQUIRED;
            case CREATED, PENDING, UNKNOWN -> next = TransportCheckoutStatus.PAYMENT_PENDING;
            default -> next = TransportCheckoutStatus.RECONCILIATION_REQUIRED;
        }
        // Persist the last successful reconciliation time even when provider status is unchanged.
        // The updatedAt field is the bounded poller's retry/backoff cursor.
        return saveOrReload(checkout, checkout.withPayment(checkout.paymentId(), payment.actionUri(),
                next, clock.instant()));
    }

    private TransportCheckout persistStatus(TransportCheckout current, TransportCheckoutStatus status) {
        return saveOrReload(current, current.withStatus(status, clock.instant()));
    }

    private TransportCheckout saveOrReload(TransportCheckout current, TransportCheckout updated) {
        try {
            checkouts.save(updated, current.version());
            return updated;
        } catch (TransportCheckoutConcurrencyException conflict) {
            return checkouts.find(current.tenantId(), current.checkoutId()).orElseThrow(() -> conflict);
        }
    }

    private static TransportPaymentPort.CreatePayment paymentCommand(TransportCheckout checkout) {
        return new TransportPaymentPort.CreatePayment(checkout.tenantId(), paymentKey(checkout.checkoutId()),
                checkout.checkoutId().toString(), checkout.amount(), checkout.currency(),
                "Transport booking checkout " + checkout.checkoutId(),
                Map.of("transportCheckoutId", checkout.checkoutId().toString(),
                        "departureId", checkout.departureId().toString(),
                        "holdId", checkout.holdId().toString(),
                        "quoteId", checkout.quoteId()));
    }

    private static void verifyPayment(TransportCheckout checkout, TransportPaymentPort.PaymentSnapshot payment,
                                      boolean requirePaymentIdMatch) {
        if (payment == null || !checkout.tenantId().equals(payment.tenantId())
                || (requirePaymentIdMatch && !Objects.equals(checkout.paymentId(), payment.paymentId()))
                || !checkout.checkoutId().toString().equals(payment.callerReference())
                || checkout.amount().compareTo(payment.amount()) != 0
                || !checkout.currency().equals(payment.currency())) {
            throw new IllegalStateException("payment snapshot does not match the persisted checkout");
        }
    }

    private static TransportCheckout requireSameRequest(TransportCheckout checkout, String fingerprint) {
        if (!checkout.requestFingerprint().equals(fingerprint)) {
            throw new TransportCheckoutIdempotencyConflictException(
                    "Idempotency-Key was already used for a different checkout request");
        }
        return checkout;
    }

    private static boolean isTerminal(TransportCheckoutStatus status) {
        return status == TransportCheckoutStatus.PAYMENT_SUCCEEDED
                || status == TransportCheckoutStatus.PAYMENT_FAILED
                || status == TransportCheckoutStatus.CANCELLED
                || status == TransportCheckoutStatus.RECONCILIATION_REQUIRED
                || status == TransportCheckoutStatus.REFUND_PENDING
                || status == TransportCheckoutStatus.REFUNDED
                || status == TransportCheckoutStatus.REFUND_FAILED;
    }

    private static String holdKey(UUID checkoutId) { return KEY_PREFIX + checkoutId; }
    private static String paymentKey(UUID checkoutId) { return KEY_PREFIX + checkoutId; }
    private static String refundKey(UUID checkoutId) { return KEY_PREFIX + "refund:" + checkoutId; }

    private static String fingerprint(StartCommand command) {
        String canonical = command.tenantId().length() + ":" + command.tenantId()
                + "|" + command.departureId() + "|" + command.fromSequence() + "|"
                + command.toSequence() + "|" + command.passengerCount();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record ReconciliationResult(int attempted, int succeeded, int failed, List<UUID> failedCheckoutIds) {
        public ReconciliationResult {
            if (attempted < 0 || succeeded < 0 || failed < 0 || succeeded + failed != attempted) {
                throw new IllegalArgumentException("reconciliation result counts are inconsistent");
            }
            failedCheckoutIds = List.copyOf(Objects.requireNonNull(failedCheckoutIds, "failedCheckoutIds"));
            if (failedCheckoutIds.size() != failed) {
                throw new IllegalArgumentException("failed checkout IDs must match failed count");
            }
        }
    }

    public record StartCommand(String tenantId, String idempotencyKey, UUID departureId,
                               int fromSequence, int toSequence, int passengerCount) {
        public StartCommand {
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
            tenantId = tenantId.trim();
            if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
                throw new IllegalArgumentException("idempotencyKey must contain 1-128 characters");
            }
            idempotencyKey = idempotencyKey.trim();
            Objects.requireNonNull(departureId, "departureId");
            if (fromSequence < 1 || toSequence <= fromSequence) throw new IllegalArgumentException("invalid segment");
            if (passengerCount < 1) throw new IllegalArgumentException("passengerCount must be positive");
        }
    }
}

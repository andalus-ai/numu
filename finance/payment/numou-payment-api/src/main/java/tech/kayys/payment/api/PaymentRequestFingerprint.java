package tech.kayys.payment.api;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Produces a stable fingerprint of the semantic payment request.
 *
 * <p>The idempotency key is intentionally excluded: it identifies the
 * idempotency slot, while this digest determines whether a retry is the same
 * request. Implementations must persist the digest alongside that slot in the
 * same transaction as the payment operation.</p>
 *
 * <p>The version marker is part of the input. If canonicalization changes,
 * introduce a new version and plan compatibility for retries created by older
 * deployments.</p>
 */
public final class PaymentRequestFingerprint {

    private static final String VERSION = "numou-payment-request-v1";

    private PaymentRequestFingerprint() {
    }

    /**
     * Returns a lowercase hexadecimal SHA-256 digest for the supplied command.
     * Map iteration order and {@link BigDecimal} scale do not affect the digest.
     */
    public static String sha256(CreatePaymentObligation command) {
        Objects.requireNonNull(command, "command");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, VERSION);
            update(digest, command.tenantId());
            update(digest, command.callerReference());
            update(digest, command.amount().stripTrailingZeros().toPlainString());
            update(digest, command.currency());
            update(digest, command.description());

            Map<String, String> sortedMetadata = new TreeMap<>(command.metadata());
            update(digest, Integer.toString(sortedMetadata.size()));
            sortedMetadata.forEach((key, value) -> {
                update(digest, key);
                update(digest, value);
            });

            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is required by every Java implementation.
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
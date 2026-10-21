package tech.kayys.numou.mobility.wallet.application.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * For gateways without their own scheme (bank or e-wallet partners, test environments). The sender signs {@code timestamp + "." + body}
 * with HMAC-SHA256 and sends {@code X-Timestamp} (epoch seconds) and {@code X-Signature} (hex). Timestamps outside the tolerance are rejected,
 * so a captured callback cannot be replayed later. Body: {@code {"orderId","amount","status","providerRef"}}.
 */
public final class GenericHmacProvider implements PaymentProvider {

    private final String id;
    private final String secret;
    private final Duration tolerance;
    private final ObjectMapper mapper = new ObjectMapper();

    public GenericHmacProvider(String id, String secret, Duration tolerance) {
        if (secret == null || secret.length() < 16) throw new IllegalArgumentException("secret must be at least 16 characters");
        this.id = id;
        this.secret = secret;
        this.tolerance = tolerance;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public PaymentNotification parse(Map<String, String> headers, String body, Instant now) {
        final String ts = headers.get("x-timestamp"), sig = headers.get("x-signature");
        if (ts == null || sig == null) throw new InvalidSignatureException();
        try {
            if (Duration.between(Instant.ofEpochSecond(Long.parseLong(ts)), now).abs().compareTo(tolerance) > 0) throw new InvalidSignatureException();
        } catch (NumberFormatException e) {
            throw new InvalidSignatureException();
        }
        if (!Signatures.equal(Signatures.hmacSha256Hex(secret, ts + "." + body), sig.toLowerCase(java.util.Locale.ROOT))) throw new InvalidSignatureException();
        try {
            final JsonNode n = mapper.readTree(body);
            return new PaymentNotification(n.path("orderId").asText(""), new BigDecimal(n.path("amount").asText("0")),
                    PaymentNotification.Status.valueOf(n.path("status").asText("PENDING")), n.path("providerRef").asText(""));
        } catch (Exception e) {
            throw new InvalidSignatureException();
        }
    }
}

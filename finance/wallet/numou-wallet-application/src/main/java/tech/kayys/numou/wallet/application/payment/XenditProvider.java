package tech.kayys.numou.wallet.application.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Xendit invoice callback. Authentic when the {@code x-callback-token} header equals the account's callback verification token.
 * Check against Xendit's current documentation before go-live.
 */
public final class XenditProvider implements PaymentProvider {

    private final String callbackToken;
    private final ObjectMapper mapper = new ObjectMapper();

    public XenditProvider(String callbackToken) {
        if (callbackToken == null || callbackToken.isBlank()) throw new IllegalArgumentException("callbackToken is required");
        this.callbackToken = callbackToken;
    }

    @Override
    public String id() {
        return "xendit";
    }

    @Override
    public PaymentNotification parse(Map<String, String> headers, String body, Instant now) {
        if (!Signatures.equal(callbackToken, headers.get("x-callback-token"))) {
            throw new InvalidSignatureException();
        }
        try {
            final JsonNode n = mapper.readTree(body);
            final JsonNode amount = n.hasNonNull("paid_amount") ? n.get("paid_amount") : n.get("amount");
            final PaymentNotification.Status status = switch (n.path("status").asText("")) {
                case "PAID", "SETTLED" -> PaymentNotification.Status.SETTLED;
                case "EXPIRED" -> PaymentNotification.Status.EXPIRED;
                case "FAILED" -> PaymentNotification.Status.FAILED;
                default -> PaymentNotification.Status.PENDING;
            };
            return new PaymentNotification(n.path("external_id").asText(""), new BigDecimal(amount.asText("0")), status, n.path("id").asText(""));
        } catch (Exception e) {
            throw new InvalidSignatureException();
        }
    }
}

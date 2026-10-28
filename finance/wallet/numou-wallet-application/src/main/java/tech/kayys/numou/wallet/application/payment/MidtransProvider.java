package tech.kayys.numou.wallet.application.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Locale;

/**
 * Midtrans HTTP notification. Authentic when {@code signature_key == SHA512(order_id + status_code + gross_amount + ServerKey)}.
 * {@code capture} (card) is settled only when the fraud check accepted it. Check against Midtrans' current documentation before go-live.
 */
public final class MidtransProvider implements PaymentProvider {

    private final String serverKey;
    private final ObjectMapper mapper = new ObjectMapper();

    public MidtransProvider(String serverKey) {
        if (serverKey == null || serverKey.isBlank()) throw new IllegalArgumentException("serverKey is required");
        this.serverKey = serverKey;
    }

    @Override
    public String id() {
        return "midtrans";
    }

    @Override
    public PaymentNotification parse(Map<String, String> headers, String body, Instant now) {
        try {
            final JsonNode n = mapper.readTree(body);
            final String orderId = n.path("order_id").asText(""), statusCode = n.path("status_code").asText(""), gross = n.path("gross_amount").asText("");
            final String expected = Signatures.sha512Hex(orderId + statusCode + gross + serverKey);
            if (!Signatures.equal(expected, n.path("signature_key").asText("").toLowerCase(Locale.ROOT))) {
                throw new InvalidSignatureException();
            }
            final String tx = n.path("transaction_status").asText(""), fraud = n.path("fraud_status").asText("");
            final PaymentNotification.Status status = switch (tx) {
                case "settlement" -> PaymentNotification.Status.SETTLED;
                case "capture" -> "accept".equals(fraud) ? PaymentNotification.Status.SETTLED : PaymentNotification.Status.PENDING;
                case "deny", "cancel", "failure" -> PaymentNotification.Status.FAILED;
                case "expire" -> PaymentNotification.Status.EXPIRED;
                default -> PaymentNotification.Status.PENDING;
            };
            return new PaymentNotification(orderId, new BigDecimal(gross), status, n.path("transaction_id").asText(""));
        } catch (InvalidSignatureException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidSignatureException();       // unparseable and unauthenticated look the same from outside
        }
    }
}

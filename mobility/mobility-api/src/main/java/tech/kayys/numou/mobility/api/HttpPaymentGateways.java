package tech.kayys.numou.mobility.api;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.mobility.wallet.spi.PaymentGateway;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/**
 * Creates the hosted payment page at the provider. Midtrans: Snap transaction (returns {@code redirect_url}); Xendit: invoice (returns
 * {@code invoice_url}). Written from the providers' public API descriptions and NOT run against their sandboxes: verify request/response
 * fields and credentials in the provider's test environment before go-live. NOT COMPILED in the authoring sandbox.
 */
@ApplicationScoped
public class HttpPaymentGateways implements PaymentGateway {

    @Inject Vertx vertx;
    @Inject PaymentsConfig config;
    private WebClient client;

    @PostConstruct
    void init() {
        client = WebClient.create(vertx);
    }

    @Override
    public Uni<String> createCharge(String provider, String orderId, BigDecimal amount, String currency, UUID riderId, Duration validFor) {
        return switch (provider) {
            case "midtrans" -> {
                final String key = config.midtrans().serverKey().orElseThrow(() -> new BusinessRuleViolation("Payment provider unavailable"));
                final var body = new JsonObject()
                        .put("transaction_details", new JsonObject().put("order_id", orderId).put("gross_amount", amount.longValue()))
                        .put("expiry", new JsonObject().put("unit", "minutes").put("duration", validFor.toMinutes()));
                yield client.postAbs(config.midtrans().snapUrl()).basicAuthentication(key, "").timeout(5_000).sendJsonObject(body)
                        .map(r -> r.bodyAsJsonObject().getString("redirect_url", ""));
            }
            case "xendit" -> {
                final String key = config.xendit().secretKey().orElseThrow(() -> new BusinessRuleViolation("Payment provider unavailable"));
                final var body = new JsonObject().put("external_id", orderId).put("amount", amount.longValue()).put("currency", currency)
                        .put("invoice_duration", validFor.toSeconds());
                yield client.postAbs(config.xendit().invoiceUrl()).basicAuthentication(key, "").timeout(5_000).sendJsonObject(body)
                        .map(r -> r.bodyAsJsonObject().getString("invoice_url", ""));
            }
            default -> Uni.createFrom().item("");        // partner/bank flows are completed in the partner's own app
        };
    }
}

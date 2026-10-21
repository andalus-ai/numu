package tech.kayys.numou.mobility.wallet.spi;

import io.smallrye.mutiny.Uni;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/** Creates the hosted payment page at the provider and returns where to send the rider. */
public interface PaymentGateway {

    /** @return the URL of the payment page (may be empty for providers that charge in-app) */
    Uni<String> createCharge(String provider, String orderId, BigDecimal amount, String currency, UUID riderId, Duration validFor);
}

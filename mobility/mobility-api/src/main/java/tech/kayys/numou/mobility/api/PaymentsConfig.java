package tech.kayys.numou.mobility.api;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/** {@code numou.payments.*}: which providers are enabled and their credentials (environment variables only). A provider with no credential is simply not offered. */
@ConfigMapping(prefix = "numou.payments")
public interface PaymentsConfig {

    Midtrans midtrans();

    Xendit xendit();

    Partner partner();

    TopUp topup();

    interface Midtrans {
        Optional<String> serverKey();

        @WithDefault("https://app.midtrans.com/snap/v1/transactions")
        String snapUrl();
    }

    interface Xendit {
        Optional<String> callbackToken();

        Optional<String> secretKey();

        @WithDefault("https://api.xendit.co/v2/invoices")
        String invoiceUrl();
    }

    interface Partner {
        /** At least 16 characters. Signs {@code timestamp.body} with HMAC-SHA256. */
        Optional<String> secret();
    }

    interface TopUp {
        @WithDefault("10000") BigDecimal min();

        @WithDefault("5000000") BigDecimal max();

        @WithDefault("PT1H") Duration ttl();
    }
}
